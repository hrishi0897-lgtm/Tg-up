package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.MonoStatValueLarge

/**
 * Quick stats bar matching the HTML redesign mockup:
 * A horizontal card below the storage ring with Files / Folders / Transfers,
 * 3 columns with thin dividers, monospace numbers, small caption labels.
 */
@Composable
fun QuickStatsBar(
    filesCount: Int,
    foldersCount: Int,
    activeTransfersCount: Int,
    onTransfersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Files
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$filesCount",
                style = MonoStatValueLarge.copy(color = colors.text, fontSize = 15.sp)
            )
            Text(
                text = "Files",
                fontSize = 11.sp,
                fontFamily = BodySansFont,
                color = colors.textFaint
            )
        }

        // Thin vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(colors.line)
        )

        // 2. Folders
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$foldersCount",
                style = MonoStatValueLarge.copy(color = colors.text, fontSize = 15.sp)
            )
            Text(
                text = "Folders",
                fontSize = 11.sp,
                fontFamily = BodySansFont,
                color = colors.textFaint
            )
        }

        // Thin vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(colors.line)
        )

        // 3. Transfers
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTransfersClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (activeTransfersCount > 0) {
                Text(
                    text = "$activeTransfersCount active",
                    style = MonoStatValueLarge.copy(color = colors.teal, fontSize = 14.sp)
                )
            } else {
                Text(
                    text = "Idle",
                    fontSize = 13.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Medium,
                    color = colors.textFaint
                )
            }
            Text(
                text = "Transfers",
                fontSize = 11.sp,
                fontFamily = BodySansFont,
                color = colors.textFaint
            )
        }
    }
}
