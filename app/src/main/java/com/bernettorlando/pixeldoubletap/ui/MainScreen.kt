package com.bernettorlando.pixeldoubletap.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernettorlando.pixeldoubletap.prefs.TapSettings
import com.bernettorlando.pixeldoubletap.system.PixelWakeStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settings: TapSettings,
    isAccessibilityEnabled: Boolean,
    wakeStatus: PixelWakeStatus,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenWakeSettings: () -> Unit,
    onTestLockNow: () -> Unit,
    onMasterEnabledChange: (Boolean) -> Unit,
    onHomeScreenChange: (Boolean) -> Unit,
    onLockScreenChange: (Boolean) -> Unit,
    onStatusBarChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onHapticChange: (Boolean) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pixel Double Tap",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Built for Google Pixel 11 Pro",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusHeroCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
                settings = settings,
                wakeStatus = wakeStatus,
                onTestLockNow = onTestLockNow,
                onOpenAccessibility = onOpenAccessibility
            )

            AnimatedVisibility(visible = !isAccessibilityEnabled) {
                SideloadSetupGuideCard(
                    onOpenAppInfo = onOpenAppInfo,
                    onOpenAccessibility = onOpenAccessibility
                )
            }

            ScreenOffZonesCard(
                settings = settings,
                onMasterEnabledChange = onMasterEnabledChange,
                onHomeScreenChange = onHomeScreenChange,
                onLockScreenChange = onLockScreenChange,
                onStatusBarChange = onStatusBarChange
            )

            ScreenOnHardwareWakeCard(
                wakeStatus = wakeStatus,
                onOpenWakeSettings = onOpenWakeSettings
            )

            InteractivePracticePadCard(
                timeoutMs = settings.doubleTapTimeoutMs,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onTriggerRealLock = onTestLockNow
            )

            GestureTuningCard(
                settings = settings,
                onTimeoutChange = onTimeoutChange,
                onHapticChange = onHapticChange
            )

            PrivacyAndBiometricsCard()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusHeroCard(
    isAccessibilityEnabled: Boolean,
    settings: TapSettings,
    wakeStatus: PixelWakeStatus,
    onTestLockNow: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val activeReady = isAccessibilityEnabled && settings.masterEnabled
    val containerColor = if (activeReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (activeReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (activeReady) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.TouchApp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !isAccessibilityEnabled -> "Setup needed to enable Double-Tap Off"
                            !settings.masterEnabled -> "Double-Tap Off is paused"
                            else -> "Double-Tap Screen Off is Active"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val activeZones = buildList {
                        if (settings.homeScreenEnabled) add("Home Screen")
                        if (settings.lockScreenEnabled) add("Lock Screen")
                        if (settings.statusBarEnabled) add("Status Bar")
                    }
                    val wakeSummary = when (wakeStatus.tapToCheckPhoneEnabled) {
                        true -> "Hardware Tap-to-Wake ON"
                        false -> "Hardware Tap-to-Wake OFF"
                        null -> "Tap-to-Wake ready in Settings"
                    }
                    Text(
                        text = if (activeReady && activeZones.isNotEmpty()) {
                            "${activeZones.joinToString(" • ")}  |  $wakeSummary"
                        } else {
                            "Enable Accessibility once below to lock without the power button"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = contentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Power button presses saved: ${settings.lockCount}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (isAccessibilityEnabled) {
                    Button(onClick = onTestLockNow) {
                        Icon(
                            imageVector = Icons.Outlined.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock Now")
                    }
                } else {
                    Button(onClick = onOpenAccessibility) {
                        Text("Enable")
                    }
                }
            }
        }
    }
}

@Composable
private fun SideloadSetupGuideCard(
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Quick 2-Step Pixel 11 Pro Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Because this APK is installed directly outside the Play Store, Android 16/17 protects Accessibility behind a one-time \"Allow restricted settings\" toggle:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SetupStepRow(
                stepNumber = "1",
                title = "Allow Restricted Settings (One-time)",
                subtitle = "Tap below → tap the top-right ⋮ menu on App Info → tap \"Allow restricted settings\" and verify with fingerprint.",
                buttonLabel = "Open App Info",
                onClick = onOpenAppInfo
            )

            HorizontalDivider()

            SetupStepRow(
                stepNumber = "2",
                title = "Turn On Pixel Double Tap Service",
                subtitle = "Tap below → select \"Pixel Double Tap\" under Downloaded Apps → switch \"Use Pixel Double Tap\" ON.",
                buttonLabel = "Open Accessibility",
                onClick = onOpenAccessibility,
                primary = true
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (primary) {
                Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(buttonLabel)
                }
            } else {
                FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun ScreenOffZonesCard(
    settings: TapSettings,
    onMasterEnabledChange: (Boolean) -> Unit,
    onHomeScreenChange: (Boolean) -> Unit,
    onLockScreenChange: (Boolean) -> Unit,
    onStatusBarChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turn Screen OFF (Double-Tap)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Locks instantly while keeping Fingerprint & Face Unlock active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.masterEnabled,
                    onCheckedChange = onMasterEnabledChange
                )
            }

            HorizontalDivider()

            ToggleOptionRow(
                icon = Icons.Outlined.Home,
                title = "Home Screen Empty Space",
                description = "Double-tap empty wallpaper on Pixel Launcher. Ignores app icons, folders, widgets, swipes, and keyboard.",
                checked = settings.homeScreenEnabled,
                enabled = settings.masterEnabled,
                onCheckedChange = onHomeScreenChange
            )

            ToggleOptionRow(
                icon = Icons.Outlined.Smartphone,
                title = "Lock Screen Empty Space",
                description = "Check notifications and double-tap empty lock screen space to sleep again. Automatically pauses during PIN/Pattern entry.",
                checked = settings.lockScreenEnabled,
                enabled = settings.masterEnabled,
                onCheckedChange = onLockScreenChange
            )

            ToggleOptionRow(
                icon = Icons.Outlined.TouchApp,
                title = "Status Bar (Inside Any App)",
                description = "Optional: double-tap top status bar to sleep from any app. Single swipe-down still opens Notifications & Quick Settings.",
                checked = settings.statusBarEnabled,
                enabled = settings.masterEnabled,
                onCheckedChange = onStatusBarChange
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bonus included: You can also add the \"Lock Screen\" Quick Settings tile in your notification shade or place the \"Invisible Double-Tap Zone\" widget on any home page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ScreenOnHardwareWakeCard(
    wakeStatus: PixelWakeStatus,
    onOpenWakeSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turn Screen ON (Zero-Battery Wake)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pixel 11 Pro native display-controller wake",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(enabled = wakeStatus.tapToCheckPhoneEnabled)
            }

            Text(
                text = "Pixel 11 Pro hides hardware tap-to-wake under \"Tap to check phone\" in Lock Screen settings. When enabled, single or double tapping your sleeping screen wakes it using the low-power display controller with 0% extra battery drain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WakeChip(
                    label = "Tap to check",
                    state = wakeStatus.tapToCheckPhoneEnabled,
                    modifier = Modifier.weight(1f)
                )
                WakeChip(
                    label = "Lift to check",
                    state = wakeStatus.liftToCheckPhoneEnabled,
                    modifier = Modifier.weight(1f)
                )
                WakeChip(
                    label = "Always-on",
                    state = wakeStatus.alwaysOnDisplayEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            FilledTonalButton(
                onClick = onOpenWakeSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (wakeStatus.tapToCheckPhoneEnabled == true) {
                        "Open Lock Screen Wake Settings"
                    } else {
                        "Enable \"Tap to check phone\" in Settings"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(enabled: Boolean?) {
    val (label, bg, fg) = when (enabled) {
        true -> Triple(
            "Enabled",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        false -> Triple(
            "Off",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        null -> Triple(
            "System",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun WakeChip(
    label: String,
    state: Boolean?,
    modifier: Modifier = Modifier
) {
    val stateText = when (state) {
        true -> "ON"
        false -> "OFF"
        null -> "—"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InteractivePracticePadCard(
    timeoutMs: Int,
    isAccessibilityEnabled: Boolean,
    onTriggerRealLock: () -> Unit
) {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var feedbackMessage by remember {
        mutableStateOf("Double-tap inside this pad to test your tap speed")
    }
    var lockOnSuccess by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Double-Tap Test Pad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .pointerInput(timeoutMs, lockOnSuccess, isAccessibilityEnabled) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val now = SystemClock.uptimeMillis()
                            val delta = now - lastTapTime
                            if (lastTapTime > 0L && delta in 65L..timeoutMs.toLong()) {
                                lastTapTime = 0L
                                feedbackMessage = "Perfect double-tap! (${delta} ms ≤ ${timeoutMs} ms)"
                                if (lockOnSuccess && isAccessibilityEnabled) {
                                    onTriggerRealLock()
                                }
                            } else if (lastTapTime > 0L && delta in (timeoutMs + 1).toLong()..900L) {
                                lastTapTime = now
                                feedbackMessage = "Slightly slow (${delta} ms > ${timeoutMs} ms) — tap faster or raise slider"
                            } else {
                                lastTapTime = now
                                feedbackMessage = "1st tap registered — tap once more!"
                            }
                        }
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = feedbackMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (isAccessibilityEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Actually lock screen when double-tapping test pad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = lockOnSuccess,
                        onCheckedChange = { lockOnSuccess = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureTuningCard(
    settings: TapSettings,
    onTimeoutChange: (Int) -> Unit,
    onHapticChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Timing & Haptics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Double-tap window",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${settings.doubleTapTimeoutMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Slider(
                value = settings.doubleTapTimeoutMs.toFloat(),
                onValueChange = { onTimeoutChange(it.roundToInt()) },
                valueRange = TapSettings.MIN_DOUBLE_TAP_TIMEOUT_MS.toFloat()..
                    TapSettings.MAX_DOUBLE_TAP_TIMEOUT_MS.toFloat(),
                steps = 13
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { onTimeoutChange(280) }) { Text("Fast (280ms)") }
                OutlinedButton(onClick = { onTimeoutChange(350) }) { Text("Default (350ms)") }
                OutlinedButton(onClick = { onTimeoutChange(430) }) { Text("Relaxed (430ms)") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Crisp Pixel Haptic Tick",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Subtle vibration tick right as the screen locks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.hapticFeedback,
                    onCheckedChange = onHapticChange
                )
            }
        }
    }
}

@Composable
private fun PrivacyAndBiometricsCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Biometrics & Privacy by Design",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "• Ultrasonic Fingerprint & Face Unlock stay 100% enabled using Android's native GLOBAL_ACTION_LOCK_SCREEN.\n" +
                    "• Zero network permissions in AndroidManifest (completely offline).\n" +
                    "• Empty-space watcher detaches automatically inside apps and whenever the keyboard is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "100% Open Source • Zero Ads • Zero Background Drain",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ToggleOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
