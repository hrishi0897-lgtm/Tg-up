package com.example.ui.screens

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import com.example.data.local.entity.SharedFileEntity
import com.example.domain.model.BotRevocationAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.local.entity.StandbyBotEntity
import com.example.data.transfer.StandbyRecoveryState
import com.example.domain.StorageUtil
import com.example.domain.model.BotHealthInfo
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.pressScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    botTokenPool: List<String> = emptyList(),
    botHealthMap: Map<String, BotHealthInfo> = emptyMap(),
    standbyBots: List<StandbyBotEntity> = emptyList(),
    verifiedStandbyBotsCount: Int = 0,
    recoveryState: StandbyRecoveryState = StandbyRecoveryState(),
    onAddStandbyBot: ((token: String, label: String, onResult: (Boolean, String?) -> Unit) -> Unit)? = null,
    onVerifyStandbyBot: ((StandbyBotEntity, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onVerifyAllStandbyBots: (() -> Unit)? = null,
    onDeleteStandbyBot: ((StandbyBotEntity) -> Unit)? = null,
    onStartStandbyRecovery: ((StandbyBotEntity) -> Unit)? = null,
    onCancelStandbyRecovery: (() -> Unit)? = null,
    onResetStandbyRecoveryState: (() -> Unit)? = null,
    onAddBotToken: ((String) -> Unit)? = null,
    onRemoveBotToken: ((String) -> Unit)? = null,
    onSetActiveBotToken: ((String) -> Unit)? = null,
    onCheckBotHealth: ((String) -> Unit)? = null,
    onCheckAllBotsHealth: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    onChunkSizeChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onResyncClick: (() -> Unit)? = null,
    onPublishClick: (() -> Unit)? = null,
    onDismissResyncMessage: (() -> Unit)? = null,
    onDismissTestStatus: (() -> Unit)? = null,
    primaryBotAlert: BotRevocationAlert? = null,
    onDismissPrimaryBotAlert: (() -> Unit)? = null,
    botHealthLogs: List<String> = emptyList(),
    onCheckPrimaryBotHealth: (() -> Unit)? = null,
    isShareSheetAskFolder: Boolean = true,
    onShareSheetAskFolderChange: ((Boolean) -> Unit)? = null,
    relayChatId: String? = null,
    onRelayChatIdChange: ((String) -> Unit)? = null,
    sharedFiles: List<SharedFileEntity> = emptyList(),
    onRevokeSharedFile: ((String) -> Unit)? = null,
    onPairDeviceClick: (() -> Unit)? = null,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onStartTestTransfer: (() -> Unit)? = null
) {
    val colors = LocalTeleVaultColors.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showAddTokenDialog by remember { mutableStateOf(false) }
    var newBotTokenInput by remember { mutableStateOf("") }
    var showBotHealthLogs by remember { mutableStateOf(false) }
    var isEditingRelayChat by remember { mutableStateOf(false) }
    var relayChatInput by remember(relayChatId) { mutableStateOf(relayChatId ?: "") }

    // Standby bot management states
    var showAddStandbyBotDialog by remember { mutableStateOf(false) }
    var standbyBotTokenInput by remember { mutableStateOf("") }
    var standbyBotLabelInput by remember { mutableStateOf("") }
    var addStandbyBotError by remember { mutableStateOf<String?>(null) }
    var isAddingStandbyBot by remember { mutableStateOf(false) }
    var botToRecover by remember { mutableStateOf<StandbyBotEntity?>(null) }
    var verifyingBotId by remember { mutableStateOf<String?>(null) }
    var verifyFeedbackMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Add Bot Token Dialog
    if (showAddTokenDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTokenDialog = false
                newBotTokenInput = ""
            },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.violet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.violet,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Add Backup Bot Token",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a token for a backup bot that has been added as an Administrator to your storage channel. If your active bot is banned, TeleVault will automatically rotate to this token and regenerate file access.",
                        fontSize = 13.sp,
                        color = colors.textDim,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newBotTokenInput,
                        onValueChange = { newBotTokenInput = it },
                        placeholder = { Text("123456789:ABCdefGHI...", color = colors.textFaint, fontSize = 13.sp) },
                        label = { Text("Bot Token", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_bot_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.violet,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newBotTokenInput.trim()
                        if (clean.isNotBlank()) {
                            onAddBotToken?.invoke(clean)
                        }
                        showAddTokenDialog = false
                        newBotTokenInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.violet),
                    enabled = newBotTokenInput.trim().isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_bot_button")
                ) {
                    Text("Add to Pool", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddTokenDialog = false
                        newBotTokenInput = ""
                    }
                ) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

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

    // Add Standby Bot Dialog
    if (showAddStandbyBotDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingStandbyBot) {
                    showAddStandbyBotDialog = false
                    standbyBotTokenInput = ""
                    standbyBotLabelInput = ""
                    addStandbyBotError = null
                }
            },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.violet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = colors.violet,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Add Standby Bot",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = "Standby bots enable instant zero-bandwidth chunk recovery via Telegram copyMessage if your primary bot gets banned.",
                        fontSize = 13.sp,
                        color = colors.textDim,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Step by step instructions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Setup Steps in Telegram:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.violet
                            )
                            Text(
                                text = "1. Create a bot with @BotFather in Telegram.",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                            Text(
                                text = "2. Open your Telegram storage channel > Administrators > Add Administrator, and add this bot (bots cannot add other bots via API).",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                            Text(
                                text = "3. Enter the bot token below to register & verify.",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = standbyBotLabelInput,
                        onValueChange = { standbyBotLabelInput = it },
                        placeholder = { Text("e.g. Backup Bot #1", color = colors.textFaint, fontSize = 13.sp) },
                        label = { Text("Bot Label / Name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("standby_bot_label_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.violet,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = standbyBotTokenInput,
                        onValueChange = {
                            standbyBotTokenInput = it
                            addStandbyBotError = null
                        },
                        placeholder = { Text("123456789:ABCdefGHI...", color = colors.textFaint, fontSize = 13.sp) },
                        label = { Text("Bot Token", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("standby_bot_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.violet,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        )
                    )

                    if (addStandbyBotError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = addStandbyBotError ?: "",
                            fontSize = 12.sp,
                            color = colors.danger,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanToken = standbyBotTokenInput.trim()
                        val cleanLabel = standbyBotLabelInput.trim().ifBlank { "Standby Bot" }
                        if (cleanToken.isNotBlank()) {
                            isAddingStandbyBot = true
                            addStandbyBotError = null
                            onAddStandbyBot?.invoke(cleanToken, cleanLabel) { success, errorMsg ->
                                isAddingStandbyBot = false
                                if (success) {
                                    showAddStandbyBotDialog = false
                                    standbyBotTokenInput = ""
                                    standbyBotLabelInput = ""
                                    addStandbyBotError = null
                                    verifyFeedbackMessage = Pair(true, "Standby bot registered securely! Token encrypted in local KeyStore.")
                                } else {
                                    addStandbyBotError = errorMsg ?: "Failed to validate bot token"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.violet),
                    enabled = standbyBotTokenInput.trim().isNotBlank() && !isAddingStandbyBot,
                    modifier = Modifier.testTag("confirm_add_standby_bot_button")
                ) {
                    if (isAddingStandbyBot) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Add & Verify", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddStandbyBotDialog = false
                        standbyBotTokenInput = ""
                        standbyBotLabelInput = ""
                        addStandbyBotError = null
                    },
                    enabled = !isAddingStandbyBot
                ) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

    // Confirm Recovery Dialog (Manual "Switch to standby bot" flow)
    if (botToRecover != null) {
        val bot = botToRecover!!
        AlertDialog(
            onDismissRequest = { botToRecover = null },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.violet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = colors.violet,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Switch Vault to ${bot.label}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = "This recovery flow is designed for when your primary bot is banned or restricted by Telegram.",
                        fontSize = 13.sp,
                        color = colors.text,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "How copyMessage Recovery Works:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.violet
                            )
                            Text(
                                text = "• 0 MB mobile data: Telegram copies messages server-side inside Telegram's cloud.",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                            Text(
                                text = "• Generates fresh file_ids and binds chunks to ${bot.label}.",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                            Text(
                                text = "• Updates your active credentials to use this standby bot going forward.",
                                fontSize = 11.sp,
                                color = colors.textDim
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Note: Recovery runs per-chunk and reports real-time progress. Standby bots cost zero bandwidth during normal operation.",
                        fontSize = 12.sp,
                        color = colors.textFaint
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetBot = bot
                        botToRecover = null
                        onStartStandbyRecovery?.invoke(targetBot)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.violet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_standby_recovery_button")
                ) {
                    Text("Start Recovery", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { botToRecover = null }) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

    // Active Recovery Progress Dialog
    if (recoveryState.isRecovering) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible while recovering */ },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.violet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = colors.violet
                    )
                }
            },
            title = {
                Text(
                    text = "Recovering Vault Chunks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (recoveryState.totalChunks > 0) {
                            "Recovering chunk ${recoveryState.currentChunk} of ${recoveryState.totalChunks}"
                        } else {
                            "Preparing recovery..."
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recoveryState.statusMessage,
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { recoveryState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.violet,
                        trackColor = colors.surfaceHi
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.mint.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.mint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Server-side copyMessage • 0 MB mobile data used",
                            fontSize = 11.sp,
                            color = colors.mint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCancelStandbyRecovery?.invoke() }
                ) {
                    Text("Cancel Recovery", color = colors.danger)
                }
            }
        )
    }

    // Recovery Completed Dialog
    if (recoveryState.isCompleted) {
        AlertDialog(
            onDismissRequest = { onResetStandbyRecoveryState?.invoke() },
            containerColor = colors.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.mint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.mint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Vault Successfully Recovered!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = recoveryState.statusMessage,
                        fontSize = 13.sp,
                        color = colors.textDim,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your files are now linked to ${recoveryState.standbyBotLabel}. You can continue uploading and downloading as normal.",
                        fontSize = 12.sp,
                        color = colors.mint,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onResetStandbyRecoveryState?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.mint),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = colors.bg)
                }
            }
        )
    }

    // Recovery Error Dialog
    if (recoveryState.errorMessage != null && !recoveryState.isRecovering) {
        AlertDialog(
            onDismissRequest = { onResetStandbyRecoveryState?.invoke() },
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
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Recovery Error",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = recoveryState.errorMessage ?: "An unexpected error occurred during vault recovery.",
                    fontSize = 13.sp,
                    color = colors.textDim,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { onResetStandbyRecoveryState?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // Verification Feedback Dialog
    if (verifyFeedbackMessage != null) {
        val (isSuccess, message) = verifyFeedbackMessage!!
        AlertDialog(
            onDismissRequest = { verifyFeedbackMessage = null },
            containerColor = colors.surface,
            icon = {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSuccess) colors.mint else colors.amber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isSuccess) "Verification Success" else "Membership Verification",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = colors.textDim,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { verifyFeedbackMessage = null }) {
                    Text("OK", color = colors.violet, fontWeight = FontWeight.Bold)
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
            // Bot Revocation Prominent Alert Banner (if active)
            if (primaryBotAlert != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, colors.danger.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .testTag("settings_bot_revocation_banner"),
                    color = colors.danger.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.danger.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Bot Token Alert",
                                    tint = colors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BOT TOKEN INVALID OR REVOKED",
                                    color = colors.danger,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "HTTP ${primaryBotAlert.errorCode ?: "401/403"} • Checked at ${primaryBotAlert.formattedTime}",
                                    color = colors.textDim,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (onDismissPrimaryBotAlert != null) {
                                IconButton(
                                    onClick = onDismissPrimaryBotAlert,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = colors.textDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Your bot token appears to be invalid or revoked — switch to a standby bot in Settings",
                            color = colors.text,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 19.sp
                        )

                        if (primaryBotAlert.errorMessage.isNotBlank() && !primaryBotAlert.errorMessage.contains("Your bot token appears")) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Details: ${primaryBotAlert.errorMessage}",
                                color = colors.textDim,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (standbyBots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val bestBot = standbyBots.firstOrNull { it.isVerifiedMember } ?: standbyBots.first()
                            Button(
                                onClick = { botToRecover = bestBot },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.violet,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_switch_to_standby_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Switch Vault to ${bestBot.label}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

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

                    // Pair Another Device Button
                    if (onPairDeviceClick != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.violet.copy(alpha = 0.12f))
                                .border(1.dp, colors.violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable { onPairDeviceClick() }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("pair_device_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = colors.violet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pair Another Device",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                    Text(
                                        text = "Display encrypted QR code to pair a secondary phone or tablet",
                                        fontSize = 11.sp,
                                        color = colors.textDim
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = colors.violet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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

            // Bot Health Monitoring & History Log Panel
            SettingsCard(
                icon = Icons.Default.History,
                title = "BOT HEALTH MONITORING & HISTORY",
                colors = colors
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Status Overview Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (primaryBotAlert != null) colors.danger else colors.mint)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (primaryBotAlert != null) "Bot Token Revoked / Invalid" else "Primary Bot Token Healthy",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (primaryBotAlert != null) colors.danger else colors.mint
                            )
                            Text(
                                text = "Polled every 15 min & on app foreground via getMe",
                                fontSize = 11.5.sp,
                                color = colors.textDim
                            )
                        }

                        if (onCheckPrimaryBotHealth != null) {
                            TextButton(
                                onClick = onCheckPrimaryBotHealth,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("check_bot_health_now_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = colors.violet,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Check Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.violet
                                )
                            }
                        }
                    }

                    // Expandable Logs Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showBotHealthLogs = !showBotHealthLogs }
                            .background(colors.surfaceHi.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bot Health Check History (${botHealthLogs.size} logs)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.text
                            )
                        }
                        Icon(
                            imageVector = if (showBotHealthLogs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showBotHealthLogs) "Collapse" else "Expand",
                            tint = colors.textDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expanded Log Entries
                    if (showBotHealthLogs) {
                        if (botHealthLogs.isEmpty()) {
                            Text(
                                text = "No health check events logged yet. Background checks run every 15 minutes.",
                                fontSize = 12.sp,
                                color = colors.textFaint,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceHi.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                botHealthLogs.forEach { logLine ->
                                    val isRevocation = logLine.contains("REVOKED") || logLine.contains("401") || logLine.contains("403")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isRevocation) colors.danger else colors.mint)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = logLine,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isRevocation) colors.danger else colors.textDim,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ban-Resilient Standby Bots & copyMessage Recovery Card
            SettingsCard(
                icon = Icons.Default.Security,
                title = "STANDBY BOTS (MULTI-BOT RESILIENCE)",
                colors = colors
            ) {
                Column {
                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zero-Bandwidth Vault Recovery",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text
                        )
                        if (standbyBots.isNotEmpty() && onVerifyAllStandbyBots != null) {
                            TextButton(
                                onClick = onVerifyAllStandbyBots,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.violet
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify All", fontSize = 11.sp, color = colors.violet, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Telegram limits file downloads to the bot that uploaded them unless copied. Standby bots use copyMessage to recover chunk access server-side with zero data usage if your primary bot is banned.",
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Persistent Honest Protection Level Indicator
                    if (verifiedStandbyBotsCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.mint.copy(alpha = 0.12f))
                                .border(1.dp, colors.mint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = colors.mint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Protected: $verifiedStandbyBotsCount standby bot(s) verified",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.mint
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "If your primary bot is banned, file access can be recovered server-side via copyMessage with 0 MB mobile data usage.",
                                        fontSize = 11.sp,
                                        color = colors.textDim,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    } else if (standbyBots.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.amber.copy(alpha = 0.12f))
                                .border(1.dp, colors.amber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Action Required: Standby bots unverified",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.amber
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${standbyBots.size} standby bot(s) added, but channel membership has not been verified yet. Tap 'Verify Membership' below.",
                                        fontSize = 11.sp,
                                        color = colors.textDim,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.danger.copy(alpha = 0.08f))
                                .border(1.dp, colors.danger.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = colors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "No standby bots configured",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.danger
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Files are only accessible through your primary bot. If Telegram bans your bot, chunks cannot be recovered without manual re-upload.",
                                        fontSize = 11.sp,
                                        color = colors.textDim,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Honest limitation notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "ℹ Limitation Notice: Standby bots protect against individual bot bans by maintaining channel membership beforehand. They cannot protect if the Telegram channel itself is deleted or if all registered bots are banned simultaneously.",
                            fontSize = 10.sp,
                            color = colors.textFaint,
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Standby Bots List
                    if (standbyBots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            standbyBots.forEach { bot ->
                                val isVerifying = verifyingBotId == bot.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceHi.copy(alpha = 0.6f))
                                        .border(
                                            width = 1.dp,
                                            color = if (bot.isVerifiedMember) colors.mint.copy(alpha = 0.3f) else colors.line,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = bot.label,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.text
                                                    )
                                                    if (!bot.username.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "@${bot.username}",
                                                            fontSize = 11.sp,
                                                            color = colors.violet
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                val addedText = try {
                                                    dateFormat.format(Date(bot.addedDate))
                                                } catch (e: Exception) {
                                                    ""
                                                }
                                                Text(
                                                    text = "Added $addedText",
                                                    fontSize = 10.sp,
                                                    color = colors.textFaint
                                                )
                                            }

                                            // Status Badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (bot.isVerifiedMember) colors.mint.copy(alpha = 0.15f)
                                                        else colors.amber.copy(alpha = 0.15f)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (bot.isVerifiedMember) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = if (bot.isVerifiedMember) colors.mint else colors.amber,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (bot.isVerifiedMember) "Verified Member" else "Unverified",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (bot.isVerifiedMember) colors.mint else colors.amber
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Actions Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // Verify Membership Button
                                                TextButton(
                                                    onClick = {
                                                        verifyingBotId = bot.id
                                                        onVerifyStandbyBot?.invoke(bot) { success, msg ->
                                                            verifyingBotId = null
                                                            verifyFeedbackMessage = Pair(success, msg)
                                                        }
                                                    },
                                                    enabled = !isVerifying,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    if (isVerifying) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 2.dp,
                                                            color = colors.violet
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Verifying...", fontSize = 11.sp, color = colors.violet)
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(13.dp),
                                                            tint = colors.violet
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Verify Membership", fontSize = 11.sp, color = colors.violet, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }

                                                // Switch & Recover Button
                                                Button(
                                                    onClick = { botToRecover = bot },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.violet.copy(alpha = 0.15f),
                                                        contentColor = colors.violet
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SwapHoriz,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = colors.violet
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Switch & Recover", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = { onDeleteStandbyBot?.invoke(bot) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Standby Bot",
                                                    modifier = Modifier.size(15.dp),
                                                    tint = colors.danger.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No standby bots registered. Add a bot to enable zero-bandwidth recovery in case of primary bot bans.",
                            fontSize = 12.sp,
                            color = colors.textFaint,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Standby Bot Button
                    Button(
                        onClick = {
                            standbyBotTokenInput = ""
                            standbyBotLabelInput = ""
                            addStandbyBotError = null
                            showAddStandbyBotDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_standby_bot_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceHi,
                            contentColor = colors.violet
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.violet
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Standby Bot (Resilience)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.violet
                        )
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

            // Inbound Sharing & Share Sheet Integration
            SettingsCard(
                icon = Icons.Default.Share,
                title = "SHARE SHEET INTEGRATION",
                colors = colors
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prompt for folder on shared files",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "When enabled, shows a folder selector when files are shared to TeleVault from Gallery, Files, or browsers. When disabled, uploads directly to root Vault.",
                                fontSize = 11.sp,
                                color = colors.textDim,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isShareSheetAskFolder,
                            onCheckedChange = { onShareSheetAskFolderChange?.invoke(it) },
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

            // Shared Files & Disposable Relay Channel
            SettingsCard(
                icon = Icons.Default.FolderShared,
                title = "SHARED FILES & RELAY CHANNEL",
                colors = colors
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Disposable relay channels generate single-recipient invite links with automatic expiration, keeping your primary vault completely hidden.",
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 16.sp
                    )

                    // Relay Chat ID Configuration Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceHi.copy(alpha = 0.6f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Relay Channel ID",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.violet
                                    )
                                    Text(
                                        text = relayChatId?.ifBlank { null } ?: "Using primary storage channel as relay",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.text
                                    )
                                }
                                TextButton(
                                    onClick = { isEditingRelayChat = !isEditingRelayChat },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.violet)
                                ) {
                                    Text(if (isEditingRelayChat) "Cancel" else "Configure", fontSize = 12.sp)
                                }
                            }

                            if (isEditingRelayChat) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = relayChatInput,
                                        onValueChange = { relayChatInput = it },
                                        placeholder = { Text("e.g. -100xxxxxxx or empty", fontSize = 12.sp, color = colors.textFaint) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.violet,
                                            unfocusedBorderColor = colors.line,
                                            focusedTextColor = colors.text,
                                            unfocusedTextColor = colors.text
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            onRelayChatIdChange?.invoke(relayChatInput.trim())
                                            isEditingRelayChat = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.violet),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Save", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Shared files list
                    if (sharedFiles.isEmpty()) {
                        Text(
                            text = "No files have been shared via relay yet.",
                            fontSize = 12.sp,
                            color = colors.textFaint,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Active & Revoked Shares (${sharedFiles.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                        sharedFiles.forEach { share ->
                            val isExpired = share.expiryDate != null && System.currentTimeMillis() > share.expiryDate
                            val isRevoked = share.revoked
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceHi.copy(alpha = 0.4f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = share.inviteLink,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.text,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val statusText = when {
                                            isRevoked -> "Revoked"
                                            isExpired -> "Expired"
                                            else -> "Active"
                                        }
                                        val statusColor = when {
                                            isRevoked -> colors.danger
                                            isExpired -> colors.amber
                                            else -> colors.mint
                                        }
                                        Text(
                                            text = "Status: $statusText • ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(share.createdDate))}",
                                            fontSize = 10.sp,
                                            color = statusColor
                                        )
                                    }
                                    if (!isRevoked && !isExpired && onRevokeSharedFile != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { onRevokeSharedFile(share.id) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.danger.copy(alpha = 0.15f),
                                                contentColor = colors.danger
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Revoke", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
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

            // 6. Display & Refresh Rate Card
            SettingsCard(
                icon = Icons.Default.Refresh,
                title = "DISPLAY & REFRESH RATE",
                colors = colors
            ) {
                val context = LocalContext.current
                val displayInfo = remember {
                    try {
                        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.display ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
                        } else {
                            @Suppress("DEPRECATION")
                            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
                        }
                        val modes = display?.supportedModes ?: emptyArray()
                        val maxRate = modes.maxOfOrNull { it.refreshRate }?.toInt() ?: display?.refreshRate?.toInt() ?: 60
                        val currentRate = display?.refreshRate?.toInt() ?: maxRate
                        Pair(currentRate, maxRate)
                    } catch (_: Exception) {
                        Pair(60, 60)
                    }
                }
                val (_, maxFps) = displayInfo
                val isHighRefresh = maxFps >= 90

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHighRefresh) "High Refresh Rate (Active)" else "Display Refresh Rate",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHighRefresh)
                                "Unlocked to hardware peak of $maxFps Hz with full GPU hardware acceleration for ultra-fluid scrolling."
                            else
                                "Configured to device peak ($maxFps Hz) with full GPU hardware acceleration.",
                            fontSize = 11.sp,
                            color = colors.textDim,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHighRefresh) colors.mint.copy(alpha = 0.15f) else colors.surfaceHi)
                            .border(
                                1.dp,
                                if (isHighRefresh) colors.mint.copy(alpha = 0.4f) else colors.line,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$maxFps Hz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighRefresh) colors.mint else colors.text
                        )
                    }
                }
            }

            // 7. Footnote Card (AES-256-GCM)
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
