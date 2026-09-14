package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.StorageCategory
import com.example.domain.model.StorageStats
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.CategoryAmber
import com.example.ui.theme.CategoryTeal
import com.example.ui.theme.CategoryViolet
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatBytesDisplay(bytes: Long): Pair<String, String> {
    if (bytes <= 0L) return Pair("0", "B")
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024
    val b = bytes.toDouble()
    return when {
        b >= tb -> Pair(String.format(java.util.Locale.US, "%.1f", b / tb), "TB")
        b >= gb -> Pair(String.format(java.util.Locale.US, "%.1f", b / gb), "GB")
        b >= mb -> Pair(String.format(java.util.Locale.US, "%.1f", b / mb), "MB")
        b >= kb -> Pair(String.format(java.util.Locale.US, "%.0f", b / kb), "KB")
        else -> Pair("$bytes", "B")
    }
}

/**
 * Bento-Grid Storage Breakdown Component:
 * Replaces the multi-segment circular donut chart with an asymmetrical bento grid:
 * - Large full-width hero tile: Total vault storage used in big monospace + "No archive limit" pill badge.
 * - 2x2 grid of smaller tiles:
 *   - Documents (violet) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Media (teal) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Other (amber) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Vault Items tile consolidating Files & Folders counts and active transfers shortcut.
 */
@Composable
fun StorageBentoGrid(
    stats: StorageStats,
    selectedCategory: StorageCategory?,
    onCategoryClick: (StorageCategory) -> Unit,
    activeTransfersCount: Int = 0,
    onTransfersClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current

    val totalBytes = stats.totalBytesStored
    val docBytes = stats.breakdown.documentsBytes
    val mediaBytes = stats.breakdown.mediaBytes
    val otherBytes = stats.breakdown.otherBytes

    // Staggered entrance animations
    val heroAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val docAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val mediaAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val otherAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val statsAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            val animSpec = tween<Float>(durationMillis = 400, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
            launch { heroAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(60); docAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(120); mediaAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(180); otherAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(240); statsAnim.animateTo(1f, animationSpec = animSpec) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -----------------------------------------------------------------
        // 1. Large Hero Tile (Full Width)
        // -----------------------------------------------------------------
        val (totalVal, totalUnit) = remember(totalBytes) { formatBytesDisplay(totalBytes) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = heroAnim.value
                    val scaleFactor = 0.96f + (0.04f * heroAnim.value)
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row: "Vault storage" caption + "No archive limit" pill badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vault storage",
                        fontSize = 13.sp,
                        fontFamily = BodySansFont,
                        fontWeight = FontWeight.Medium,
                        color = colors.textDim
                    )

                    // "No archive limit" Pill Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(colors.teal.copy(alpha = 0.12f))
                            .border(1.dp, colors.teal.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.teal)
                        )
                        Text(
                            text = "No archive limit",
                            fontSize = 11.5.sp,
                            fontFamily = BodySansFont,
                            fontWeight = FontWeight.Medium,
                            color = colors.teal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big Monospace Number + Unit
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    AnimatedContent(
                        targetState = totalVal,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "hero_total_val"
                    ) { v ->
                        Text(
                            text = v,
                            fontFamily = NumericMonoFont,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = totalUnit,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "hero_total_unit"
                    ) { u ->
                        Text(
                            text = u,
                            fontFamily = NumericMonoFont,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.violet,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // "used" caption below
                Text(
                    text = "used across ${stats.fileCount} encrypted file${if (stats.fileCount == 1) "" else "s"}",
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    color = colors.textFaint
                )
            }
        }

        // -----------------------------------------------------------------
        // 2. 2-Column Bento Grid Rows (Documents, Media, Other, Vault Items)
        // -----------------------------------------------------------------

        // Row A: Documents & Media
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile: Documents
            CategoryBentoTile(
                category = StorageCategory.DOCUMENTS,
                name = "Documents",
                color = CategoryViolet,
                bytes = docBytes,
                totalBytes = totalBytes,
                isSelected = selectedCategory == StorageCategory.DOCUMENTS,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.DOCUMENTS,
                animProgress = docAnim.value,
                onClick = { onCategoryClick(StorageCategory.DOCUMENTS) },
                modifier = Modifier.weight(1f)
            )

            // Tile: Media
            CategoryBentoTile(
                category = StorageCategory.MEDIA,
                name = "Media",
                color = CategoryTeal,
                bytes = mediaBytes,
                totalBytes = totalBytes,
                isSelected = selectedCategory == StorageCategory.MEDIA,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.MEDIA,
                animProgress = mediaAnim.value,
                onClick = { onCategoryClick(StorageCategory.MEDIA) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row B: Other & Vault Items (Consolidated Quick Stats)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile: Other
            CategoryBentoTile(
                category = StorageCategory.OTHER,
                name = "Other",
                color = CategoryAmber,
                bytes = otherBytes,
                totalBytes = totalBytes,
                isSelected = selectedCategory == StorageCategory.OTHER,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.OTHER,
                animProgress = otherAnim.value,
                onClick = { onCategoryClick(StorageCategory.OTHER) },
                modifier = Modifier.weight(1f)
            )

            // Tile: Vault Items (Consolidated Files, Folders, & Transfers)
            VaultStatsBentoTile(
                fileCount = stats.fileCount,
                folderCount = stats.folderCount,
                activeTransfersCount = activeTransfersCount,
                animProgress = statsAnim.value,
                onTransfersClick = onTransfersClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Category Bento Tile:
 * Shows a colored indicator dot, category name, percentage badge, monospace value,
 * and an animated horizontal mini-bar representing that category's share of total storage.
 * Tapping highlights the tile with accent border and triggers category filtering.
 */
@Composable
private fun CategoryBentoTile(
    category: StorageCategory,
    name: String,
    color: Color,
    bytes: Long,
    totalBytes: Long,
    isSelected: Boolean,
    isDimmed: Boolean,
    animProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val (valueStr, unitStr) = remember(bytes) { formatBytesDisplay(bytes) }
    val pct = if (totalBytes > 0L) ((bytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
    val targetFraction = if (totalBytes > 0L) (bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    // Animated mini-bar fill width
    val animatedProgress by animateFloatAsState(
        targetValue = if (reduceMotion) targetFraction else targetFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "mini_bar_${category.name}"
    )

    // Pressed & selection scale
    val scale = when {
        isPressed -> 0.97f
        isSelected -> 1.02f
        else -> 1.0f
    }

    val tileBg = if (isSelected) {
        color.copy(alpha = if (colors.isDark) 0.15f else 0.08f)
    } else {
        colors.surface
    }

    val borderColor = if (isSelected) {
        color
    } else {
        colors.line
    }

    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val contentAlpha = if (isDimmed) 0.45f else 1.0f

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = animProgress * contentAlpha
                val animScale = 0.94f + (0.06f * animProgress)
                scaleX = animScale * scale
                scaleY = animScale * scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(tileBg)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag("bento_tile_${category.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Colored Dot + Name + Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = name,
                        fontSize = 12.5.sp,
                        fontFamily = BodySansFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) color else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "$pct%",
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) color else colors.textDim
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Value in Monospace
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = valueStr,
                    fontFamily = NumericMonoFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unitStr,
                    fontFamily = NumericMonoFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thin Horizontal Mini-Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(colors.surfaceHi)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress.coerceAtLeast(if (bytes > 0) 0.04f else 0f))
                        .clip(RoundedCornerShape(100.dp))
                        .background(color)
                )
            }
        }
    }
}

/**
 * Vault Stats Bento Tile:
 * Fourth tile consolidating the quick stats (Files & Folders count + Transfers action/indicator)
 * perfectly balancing the 2x2 grid.
 */
@Composable
private fun VaultStatsBentoTile(
    fileCount: Int,
    folderCount: Int,
    activeTransfersCount: Int,
    animProgress: Float,
    onTransfersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = animProgress
                val animScale = 0.94f + (0.06f * animProgress)
                scaleX = animScale
                scaleY = animScale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("bento_tile_vault_items")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Icon/Dot + Title + Transfers pill
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(colors.textDim)
                    )
                    Text(
                        text = "Vault Items",
                        fontSize = 12.5.sp,
                        fontFamily = BodySansFont,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textDim,
                        maxLines = 1
                    )
                }

                if (activeTransfersCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(colors.teal.copy(alpha = 0.15f))
                            .clickable(onClick = onTransfersClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$activeTransfersCount live",
                            fontSize = 9.5.sp,
                            fontFamily = NumericMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Middle: Files & Folders Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Files
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$fileCount",
                        fontFamily = NumericMonoFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        text = "files",
                        fontFamily = BodySansFont,
                        fontSize = 10.5.sp,
                        color = colors.textFaint
                    )
                }

                // Thin vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(colors.line)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Folders
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$folderCount",
                        fontFamily = NumericMonoFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        text = "folders",
                        fontFamily = BodySansFont,
                        fontSize = 10.5.sp,
                        color = colors.textFaint
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom: Transfers clickable link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTransfersClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = if (activeTransfersCount > 0) colors.teal else colors.textFaint,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (activeTransfersCount > 0) "$activeTransfersCount in flight" else "Transfers idle",
                        fontFamily = BodySansFont,
                        fontSize = 10.5.sp,
                        color = if (activeTransfersCount > 0) colors.teal else colors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "View →",
                    fontFamily = BodySansFont,
                    fontSize = 10.sp,
                    color = colors.textDim
                )
            }
        }
    }
}
