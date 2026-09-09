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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.example.ui.theme.pressScale
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledCard
import com.example.ui.theme.OledSurface
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusMint
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TextFaint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun SettingsScreen(
    botTokenMasked: String,
    chatId: String,
    chunkSizeMb: Int,
    isWifiOnly: Boolean,
    lastSyncedTime: Long = 0L,
    isSyncing: Boolean = false,
    resyncMessage: String? = null,
    onChunkSizeChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onResyncClick: () -> Unit,
    onPublishClick: (() -> Unit)? = null,
    onDismissResyncMessage: (() -> Unit)? = null,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onStartTestTransfer: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack),
        containerColor = OledBlack,
        topBar = {
            Surface(
                color = OledBlack,
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
                            tint = TextPrimary
                        )
                    }

                    Text(
                        text = "Vault Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
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

            // 1. Account Credentials Card
            Card(
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONNECTED BOT ACCOUNT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Chat ID", fontSize = 11.sp, color = TextTertiary)
                    Text(text = chatId, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Bot Token", fontSize = 11.sp, color = TextTertiary)
                    Text(
                        text = if (botTokenMasked.length > 8) "${botTokenMasked.take(6)}••••••••••••" else "••••••••",
                        fontSize = 13.sp,
                        color = TextPrimary,
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
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHUNK SLICE SIZE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Telegram limits standard bot downloads to 20MB per file via getFile. Slices are capped at 18MB to leave safe headroom for multipart overhead so files can be seamlessly uploaded and re-downloaded.",
                        fontSize = 12.sp,
                        color = TextSecondary,
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
                                    selectedContainerColor = TelegramBlue,
                                    selectedLabelColor = OledBlack,
                                    containerColor = OledBlack,
                                    labelColor = TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (chunkSizeMb == size) TelegramBlue else OledBorder,
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
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NETWORK & POWER POLICY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
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
                                color = TextPrimary
                            )
                            Text(
                                text = "Restricts large multi-chunk uploads & downloads to Wi-Fi to preserve mobile data.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isWifiOnly,
                            onCheckedChange = onWifiOnlyChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = TelegramBlue,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = OledBorder
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
                                .background(TelegramBlue.copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background Execution",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Allow TeleVault to ignore battery optimizations for uninterrupted multi-GB transfers.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
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
                                    containerColor = TelegramBlue,
                                    contentColor = OledBlack
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

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Multi-Device Vault Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MULTI-DEVICE VAULT SYNC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelegramBlue,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TelegramBlue,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "TeleVault maintains a single unified vault index (folders, hierarchy, and file manifests) directly in Telegram. Multiple devices sharing this bot token & chat ID stay completely synchronized.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Last Synced status box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OledSurface)
                            .border(1.dp, OledBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (lastSyncedTime > 0) StatusMint else TextFaint)
                            )
                            Text(
                                text = "Sync Status",
                                fontSize = 11.sp,
                                color = TextTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        val lastSyncedFormatted = when {
                            isSyncing -> "Syncing now..."
                            lastSyncedTime > 0 -> {
                                val diff = System.currentTimeMillis() - lastSyncedTime
                                when {
                                    diff < 60_000L -> "Synced just now"
                                    diff < 3_600_000L -> "Synced ${diff / 60_000L}m ago"
                                    diff < 86_400_000L -> "Synced ${diff / 3_600_000L}h ago"
                                    else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncedTime))
                                }
                            }
                            else -> "Never synced"
                        }

                        Text(
                            text = lastSyncedFormatted,
                            fontSize = 11.sp,
                            color = if (isSyncing) TelegramBlue else TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onResyncClick()
                        },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelegramBlue.copy(alpha = 0.15f),
                            contentColor = TelegramBlue,
                            disabledContainerColor = TelegramBlue.copy(alpha = 0.05f),
                            disabledContentColor = TextFaint
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(0.92f)
                            .testTag("sync_now_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = TelegramBlue
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSyncing) "Syncing with Telegram..." else "Sync Now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onPublishClick != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onPublishClick,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(0.92f)
                                .testTag("publish_index_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary,
                                disabledContentColor = TextFaint
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Publish Vault to Telegram",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Explicit Error / Status banner if sync returned a result or error
                    if (!resyncMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        val isError = resyncMessage.contains("error", ignoreCase = true) ||
                                resyncMessage.contains("failed", ignoreCase = true) ||
                                resyncMessage.contains("No Vault Index", ignoreCase = true)

                        val bannerBg = if (isError) StatusError.copy(alpha = 0.12f) else StatusMint.copy(alpha = 0.12f)
                        val bannerBorder = if (isError) StatusError.copy(alpha = 0.35f) else StatusMint.copy(alpha = 0.35f)
                        val bannerIcon = if (isError) Icons.Default.Warning else Icons.Default.Check
                        val bannerTint = if (isError) StatusError else StatusMint
                        val bannerTitle = if (isError) "Sync Error" else "Sync Status"

                        Card(
                            colors = CardDefaults.cardColors(containerColor = bannerBg),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, bannerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_result_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = bannerIcon,
                                    contentDescription = bannerTitle,
                                    tint = bannerTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bannerTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bannerTint
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = resyncMessage,
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                                if (onDismissResyncMessage != null) {
                                    IconButton(
                                        onClick = onDismissResyncMessage,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = TextFaint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onStartTestTransfer != null) {
                Spacer(modifier = Modifier.height(14.dp))

                // 5. Verification & Test Mode Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = OledCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRANSFER VERIFICATION TEST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelegramBlue,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Starts a synthetic 5-chunk multi-part test transfer to verify pause/resume chunk integrity and ensure completed chunks are never re-uploaded.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onStartTestTransfer()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TelegramBlue.copy(alpha = 0.15f),
                                contentColor = TelegramBlue
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

            // 4. Security info
            Card(
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TelegramBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Hardware KeyStore Encryption: All credentials & tokens are encrypted via AES-256-GCM. No third-party servers.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
