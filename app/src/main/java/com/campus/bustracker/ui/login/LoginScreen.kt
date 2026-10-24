package com.campus.bustracker.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.bustracker.ui.common.GlassCard
import com.campus.bustracker.ui.theme.AmberDark
import com.campus.bustracker.ui.theme.AmberPrimary
import com.campus.bustracker.ui.theme.CardBackground
import com.campus.bustracker.ui.theme.CardBorder
import com.campus.bustracker.ui.theme.ErrorRed
import com.campus.bustracker.ui.theme.Navy800
import com.campus.bustracker.ui.theme.Navy950
import com.campus.bustracker.ui.theme.TextMuted
import com.campus.bustracker.ui.theme.TextPrimary
import com.campus.bustracker.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToDriver: (String) -> Unit,
    onNavigateToStudent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()
    val imeInsets = WindowInsets.ime

    LaunchedEffect(imeInsets) {
        // Automatically scroll to bottom when keyboard opens so input fields & buttons remain visible
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is LoginNavEvent.NavigateToDriver -> onNavigateToDriver(event.routeId)
                is LoginNavEvent.NavigateToStudent -> onNavigateToStudent(event.routeId)
            }
        }
    }

    Scaffold(
        containerColor = Navy950,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { keyboardController?.hide() })
            }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Brand Header
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AmberPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = Navy950,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Campus Bus Tracker",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Live telemetry & arrival predictions",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Role Switcher Segmented Control
            RoleSegmentedControl(
                selectedRole = uiState.selectedRole,
                onRoleSelected = viewModel::onRoleSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // General Error Banner
            AnimatedVisibility(
                visible = uiState.generalError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.generalError?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ErrorRed.copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Mode Forms
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    when (uiState.selectedRole) {
                        AuthRole.STUDENT -> {
                            StudentForm(
                                uiState = uiState,
                                onRollNumberChanged = viewModel::onStudentRollNumberChanged,
                                onRouteSelected = viewModel::onRouteSelected,
                                onSubmit = {
                                    keyboardController?.hide()
                                    viewModel.submitStudentLogin(isGuest = false)
                                },
                                onGuestEntry = {
                                    keyboardController?.hide()
                                    viewModel.submitStudentLogin(isGuest = true)
                                }
                            )
                        }
                        AuthRole.DRIVER -> {
                            DriverForm(
                                uiState = uiState,
                                onEmailChanged = viewModel::onDriverEmailChanged,
                                onSecretKeyChanged = viewModel::onDriverSecretKeyChanged,
                                onToggleKeyVisibility = viewModel::toggleKeyVisibility,
                                onRouteSelected = viewModel::onRouteSelected,
                                onSubmit = {
                                    keyboardController?.hide()
                                    viewModel.submitDriverLogin()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Offline-First Engine • Route 85 Corridor",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun RoleSegmentedControl(
    selectedRole: AuthRole,
    onRoleSelected: (AuthRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RoleTabItem(
            title = "Student",
            isSelected = selectedRole == AuthRole.STUDENT,
            onClick = { onRoleSelected(AuthRole.STUDENT) },
            modifier = Modifier.weight(1f)
        )
        RoleTabItem(
            title = "Driver Console",
            isSelected = selectedRole == AuthRole.DRIVER,
            onClick = { onRoleSelected(AuthRole.DRIVER) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RoleTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) AmberPrimary else Color.Transparent,
        label = "roleBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Navy950 else TextSecondary,
        label = "roleText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentForm(
    uiState: LoginUiState,
    onRollNumberChanged: (String) -> Unit,
    onRouteSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onGuestEntry: () -> Unit
) {
    Text(
        text = "Student Quick Access",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Select your route and enter your student ID or jump in as guest.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(20.dp))

    // Route Selector
    RouteDropdownSelector(
        routes = uiState.availableRoutes,
        selectedRouteId = uiState.selectedRouteId,
        onRouteSelected = onRouteSelected
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Roll Number / ID
    OutlinedTextField(
        value = uiState.rollNumber,
        onValueChange = onRollNumberChanged,
        label = { Text("Roll Number / Student ID (Optional)") },
        placeholder = { Text("e.g. 21CS045") },
        leadingIcon = {
            Icon(Icons.Default.Badge, contentDescription = null, tint = TextSecondary)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = outlinedFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        enabled = !uiState.isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Navy950),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Navy950, modifier = Modifier.size(24.dp))
        } else {
            Text("Track Route Now", style = MaterialTheme.typography.titleMedium)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onGuestEntry,
        enabled = !uiState.isLoading,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Text("Continue as Guest", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverForm(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onSecretKeyChanged: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onRouteSelected: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Text(
        text = "Driver Authentication",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Enter your driver credentials to begin telemetry broadcast.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(20.dp))

    // Route Selector
    RouteDropdownSelector(
        routes = uiState.availableRoutes,
        selectedRouteId = uiState.driverRouteId,
        onRouteSelected = onRouteSelected
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Email
    OutlinedTextField(
        value = uiState.driverEmail,
        onValueChange = onEmailChanged,
        label = { Text("Driver Email / ID") },
        placeholder = { Text("driver@campus.edu") },
        leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary)
        },
        isError = uiState.emailError != null,
        supportingText = uiState.emailError?.let { { Text(it, color = ErrorRed) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        colors = outlinedFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Secret Key / Access Code
    OutlinedTextField(
        value = uiState.driverSecretKey,
        onValueChange = onSecretKeyChanged,
        label = { Text("Access Code") },
        placeholder = { Text("4-digit security PIN") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
        },
        trailingIcon = {
            IconButton(onClick = onToggleKeyVisibility) {
                Icon(
                    imageVector = if (uiState.isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        },
        visualTransformation = if (uiState.isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = uiState.keyError != null,
        supportingText = uiState.keyError?.let { { Text(it, color = ErrorRed) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = outlinedFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        enabled = !uiState.isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Navy950),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Navy950, modifier = Modifier.size(24.dp))
        } else {
            Text("Access Driver Dashboard", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDropdownSelector(
    routes: List<RouteOption>,
    selectedRouteId: String,
    onRouteSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentRoute = routes.find { it.routeId == selectedRouteId } ?: routes.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentRoute?.displayName ?: selectedRouteId,
            onValueChange = {},
            readOnly = true,
            label = { Text("Assigned Route") },
            leadingIcon = { Icon(Icons.Default.Route, contentDescription = null, tint = AmberDark) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = outlinedFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Navy800)
        ) {
            routes.forEach { route ->
                DropdownMenuItem(
                    text = { Text(route.displayName, color = TextPrimary) },
                    onClick = {
                        onRouteSelected(route.routeId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AmberPrimary,
    unfocusedBorderColor = CardBorder,
    errorBorderColor = ErrorRed,
    focusedLabelColor = AmberPrimary,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AmberPrimary
)
