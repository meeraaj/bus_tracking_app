package com.campus.bustracker.ui.student

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.bustracker.map.OsmMapView
import com.campus.bustracker.ui.common.GlassCard
import com.campus.bustracker.ui.common.StatusPill
import com.campus.bustracker.ui.student.components.ApproachingStopBanner
import com.campus.bustracker.ui.theme.AmberPrimary
import com.campus.bustracker.ui.theme.CardBackground
import com.campus.bustracker.ui.theme.ErrorRed
import com.campus.bustracker.ui.theme.Navy950
import com.campus.bustracker.ui.theme.SuccessGreen
import com.campus.bustracker.ui.theme.TextMuted
import com.campus.bustracker.ui.theme.TextPrimary
import com.campus.bustracker.ui.theme.TextSecondary
import com.campus.bustracker.ui.theme.WarningOrange

@Composable
fun StudentMapScreen(
    viewModel: StudentViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // Architectural Guardrail #6: Natural back button minimization without sign-out
    BackHandler {
        activity?.moveTaskToBack(true)
    }

    Scaffold(
        containerColor = Navy950,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val routeData = uiState.routeData

            if (routeData != null) {
                // Integrated osmdroid MapView
                OsmMapView(
                    routeData = routeData,
                    busLocation = uiState.busLocation,
                    animatedBusPosition = uiState.animatedBusPosition,
                    approachingStopId = uiState.approachingStop?.currentStop?.stopId,
                    followBus = uiState.followBus,
                    offlineTileManager = viewModel.offlineTileManager,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.isLoadingRoute) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AmberPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading offline route geometry…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Top Control Overlay: Route bar & Approaching Stop Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Route Bar
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = CardBackground.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routeData?.routeName ?: "Bus Route",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            val statusText = when {
                                uiState.isStaleOrOffline -> "Offline • Last seen ${uiState.lastUpdatedAgoText}"
                                uiState.busLocation?.isActive == true -> "Live • Updated ${uiState.lastUpdatedAgoText}"
                                else -> "Connecting…"
                            }
                            val dotColor = if (uiState.isStaleOrOffline) WarningOrange else SuccessGreen

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.signOut(onSignOut) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Exit to role select",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Approaching Stop Banner with non-sticking calculation logic
                ApproachingStopBanner(
                    stopInfo = uiState.approachingStop,
                    isBusActive = !uiState.isStaleOrOffline,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom Floating Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Speed & Telemetry Pill
                AnimatedVisibility(
                    visible = uiState.busLocation != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val speed = uiState.busLocation?.speedKmh ?: 0f
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = CardBackground.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${speed.toInt()} km/h",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Camera follow toggle FAB
                FloatingActionButton(
                    onClick = viewModel::toggleFollowBus,
                    containerColor = if (uiState.followBus) AmberPrimary else CardBackground,
                    contentColor = if (uiState.followBus) Navy950 else TextPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.followBus) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = "Toggle Follow Bus"
                    )
                }
            }
        }
    }
}
