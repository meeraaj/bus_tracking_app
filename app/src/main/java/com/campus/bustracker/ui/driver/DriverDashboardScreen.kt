package com.campus.bustracker.ui.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.bustracker.service.ConnectionStatus
import com.campus.bustracker.ui.common.GlassCard
import com.campus.bustracker.ui.common.PermissionRationaleDialog
import com.campus.bustracker.ui.common.StatusPill
import com.campus.bustracker.ui.common.TelemetryTile
import com.campus.bustracker.ui.theme.AmberPrimary
import com.campus.bustracker.ui.theme.CardBackground
import com.campus.bustracker.ui.theme.ErrorRed
import com.campus.bustracker.ui.theme.Navy800
import com.campus.bustracker.ui.theme.Navy950
import com.campus.bustracker.ui.theme.SuccessGreen
import com.campus.bustracker.ui.theme.TealRoute
import com.campus.bustracker.ui.theme.TextMuted
import com.campus.bustracker.ui.theme.TextPrimary
import com.campus.bustracker.ui.theme.TextSecondary
import com.campus.bustracker.ui.theme.WarningOrange

@Composable
fun DriverDashboardScreen(
    viewModel: DriverViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    // Architectural Guardrail #6: Natural back button minimization without killing service or logging out
    BackHandler {
        activity?.moveTaskToBack(true)
    }

    // Permission launcher for Location + Android 13+ POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        if (fineLocationGranted && notificationGranted) {
            viewModel.startBroadcast(context)
        } else {
            val msg = buildString {
                append("Driver telemetry broadcast requires:")
                if (!fineLocationGranted) append("\n• Precise Location Permission")
                if (!notificationGranted) append("\n• Notification Permission (Android 13+)")
            }
            viewModel.showPermissionRationale(msg)
        }
    }

    fun handleBroadcastToggle() {
        if (uiState.telemetry.isBroadcasting) {
            viewModel.stopBroadcast(context)
        } else {
            val required = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                required.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            val allGranted = required.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                viewModel.startBroadcast(context)
            } else {
                permissionLauncher.launch(required.toTypedArray())
            }
        }
    }

    Scaffold(
        containerColor = Navy950,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Driver Console",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Route ${uiState.routeId.uppercase()} • Live Telemetry Hub",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = {
                    if (uiState.telemetry.isBroadcasting) {
                        showSignOutConfirmation = true
                    } else {
                        viewModel.signOut(context, onSignOut)
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign out",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Route Broadcast Toggle Card
            BroadcastStatusCard(
                isBroadcasting = uiState.telemetry.isBroadcasting,
                connectionStatus = uiState.telemetry.connectionStatus,
                onToggle = ::handleBroadcastToggle,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Telemetry Grid
            Text(
                text = "REAL-TIME TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))

            val speedText = String.format("%.0f", uiState.telemetry.currentSpeedKmh)
            val intervalSec = (uiState.telemetry.transmissionIntervalMs / 1000).toString()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryTile(
                    title = "Speed",
                    value = speedText,
                    unit = "km/h",
                    icon = Icons.Default.Speed,
                    accentColor = AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    title = "Interval",
                    value = intervalSec,
                    unit = "sec",
                    icon = Icons.Default.Timer,
                    accentColor = TealRoute,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryTile(
                    title = "Packets Sent",
                    value = uiState.telemetry.packetsSentCount.toString(),
                    unit = "pings",
                    icon = Icons.Default.Upload,
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                val accText = if (uiState.telemetry.accuracyMeters > 0) {
                    "±" + uiState.telemetry.accuracyMeters.toInt()
                } else {
                    "--"
                }
                TelemetryTile(
                    title = "GPS Accuracy",
                    value = accText,
                    unit = "meters",
                    icon = Icons.Default.GpsFixed,
                    accentColor = AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Route Details Card
            val route = uiState.routeData
            if (route != null) {
                Text(
                    text = "CORRIDOR SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = AmberPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = route.routeName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Stops: ${route.stops.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Points: ${route.polylinePoints.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Target Node: /routes/${uiState.routeId}/live_location",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }

    // Permission Rationale Dialog
    if (uiState.showPermissionRationale) {
        PermissionRationaleDialog(
            title = "Permissions Required",
            message = uiState.rationaleMessage,
            confirmButtonText = "Open App Settings",
            onConfirm = {
                viewModel.dismissPermissionRationale()
                openAppSettings(context)
            },
            onDismiss = viewModel::dismissPermissionRationale
        )
    }

    // Sign out during broadcast confirmation
    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Active Route in Progress") },
            text = { Text("Signing out will immediately end route broadcast and mark your bus OFFLINE. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmation = false
                        viewModel.signOut(context, onSignOut)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("End & Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun BroadcastStatusCard(
    isBroadcasting: Boolean,
    connectionStatus: ConnectionStatus,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionStatus) {
        ConnectionStatus.TRANSMITTING -> SuccessGreen
        ConnectionStatus.CONNECTED -> SuccessGreen
        ConnectionStatus.ERROR -> ErrorRed
        ConnectionStatus.IDLE -> TextMuted
    }

    val statusText = when (connectionStatus) {
        ConnectionStatus.TRANSMITTING -> "Transmitting GPS"
        ConnectionStatus.CONNECTED -> "Broadcasting Active"
        ConnectionStatus.ERROR -> "Error / Reconnecting"
        ConnectionStatus.IDLE -> "Standby"
    }

    GlassCard(
        modifier = modifier,
        backgroundColor = if (isBroadcasting) CardBackground else CardBackground.copy(alpha = 0.85f),
        borderColor = if (isBroadcasting) AmberPrimary.copy(alpha = 0.6f) else Navy800
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBroadcasting) Icons.Default.Sensors else Icons.Default.Radio,
                        contentDescription = null,
                        tint = if (isBroadcasting) AmberPrimary else TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBroadcasting) "ROUTE BROADCAST ON" else "BROADCAST OFF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusPill(text = statusText, dotColor = statusColor)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcasting) ErrorRed else AmberPrimary,
                    contentColor = if (isBroadcasting) TextPrimary else Navy950
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = if (isBroadcasting) Icons.Default.StopCircle else Icons.Default.Sensors,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isBroadcasting) "End Route Broadcast" else "Start Route Broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
