package com.campus.bustracker.ui.student.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campus.bustracker.domain.ApproachingStopInfo
import com.campus.bustracker.ui.common.GlassCard
import com.campus.bustracker.ui.theme.AmberPrimary
import com.campus.bustracker.ui.theme.CardBackground
import com.campus.bustracker.ui.theme.Navy800
import com.campus.bustracker.ui.theme.SuccessGreen
import com.campus.bustracker.ui.theme.TealRoute
import com.campus.bustracker.ui.theme.TextMuted
import com.campus.bustracker.ui.theme.TextPrimary
import com.campus.bustracker.ui.theme.TextSecondary

@Composable
fun ApproachingStopBanner(
    stopInfo: ApproachingStopInfo?,
    isBusActive: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stopInfo != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (stopInfo == null) return@AnimatedVisibility

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = CardBackground.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header row: Next stop badge & ETA chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (stopInfo.isArriving) AmberPrimary else TealRoute),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (stopInfo.isArriving) Icons.Default.NearMe else Icons.Default.Place,
                                contentDescription = null,
                                tint = CardBackground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (stopInfo.isArriving) "ARRIVING NOW (<= 25m)" else "NEXT STOP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (stopInfo.isArriving) AmberPrimary else TealRoute
                        )
                    }

                    // ETA Chip
                    val etaText = if (stopInfo.isArriving) {
                        "Here"
                    } else if (stopInfo.etaSeconds < 60) {
                        "< 1 min"
                    } else {
                        val mins = stopInfo.etaSeconds / 60
                        "$mins min"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Navy800)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isBusActive) "ETA: $etaText" else "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBusActive) TextPrimary else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stop Name
                Text(
                    text = stopInfo.currentStop.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Distance & progress
                val distanceText = if (stopInfo.distanceAlongRouteMeters >= 1000.0) {
                    String.format("%.1f km remaining", stopInfo.distanceAlongRouteMeters / 1000.0)
                } else {
                    "${stopInfo.distanceAlongRouteMeters.toInt()} m remaining"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Stop #${stopInfo.currentStop.sequenceOrder}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Smooth progress bar towards the stop
                LinearProgressIndicator(
                    progress = { stopInfo.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (stopInfo.isArriving) SuccessGreen else AmberPrimary,
                    trackColor = Navy800,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
