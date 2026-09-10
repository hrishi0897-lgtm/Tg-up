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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.MotionSpecs
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen

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

    // Animate tab colors smoothly
    val vaultColor by animateColorAsState(
        targetValue = if (isVault) colors.text else colors.textFaint,
        animationSpec = tween(durationMillis = 200),
        label = "vault_tab_color"
    )
    val transfersColor by animateColorAsState(
        targetValue = if (!isVault) colors.text else colors.textFaint,
        animationSpec = tween(durationMillis = 200),
        label = "transfers_tab_color"
    )

    Surface(
        color = colors.navBg,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(
                width = 1.dp,
                color = colors.line,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 6.dp)
        ) {
            val totalWidth = maxWidth
            val halfWidth = totalWidth / 2
            val indicatorWidth = 22.dp

            // Center of Tab 0 is halfWidth / 2; center of Tab 1 is halfWidth + (halfWidth / 2)
            val vaultCenterX = halfWidth * 0.5f
            val transfersCenterX = halfWidth * 1.5f

            val targetIndicatorOffset = if (isVault) {
                vaultCenterX - (indicatorWidth / 2)
            } else {
                transfersCenterX - (indicatorWidth / 2)
            }

            val animatedIndicatorOffset by animateDpAsState(
                targetValue = targetIndicatorOffset,
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = 0.72f,
                    stiffness = 320f
                ),
                label = "nav_indicator_slide"
            )

            // Underline sliding indicator
            Box(
                modifier = Modifier
                    .offset(x = animatedIndicatorOffset, y = 52.dp)
                    .width(indicatorWidth)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.violet)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vault Nav Item
                val vaultInteraction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(pressedScale = 0.92f, interactionSource = vaultInteraction)
                        .clickable(
                            interactionSource = vaultInteraction,
                            indication = null,
                            onClick = onVaultSelected
                        )
                        .padding(vertical = 6.dp)
                        .testTag("tab_nav_vault"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Vault",
                        tint = vaultColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Vault",
                        fontSize = 11.5.sp,
                        fontFamily = BodySansFont,
                        fontWeight = if (isVault) FontWeight.SemiBold else FontWeight.Medium,
                        color = vaultColor
                    )
                }

                // Transfers Nav Item
                val transfersInteraction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(pressedScale = 0.92f, interactionSource = transfersInteraction)
                        .clickable(
                            interactionSource = transfersInteraction,
                            indication = null,
                            onClick = onTransfersSelected
                        )
                        .padding(vertical = 6.dp)
                        .testTag("tab_nav_transfers"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                tint = transfersColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Transfers",
                            tint = transfersColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Transfers",
                        fontSize = 11.5.sp,
                        fontFamily = BodySansFont,
                        fontWeight = if (!isVault) FontWeight.SemiBold else FontWeight.Medium,
                        color = transfersColor
                    )
                }
            }
        }
    }
}
