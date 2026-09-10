package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.StatusError
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
    onToggleTheme: (() -> Unit)? = null,
    onChunkSizeChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onResyncClick: (() -> Unit)? = null,
    onPublishClick: (() -> Unit)? = null,
    onDismissResyncMessage: (() -> Unit)? = null,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onStartTestTransfer: (() -> Unit)? = null
) {
    val colors = LocalTeleVaultColors.current
    val scrollState = rememberScrollState()

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
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 36.dp)
        ) {
            // 0. Appearance Card
            if (onToggleTheme != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPEARANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.violet,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isDarkTheme) "Dark OLED Theme" else "Light Theme",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.text
                                )
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
                                    checkedThumbColor = colors.bg,
                                    checkedTrackColor = colors.violet,
                                    uncheckedThumbColor = colors.textFaint,
                                    uncheckedTrackColor = colors.surfaceHi
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 1. Account Credentials Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONNECTED BOT ACCOUNT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.violet,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Chat ID", fontSize = 11.sp, color = colors.textFaint)
                    Text(text = chatId, fontSize = 13.sp, color = colors.text, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Bot Token", fontSize = 11.sp, color = colors.textFaint)
                    Text(
                        text = if (botTokenMasked.length > 8) "${botTokenMasked.take(6)}••••••••••••" else "••••••••",
                        fontSize = 13.sp,
                        color = colors.text,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDisconnect()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(0.92f)
                            .testTag("disconnect_button")
                    ) {
                        Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect Vault & Clear Credentials", fontSize = 12.sp, color = StatusError)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Safe Chunk Size Config Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHUNK SLICE SIZE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.violet,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Telegram limits standard bot downloads to 20MB per file via getFile. Slices are capped at 18MB to leave safe headroom for multipart overhead so files can be seamlessly uploaded and re-downloaded.",
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 14, 18).forEach { size ->
                            FilterChip(
                                selected = chunkSizeMb == size,
                                onClick = { onChunkSizeChange(size) },
                                label = { Text("$size MB") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.violet,
                                    selectedLabelColor = Color.White,
                                    containerColor = colors.surfaceHi,
                                    labelColor = colors.text
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (chunkSizeMb == size) colors.violet else colors.line,
                                    enabled = true,
                                    selected = chunkSizeMb == size
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Network & Power Policy Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NETWORK & POWER POLICY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.violet,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer only on Wi-Fi",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.text
                            )
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
                                checkedThumbColor = colors.bg,
                                checkedTrackColor = colors.violet,
                                uncheckedThumbColor = colors.textFaint,
                                uncheckedTrackColor = colors.surfaceHi
                            )
                        )
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    if (!com.example.domain.StorageUtil.isIgnoringBatteryOptimizations(context)) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.violet.copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background Execution",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text
                                )
                                Text(
                                    text = "Allow TeleVault to ignore battery optimizations for uninterrupted multi-GB transfers.",
                                    fontSize = 11.sp,
                                    color = colors.textDim,
                                    lineHeight = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val intent = com.example.domain.StorageUtil.createBatteryOptimizationIntent(context)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SettingsScreen", "Failed to start battery optimization intent, falling back", e)
                                        try {
                                            context.startActivity(com.example.domain.StorageUtil.createBatterySettingsFallbackIntent())
                                        } catch (e2: Exception) {
                                            android.util.Log.e("SettingsScreen", "Failed fallback battery intent", e2)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.violet,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.pressScale(0.90f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Optimize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (onStartTestTransfer != null) {
                Spacer(modifier = Modifier.height(14.dp))

                // 4. Verification & Test Mode Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRANSFER VERIFICATION TEST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.violet,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Starts a synthetic 5-chunk multi-part test transfer to verify pause/resume chunk integrity and ensure completed chunks are never re-uploaded.",
                            fontSize = 12.sp,
                            color = colors.textDim,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onStartTestTransfer()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.violet.copy(alpha = 0.15f),
                                contentColor = colors.violet
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(0.92f)
                                .testTag("btn_start_test_transfer")
                        ) {
                            Text("Start 5-Chunk Test Transfer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Security info
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
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.violet,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Hardware KeyStore Encryption: All credentials & tokens are encrypted via AES-256-GCM. No third-party servers.",
                        fontSize = 11.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
