package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen

private val FloatingNavShape = RoundedCornerShape(26.dp)
private val ActiveCapsuleShape = RoundedCornerShape(20.dp)

@Composable
fun TeleVaultBottomNav(
    currentScreen: AppScreen,
    activeTransferCount: Int,
    onVaultSelected: () -> Unit,
    onTransfersSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current
    val isVault = currentScreen == AppScreen.VAULT

    val vaultColor by animateColorAsState(
        targetValue = if (isVault) colors.text else colors.textDim,
        animationSpec = tween(durationMillis = 200),
        label = "vault_tab_color"
    )
    val transfersColor by animateColorAsState(
        targetValue = if (!isVault) colors.text else colors.textDim,
        animationSpec = tween(durationMillis = 200),
        label = "transfers_tab_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.navBg,
            shape = FloatingNavShape,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(
                    width = 1.dp,
                    color = colors.violet.copy(alpha = 0.28f),
                    shape = FloatingNavShape
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                val innerWidth = maxWidth
                val capsuleWidth = innerWidth / 2
                val targetCapsuleOffset = if (isVault) 0.dp else capsuleWidth

                val animatedCapsuleOffset by animateDpAsState(
                    targetValue = targetCapsuleOffset,
                    animationSpec = if (reduceMotion) snap() else spring(
                        dampingRatio = 0.76f,
                        stiffness = 380f
                    ),
                    label = "nav_capsule_slide"
                )

                // Sliding active capsule in solid Signature Violet opacity shade
                Box(
                    modifier = Modifier
                        .offset(x = animatedCapsuleOffset)
                        .width(capsuleWidth)
                        .fillMaxHeight()
                        .clip(ActiveCapsuleShape)
                        .background(
                            colors.violet.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                        )
                        .border(
                            width = 1.dp,
                            color = colors.violet.copy(alpha = 0.42f),
                            shape = ActiveCapsuleShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vault Nav Item
                    val vaultInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(pressedScale = 0.94f, interactionSource = vaultInteraction)
                            .clickable(
                                interactionSource = vaultInteraction,
                                indication = null,
                                onClick = onVaultSelected
                            )
                            .testTag("tab_nav_vault"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Vault",
                            tint = if (isVault) colors.violet else vaultColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vault",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            fontWeight = if (isVault) FontWeight.Bold else FontWeight.Medium,
                            color = vaultColor
                        )
                    }

                    // Transfers Nav Item
                    val transfersInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(pressedScale = 0.94f, interactionSource = transfersInteraction)
                            .clickable(
                                interactionSource = transfersInteraction,
                                indication = null,
                                onClick = onTransfersSelected
                            )
                            .testTag("tab_nav_transfers"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeTransferCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = colors.violet,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = activeTransferCount.toString(),
                                            fontSize = 10.sp,
                                            fontFamily = NumericMonoFont,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Transfers",
                                    tint = if (!isVault) colors.violet else transfersColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Transfers",
                                tint = if (!isVault) colors.violet else transfersColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transfers",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            fontWeight = if (!isVault) FontWeight.Bold else FontWeight.Medium,
                            color = transfersColor
                        )
                    }
                }
            }
        }
    }
}

