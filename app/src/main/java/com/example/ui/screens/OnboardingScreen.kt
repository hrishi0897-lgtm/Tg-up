package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.RestoreResult
import com.example.ui.components.QrScannerDialog
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@Composable
fun OnboardingScreen(
    isValidating: Boolean,
    validationError: String?,
    onConnect: (token: String, chatId: String, backupChatId: String?) -> Unit,
    modifier: Modifier = Modifier,
    isRestoringBackup: Boolean = false,
    restoreError: String? = null,
    restoreResult: RestoreResult? = null,
    onRestoreFromBackup: (backupChatId: String, botToken: String?) -> Unit = { _, _ -> },
    onDismissRestoreResult: () -> Unit = {}
) {
    val colors = LocalTeleVaultColors.current
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var backupChatId by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreBotTokenInput by remember { mutableStateOf("") }
    var restoreChannelIdInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.violet.copy(alpha = 0.15f))
                    .border(1.5.dp, colors.violet.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "TeleVault Cloud",
                    tint = colors.violet,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TeleVault",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Free, unlimited personal cloud storage backed by your private Telegram account",
                fontSize = 14.sp,
                color = colors.textDim,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Credentials Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "CONNECT YOUR TELEGRAM BOT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.violet,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bot Token field
                    Text(
                        text = "Telegram Bot Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        placeholder = {
                            Text(
                                "123456789:ABCdefGHIjklMNOpqr...",
                                color = colors.textFaint,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token visibility",
                                    tint = colors.textDim
                                )
                            }
                        },
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.violet,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            cursorColor = colors.violet,
                            focusedContainerColor = colors.bg,
                            unfocusedContainerColor = colors.bg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bot_token_input")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Chat ID field
                    Text(
                        text = "Your Telegram Chat ID",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        placeholder = {
                            Text(
                                "e.g. 987654321 or -100123456789",
                                color = colors.textFaint,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (botToken.isNotBlank() && chatId.isNotBlank()) {
                                    onConnect(botToken, chatId, backupChatId.takeIf { it.isNotBlank() })
                                }
                            }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.violet,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            cursorColor = colors.violet,
                            focusedContainerColor = colors.bg,
                            unfocusedContainerColor = colors.bg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_id_input")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Backup Channel ID field (Optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Backup Channel ID",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                        Text(
                            text = "Optional · Redundant",
                            fontSize = 11.sp,
                            color = colors.teal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Every chunk is auto-mirrored via copyMessage to this channel with zero extra mobile data.",
                        fontSize = 11.sp,
                        color = colors.textDim,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = backupChatId,
                        onValueChange = { backupChatId = it },
                        placeholder = {
                            Text(
                                "e.g. -100987654321",
                                color = colors.textFaint,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.teal,
                            unfocusedBorderColor = colors.line,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            cursorColor = colors.teal,
                            focusedContainerColor = colors.bg,
                            unfocusedContainerColor = colors.bg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_channel_id_input")
                    )

                    // Error display if validation failed
                    AnimatedVisibility(visible = validationError != null) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StatusError.copy(alpha = 0.12f))
                                    .border(1.dp, StatusError.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = validationError ?: "",
                                    fontSize = 12.sp,
                                    color = StatusError,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Connect button
                    Button(
                        onClick = { onConnect(botToken, chatId, backupChatId.takeIf { it.isNotBlank() }) },
                        enabled = !isValidating && botToken.isNotBlank() && chatId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.violet,
                            contentColor = Color.White,
                            disabledContainerColor = colors.surfaceHi,
                            disabledContentColor = colors.textFaint
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_button")
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Validating Bot with Telegram...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                "Connect & Verify Vault",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.line))
                        Text(
                            text = "OR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textFaint,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.line))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scan QR to Pair button
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("scan_qr_pair_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.violet.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.violet)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Scan QR to Pair from Another Device",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (showScanner) {
                QrScannerDialog(
                    onDismiss = { showScanner = false },
                    onScanned = { pairing ->
                        showScanner = false
                        botToken = pairing.token
                        chatId = pairing.chatId
                        onConnect(pairing.token, pairing.chatId, null)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disaster Recovery Section
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.teal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DISASTER RECOVERY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.teal,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Restore Vault from Backup Channel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.text
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lost your primary bot or channel? Rebuild your entire vault from televault_index.json in your secondary backup channel without needing any previous local data.",
                        fontSize = 12.sp,
                        color = colors.textDim,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (botToken.isNotBlank()) restoreBotTokenInput = botToken
                            showRestoreDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("onboarding_restore_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.teal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Restore from Backup Channel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (showRestoreDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isRestoringBackup) showRestoreDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disaster Recovery", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Enter your Telegram Bot Token and the Backup Channel ID where chunks and televault_index.json were mirrored.",
                                fontSize = 13.sp,
                                color = colors.textDim
                            )

                            OutlinedTextField(
                                value = restoreBotTokenInput,
                                onValueChange = { restoreBotTokenInput = it },
                                label = { Text("Bot Token") },
                                placeholder = { Text("123456789:ABCdef...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("restore_bot_token_input")
                            )

                            OutlinedTextField(
                                value = restoreChannelIdInput,
                                onValueChange = { restoreChannelIdInput = it },
                                label = { Text("Backup Channel ID") },
                                placeholder = { Text("-100123456789") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("restore_backup_channel_input")
                            )

                            if (restoreError != null) {
                                Text(
                                    text = restoreError,
                                    color = StatusError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val tokenToUse = restoreBotTokenInput.trim().ifBlank { botToken.trim() }
                                onRestoreFromBackup(
                                    restoreChannelIdInput.trim(),
                                    tokenToUse.takeIf { it.isNotBlank() }
                                )
                            },
                            enabled = !isRestoringBackup && restoreChannelIdInput.isNotBlank() && (restoreBotTokenInput.isNotBlank() || botToken.isNotBlank()),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            modifier = Modifier.testTag("confirm_restore_button")
                        ) {
                            if (isRestoringBackup) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rebuilding Vault...")
                            } else {
                                Text("Rebuild Vault")
                            }
                        }
                    },
                    dismissButton = {
                        if (!isRestoringBackup) {
                            TextButton(onClick = { showRestoreDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }

            if (restoreResult != null) {
                AlertDialog(
                    onDismissRequest = {
                        showRestoreDialog = false
                        onDismissRestoreResult()
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (restoreResult.brokenFilesCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (restoreResult.brokenFilesCount > 0) colors.amber else colors.mint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Complete", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Successfully recovered from backup channel!",
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                                fontSize = 14.sp
                            )
                            Text("• Files restored: ${restoreResult.restoredFilesCount}", fontSize = 13.sp, color = colors.textDim)
                            Text("• Folders restored: ${restoreResult.restoredFoldersCount}", fontSize = 13.sp, color = colors.textDim)
                            Text("• Status: ${restoreResult.summary}", fontSize = 13.sp, color = colors.textDim)

                            if (restoreResult.brokenFilesCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ ${restoreResult.brokenFilesCount} file(s) have missing chunks and are flagged as BROKEN:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.danger
                                )
                                restoreResult.brokenFileNames.take(5).forEach { name ->
                                    Text("  - $name", fontSize = 12.sp, color = colors.danger)
                                }
                                if (restoreResult.brokenFileNames.size > 5) {
                                    Text("  ...and ${restoreResult.brokenFileNames.size - 5} more", fontSize = 12.sp, color = colors.textFaint)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showRestoreDialog = false
                                onDismissRestoreResult()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                        ) {
                            Text("Open Vault")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guide Toggle Button
            TextButton(
                onClick = { showGuide = !showGuide },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.violet)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (showGuide) "Hide Step-by-Step Guide" else "How to get Bot Token & Chat ID?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Interactive Step-by-Step Guide
            AnimatedVisibility(visible = showGuide) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "3-STEP SETUP GUIDE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.violet,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        GuideStep(
                            stepNumber = "1",
                            title = "Create Your Storage Bot",
                            description = "Open Telegram, search for @BotFather and send the message /newbot. Follow the prompts to set a name (e.g. 'My Personal Vault') and unique username ending in 'bot'."
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        GuideStep(
                            stepNumber = "2",
                            title = "Copy the Bot Token",
                            description = "BotFather will send you an HTTP API access token (e.g. 123456789:ABC...). Copy this string and paste it into the Bot Token field above."
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        GuideStep(
                            stepNumber = "3",
                            title = "Start Your Bot & Get Chat ID",
                            description = "Search for your new bot in Telegram and press START (or send /start). Then forward a message to @userinfobot or open web.telegram.org to find your numeric ID (e.g. 987654321). Paste it into the Chat ID field."
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = colors.textFaint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stored encrypted at rest via hardware Android KeyStore (AES-256)",
                    fontSize = 11.sp,
                    color = colors.textFaint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GuideStep(
    stepNumber: String,
    title: String,
    description: String
) {
    val colors = LocalTeleVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.violet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = colors.violet,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = colors.textDim,
                lineHeight = 17.sp
            )
        }
    }
}
