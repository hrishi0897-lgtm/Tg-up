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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledCard
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun OnboardingScreen(
    isValidating: Boolean,
    validationError: String?,
    onConnect: (token: String, chatId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
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
                    .background(TelegramBlue.copy(alpha = 0.15f))
                    .border(1.5.dp, TelegramBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "TeleVault Cloud",
                    tint = TelegramBlue,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TeleVault",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Free, unlimited personal cloud storage backed by your private Telegram account",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Credentials Card
            Card(
                colors = CardDefaults.cardColors(containerColor = OledCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
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
                        color = TelegramBlue,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bot Token field
                    Text(
                        text = "Telegram Bot Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        placeholder = {
                            Text(
                                "123456789:ABCdefGHIjklMNOpqr...",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token visibility",
                                    tint = TextSecondary
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
                            focusedBorderColor = TelegramBlue,
                            unfocusedBorderColor = OledBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = TelegramBlue,
                            focusedContainerColor = OledBlack,
                            unfocusedContainerColor = OledBlack
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
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        placeholder = {
                            Text(
                                "e.g. 987654321 or -100123456789",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = TextSecondary,
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
                                    onConnect(botToken, chatId)
                                }
                            }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelegramBlue,
                            unfocusedBorderColor = OledBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = TelegramBlue,
                            focusedContainerColor = OledBlack,
                            unfocusedContainerColor = OledBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_id_input")
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
                                    color = Color(0xFFFF8A80),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Connect button
                    Button(
                        onClick = { onConnect(botToken, chatId) },
                        enabled = !isValidating && botToken.isNotBlank() && chatId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelegramBlue,
                            contentColor = OledBlack,
                            disabledContainerColor = OledSurfaceVariant,
                            disabledContentColor = TextTertiary
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
                                color = OledBlack,
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guide Toggle Button
            TextButton(
                onClick = { showGuide = !showGuide },
                colors = ButtonDefaults.textButtonColors(contentColor = TelegramBlue)
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
                    colors = CardDefaults.cardColors(containerColor = OledCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
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
                            color = TelegramBlue,
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
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stored encrypted at rest via hardware Android KeyStore (AES-256)",
                    fontSize = 11.sp,
                    color = TextTertiary,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(TelegramBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = TelegramBlue,
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
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}
