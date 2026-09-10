package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.StorageUtil
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.pressScale

@Composable
fun SettingsScreen(
    botTokenMasked: String,
    chatId: String,
    chunkSizeMb: Int,
    isWifiOnly: Boolean,
    lastSyncedTime: Long = 0L,
    isSyncing: Boolean = false,
    resyncMessage: String? = null,
    isDarkTheme: Boolean = false,
    testTransferRunning: Boolean = false,
    testTransferStatus: String? = null,
    testTransferSuccess: Boolean? = null,
    onToggleTheme: (() -> Unit)? = null,
    onChunkSizeChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onResyncClick: (() -> Unit)? = null,
    onPublishClick: (() -> Unit)? = null,
    onDismissResyncMessage: (() -> Unit)? = null,
    onDismissTestStatus: (() -> Unit)? = null,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onStartTestTransfer: (() -> Unit)? = null
) {
    val colors = LocalTeleVaultColors.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.danger.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Disconnect Vault?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "This will cancel any active transfers, clear local cache and database records, and remove your Telegram bot credentials. Your uploaded files remain safe in Telegram.",
                    fontSize = 13.sp,
                    color = colors.textDim,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        onDismiss()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false }
                ) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
        containerColor = colors.bg,
        topBar = {
            Surface(
                color = colors.bg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(0.88f)
                            .testTag("btn_back_to_vault")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Vault",
                            tint = colors.text
                        )
                    }

                    Text(
                        text = "Vault Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )

                    if (onToggleTheme != null) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(48.dp)
                                .pressScale(0.88f)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = colors.violet
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Connected Bot Account Panel
            SettingsCard(
                icon = Icons.Default.Key,
                title = "CONNECTED BOT ACCOUNT",
                colors = colors
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Chat ID Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Chat ID",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textFaint
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = chatId.ifEmpty { "Not connected" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.text
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.mint.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.mint
                            )
                        }
                    }

                    // Bot Token Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bot Token",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textFaint
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (botTokenMasked.length > 8) {
                                    "${botTokenMasked.take(6)}••••••••••••"
                                } else {
                                    "••••••••••••"
                                },
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textDim
                            )
                        }
                    }

                    // Disconnect Danger Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.danger.copy(alpha = 0.08f))
                            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { showDisconnectDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("disconnect_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disconnect Vault & Clear Credentials",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.danger
                            )
                        }
                    }
                }
            }

            // 2. Safe Chunk Size Config Card (Pill-style selector)
            SettingsCard(
                icon = Icons.Default.Tune,
                title = "CHUNK SLICE SIZE",
                colors = colors
            ) {
                Column {
                    Text(
                        text = "Telegram limits standard bot downloads to 20MB per file via getFile. Slices are capped at 18MB to ensure safe headroom for multipart overhead and reliable resumption.",
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(10, 14, 18).forEach { size ->
                            val isSelected = chunkSizeMb == size
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) colors.violet else colors.surfaceHi)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) colors.violet else colors.line,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onChunkSizeChange(size) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "$size MB",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Network & Power Policy Card
            SettingsCard(
                icon = Icons.Default.Wifi,
                title = "NETWORK & POWER POLICY",
                colors = colors
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Wi-Fi Only Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer only on Wi-Fi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Restricts large multi-chunk uploads & downloads to Wi-Fi to preserve mobile data.",
                                fontSize = 11.sp,
                                color = colors.textDim,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isWifiOnly,
                            onCheckedChange = onWifiOnlyChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.violet,
                                uncheckedThumbColor = colors.textFaint,
                                uncheckedTrackColor = colors.surfaceHi
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.line)
                    )

                    // Battery Optimization Row
                    val isOptimized = StorageUtil.isIgnoringBatteryOptimizations(context)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOptimized) colors.mint.copy(alpha = 0.08f) else colors.violet.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isOptimized) colors.mint else colors.violet,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Execution",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isOptimized) {
                                    "Battery optimizations are exempted. Multi-GB background transfers will run uninterrupted."
                                } else {
                                    "Exempt TeleVault from battery optimizations so long-running transfers are not paused by the OS."
                                },
                                fontSize = 11.sp,
                                color = colors.textDim,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isOptimized) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.mint.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.mint,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Optimized",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.mint
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val intent = StorageUtil.createBatteryOptimizationIntent(context)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(StorageUtil.createBatterySettingsFallbackIntent())
                                        } catch (_: Exception) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.violet,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.pressScale(0.90f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Optimize", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Verification & Test Mode Card (Primary Gradient CTA)
            if (onStartTestTransfer != null) {
                SettingsCard(
                    icon = Icons.Default.Science,
                    title = "TRANSFER INTEGRITY TEST",
                    colors = colors
                ) {
                    Column {
                        Text(
                            text = "Starts an actual synthetic 5-chunk multi-part transfer to Telegram. It uploads chunk 1, simulates a pause/resume cycle, confirms completed chunks are never re-uploaded, and verifies full stream integrity.",
                            fontSize = 12.sp,
                            color = colors.textDim,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Gradient CTA Button
                        val gradientBrush = Brush.horizontalGradient(
                            listOf(colors.violet, Color(0xFF6C5CE7))
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (testTransferRunning) colors.surfaceHi else colors.violet)
                                .then(
                                    if (!testTransferRunning) {
                                        Modifier.background(gradientBrush)
                                    } else Modifier
                                )
                                .clickable(enabled = !testTransferRunning) {
                                    onStartTestTransfer()
                                }
                                .pressScale(0.96f)
                                .testTag("btn_start_test_transfer"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (testTransferRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = colors.violet,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Running Test…",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start 5-Chunk Test Transfer",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Test Status Banner
                        AnimatedVisibility(
                            visible = testTransferRunning || testTransferStatus != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val isSuccess = testTransferSuccess == true
                            val isFailure = testTransferSuccess == false
                            val bannerBg = when {
                                isSuccess -> colors.mint.copy(alpha = 0.12f)
                                isFailure -> colors.danger.copy(alpha = 0.12f)
                                else -> colors.surfaceHi
                            }
                            val bannerBorder = when {
                                isSuccess -> colors.mint.copy(alpha = 0.35f)
                                isFailure -> colors.danger.copy(alpha = 0.35f)
                                else -> colors.line
                            }
                            val bannerTint = when {
                                isSuccess -> colors.mint
                                isFailure -> colors.danger
                                else -> colors.violet
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bannerBg)
                                    .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (testTransferRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(top = 2.dp),
                                            color = colors.violet,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = bannerTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                testTransferRunning -> "Integrity Test in Progress"
                                                isSuccess -> "Integrity Test Passed"
                                                else -> "Integrity Test Failed"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bannerTint
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = testTransferStatus ?: "",
                                            fontSize = 11.sp,
                                            color = colors.text,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    if (!testTransferRunning && onDismissTestStatus != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Dismiss",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textDim,
                                            modifier = Modifier
                                                .clickable { onDismissTestStatus() }
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Appearance Card
            if (onToggleTheme != null) {
                SettingsCard(
                    icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "APPEARANCE",
                    colors = colors
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDarkTheme) "Dark OLED Theme" else "Light Theme",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isDarkTheme) "Deep black high-contrast OLED palette" else "Clean modern light paper palette",
                                fontSize = 11.sp,
                                color = colors.textDim,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.violet,
                                uncheckedThumbColor = colors.textFaint,
                                uncheckedTrackColor = colors.surfaceHi
                            )
                        )
                    }
                }
            }

            // 6. Footnote Card (AES-256-GCM)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.violet.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hardware KeyStore Encryption",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All credentials and bot tokens are encrypted via AES-256-GCM. Direct transfer to Telegram Cloud API with zero intermediate servers.",
                            fontSize = 11.sp,
                            color = colors.textDim,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    colors: com.example.ui.theme.TeleVaultColors,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.violet.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.violet,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.violet,
                    letterSpacing = 0.6.sp
                )
            }
            content()
        }
    }
}
