package ws

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

const (
	rfc6455GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

	OpcodeText  = 0x1
	OpcodeClose = 0x8
	OpcodePing  = 0x9
	OpcodePong  = 0xA
)

var (
	ErrBadHandshake   = errors.New("websocket: invalid upgrade headers")
	ErrFrameTooLarge  = errors.New("websocket: frame exceeds maximum allowed size")
	ErrUnmaskedFrame  = errors.New("websocket: client frame must be masked per RFC 6455")
	ErrConnectionDone = errors.New("websocket: connection closed")
)

type Conn struct {
	netConn       net.Conn
	rw            *bufio.ReadWriter
	writeMu       sync.Mutex
	maxPayloadLen int64
	readTimeout   time.Duration
}

// Upgrade performs the RFC 6455 HTTP/1.1 101 Switching Protocols handshake using the Go standard library.
func Upgrade(w http.ResponseWriter, r *http.Request, maxPayloadLen int64, readTimeout time.Duration) (*Conn, error) {
	if !strings.EqualFold(r.Header.Get("Upgrade"), "websocket") ||
		!strings.Contains(strings.ToLower(r.Header.Get("Connection")), "upgrade") {
		http.Error(w, "websocket upgrade required", http.StatusBadRequest)
		return nil, ErrBadHandshake
	}

	wsKey := strings.TrimSpace(r.Header.Get("Sec-WebSocket-Key"))
	if wsKey == "" {
		http.Error(w, "missing Sec-WebSocket-Key", http.StatusBadRequest)
		return nil, ErrBadHandshake
	}

	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "webserver does not support hijacking", http.StatusInternalServerError)
		return nil, ErrBadHandshake
	}

	netConn, rw, err := hijacker.Hijack()
	if err != nil {
		return nil, err
	}

	hash := sha1.Sum([]byte(wsKey + rfc6455GUID))
	acceptKey := base64.StdEncoding.EncodeToString(hash[:])

	response := "HTTP/1.1 101 Switching Protocols\r\n" +
		"Upgrade: websocket\r\n" +
		"Connection: Upgrade\r\n" +
		"Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n"

	if _, err := rw.WriteString(response); err != nil {
		_ = netConn.Close()
		return nil, err
	}
	if err := rw.Flush(); err != nil {
		_ = netConn.Close()
		return nil, err
	}

	return &Conn{
		netConn:       netConn,
		rw:            rw,
		maxPayloadLen: maxPayloadLen,
		readTimeout:   readTimeout,
	}, nil
}

// ReadText reads the next text message frame from the client, automatically handling Ping/Pong control frames.
func (c *Conn) ReadText() ([]byte, error) {
	for {
		if c.readTimeout > 0 {
			_ = c.netConn.SetReadDeadline(time.Now().Add(c.readTimeout))
		}

		header := make([]byte, 2)
		if _, err := io.ReadFull(c.rw, header); err != nil {
			return nil, err
		}

		opcode := header[0] & 0x0F
		masked := (header[1] & 0x80) != 0
		payloadLen := int64(header[1] & 0x7F)

		if !masked {
			_ = c.Close()
			return nil, ErrUnmaskedFrame
		}

		switch payloadLen {
		case 126:
			ext := make([]byte, 2)
			if _, err := io.ReadFull(c.rw, ext); err != nil {
				return nil, err
			}
			payloadLen = int64(binary.BigEndian.Uint16(ext))
		case 127:
			ext := make([]byte, 8)
			if _, err := io.ReadFull(c.rw, ext); err != nil {
				return nil, err
			}
			payloadLen = int64(binary.BigEndian.Uint64(ext))
		}

		if payloadLen > c.maxPayloadLen || payloadLen < 0 {
			_ = c.Close()
			return nil, ErrFrameTooLarge
		}

		maskKey := make([]byte, 4)
		if _, err := io.ReadFull(c.rw, maskKey); err != nil {
			return nil, err
		}

		payload := make([]byte, payloadLen)
		if payloadLen > 0 {
			if _, err := io.ReadFull(c.rw, payload); err != nil {
				return nil, err
			}
			for i := int64(0); i < payloadLen; i++ {
				payload[i] ^= maskKey[i%4]
			}
		}

		switch opcode {
		case OpcodeText:
			return payload, nil
		case OpcodePing:
			_ = c.writeFrame(OpcodePong, payload)
		case OpcodePong:
			// Keep-alive acknowledged; deadline refreshes on next loop iteration
			continue
		case OpcodeClose:
			_ = c.Close()
			return nil, ErrConnectionDone
		default:
			continue
		}
	}
}

func (c *Conn) WriteText(payload []byte) error {
	return c.writeFrame(OpcodeText, payload)
}

func (c *Conn) WritePing() error {
	return c.writeFrame(OpcodePing, []byte("ping"))
}

func (c *Conn) writeFrame(opcode byte, payload []byte) error {
	c.writeMu.Lock()
	defer c.writeMu.Unlock()

	_ = c.netConn.SetWriteDeadline(time.Now().Add(5 * time.Second))

	length := len(payload)
	var header []byte

	if length < 126 {
		header = []byte{0x80 | opcode, byte(length)}
	} else if length <= 65535 {
		header = make([]byte, 4)
		header[0] = 0x80 | opcode
		header[1] = 126
		binary.BigEndian.PutUint16(header[2:4], uint16(length))
	} else {
		header = make([]byte, 10)
		header[0] = 0x80 | opcode
		header[1] = 127
		binary.BigEndian.PutUint64(header[2:10], uint64(length))
	}

	if _, err := c.rw.Write(header); err != nil {
		return err
	}
	if length > 0 {
		if _, err := c.rw.Write(payload); err != nil {
			return err
		}
	}
	return c.rw.Flush()
}

func (c *Conn) Close() error {
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	_ = c.netConn.SetWriteDeadline(time.Now().Add(1 * time.Second))
	_, _ = c.rw.Write([]byte{0x80 | OpcodeClose, 0x00})
	_ = c.rw.Flush()
	return c.netConn.Close()
}
