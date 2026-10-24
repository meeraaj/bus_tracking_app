# Stage 1: Build static Go binary
FROM golang:1.22-alpine AS builder

WORKDIR /src

# Copy module file and source code (100% pure stdlib, zero external dependencies)
COPY go.mod ./
COPY cmd/ ./cmd/
COPY internal/ ./internal/

# Compile statically linked binary with stripped debug symbols for minimal image size
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /out/bustracker-server ./cmd/server

# Stage 2: Minimal hardened runtime image
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata && \
    addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=builder /out/bustracker-server /app/bustracker-server

# Drop root privileges
USER appuser

EXPOSE 8080

ENV PORT=8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:${PORT}/healthz || exit 1

ENTRYPOINT ["/app/bustracker-server"]
