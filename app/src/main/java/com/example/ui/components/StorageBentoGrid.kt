package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

@Immutable
data class BentoCategoryData(
    val category: StorageCategory,
    val name: String,
    val color: Color,
    val bytes: Long,
    val formattedValue: String,
    val formattedUnit: String,
    val percentage: Int,
    val fraction: Float
)

@Immutable
data class BentoStorageSummary(
    val totalBytes: Long,
    val formattedTotalValue: String,
    val formattedTotalUnit: String,
    val fileCount: Int,
    val folderCount: Int,
    val documents: BentoCategoryData,
    val media: BentoCategoryData,
    val other: BentoCategoryData
)

fun computeBentoStorageSummary(stats: StorageStats): BentoStorageSummary {
    val totalBytes = stats.totalBytesStored
    val (totVal, totUnit) = formatBytesDisplay(totalBytes)

    val docBytes = stats.breakdown.documentsBytes
    val mediaBytes = stats.breakdown.mediaBytes
    val otherBytes = stats.breakdown.otherBytes

    val (docVal, docUnit) = formatBytesDisplay(docBytes)
    val (mediaVal, mediaUnit) = formatBytesDisplay(mediaBytes)
    val (otherVal, otherUnit) = formatBytesDisplay(otherBytes)

    val docPct = if (totalBytes > 0L) ((docBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
    val mediaPct = if (totalBytes > 0L) ((mediaBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
    val otherPct = if (totalBytes > 0L) ((otherBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0

    val docFrac = if (totalBytes > 0L) (docBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val mediaFrac = if (totalBytes > 0L) (mediaBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val otherFrac = if (totalBytes > 0L) (otherBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    return BentoStorageSummary(
        totalBytes = totalBytes,
        formattedTotalValue = totVal,
        formattedTotalUnit = totUnit,
        fileCount = stats.fileCount,
        folderCount = stats.folderCount,
        documents = BentoCategoryData(
            category = StorageCategory.DOCUMENTS,
            name = "Documents",
            color = CategoryViolet,
            bytes = docBytes,
            formattedValue = docVal,
            formattedUnit = docUnit,
            percentage = docPct,
            fraction = docFrac
        ),
        media = BentoCategoryData(
            category = StorageCategory.MEDIA,
            name = "Media",
            color = CategoryTeal,
            bytes = mediaBytes,
            formattedValue = mediaVal,
            formattedUnit = mediaUnit,
            percentage = mediaPct,
            fraction = mediaFrac
        ),
        other = BentoCategoryData(
            category = StorageCategory.OTHER,
            name = "Other",
            color = CategoryAmber,
            bytes = otherBytes,
            formattedValue = otherVal,
            formattedUnit = otherUnit,
            percentage = otherPct,
            fraction = otherFrac
        )
    )
}

/**
 * Bento-Grid Storage Breakdown Component (Performance Optimized):
 * - Large full-width hero tile: Total vault storage used in monospace + "No archive limit" pill badge.
 * - 2x2 grid of smaller tiles:
 *   - Documents (violet) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Media (teal) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Other (amber) tile with value, percentage, animated mini-bar, and filter toggle.
 *   - Vault Items tile consolidating Files & Folders counts and active transfers shortcut.
 *
 * Performance optimizations:
 * 1. Storage breakdown calculations and string formatting are memoized via `remember(stats)` in `BentoStorageSummary`.
 * 2. Entrance animations are gated by `rememberSaveable { hasEntrancePlayed }`, preventing unnecessary replays.
 * 3. Entrance animation duration is reduced to 220ms with 40ms stagger delays (total <= 380ms).
 * 4. Animations use draw-phase `graphicsLayer` lambda evaluations to avoid recomposing Composable bodies on frames.
 * 5. Mini-bar width is animated in an isolated sub-composable via `animateFloatAsState` (240ms duration).
 * 6. Reduced-motion setting immediately snaps all states to 1f and disables animations.
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

    // Memoize category breakdown, percentages, and string formatting
    val summary = remember(stats) { computeBentoStorageSummary(stats) }

    // Gated entrance animation: play once on initial display, never replay on scroll or data refresh
    var hasEntrancePlayed by rememberSaveable { mutableStateOf(false) }
    val shouldAnimate = !reduceMotion && !hasEntrancePlayed

    val heroAnim = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val docAnim = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val mediaAnim = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val otherAnim = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val statsAnim = remember { Animatable(if (shouldAnimate) 0f else 1f) }

    LaunchedEffect(hasEntrancePlayed, reduceMotion) {
        if (shouldAnimate) {
            // Short 220ms duration with tight 40ms stagger delays
            val animSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
            launch { heroAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(40); docAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(80); mediaAnim.animateTo(1f, animationSpec = animSpec) }
            launch { delay(120); otherAnim.animateTo(1f, animationSpec = animSpec) }
            launch {
                delay(160)
                statsAnim.animateTo(1f, animationSpec = animSpec)
                hasEntrancePlayed = true
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -----------------------------------------------------------------
        // 1. Large Hero Tile (Full Width)
        // -----------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = heroAnim.value
                    alpha = p
                    val s = 0.97f + (0.03f * p)
                    scaleX = s
                    scaleY = s
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
                        targetState = summary.formattedTotalValue,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(100))
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
                        targetState = summary.formattedTotalUnit,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(100))
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
                    text = "used across ${summary.fileCount} encrypted file${if (summary.fileCount == 1) "" else "s"}",
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
            CategoryBentoTile(
                categoryData = summary.documents,
                isSelected = selectedCategory == StorageCategory.DOCUMENTS,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.DOCUMENTS,
                animProgress = { docAnim.value },
                reduceMotion = reduceMotion,
                onClick = { onCategoryClick(StorageCategory.DOCUMENTS) },
                modifier = Modifier.weight(1f)
            )

            CategoryBentoTile(
                categoryData = summary.media,
                isSelected = selectedCategory == StorageCategory.MEDIA,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.MEDIA,
                animProgress = { mediaAnim.value },
                reduceMotion = reduceMotion,
                onClick = { onCategoryClick(StorageCategory.MEDIA) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row B: Other & Vault Items (Consolidated Quick Stats)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryBentoTile(
                categoryData = summary.other,
                isSelected = selectedCategory == StorageCategory.OTHER,
                isDimmed = selectedCategory != null && selectedCategory != StorageCategory.OTHER,
                animProgress = { otherAnim.value },
                reduceMotion = reduceMotion,
                onClick = { onCategoryClick(StorageCategory.OTHER) },
                modifier = Modifier.weight(1f)
            )

            VaultStatsBentoTile(
                fileCount = summary.fileCount,
                folderCount = summary.folderCount,
                activeTransfersCount = activeTransfersCount,
                animProgress = { statsAnim.value },
                onTransfersClick = onTransfersClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Category Bento Tile:
 * Shows indicator dot, category name, percentage badge, monospace value, and an animated mini-bar.
 * Animation progress is read strictly in graphicsLayer { ... } to avoid recomposing on animation frames.
 */
@Composable
private fun CategoryBentoTile(
    categoryData: BentoCategoryData,
    isSelected: Boolean,
    isDimmed: Boolean,
    animProgress: () -> Float,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = when {
        isPressed -> 0.97f
        isSelected -> 1.02f
        else -> 1.0f
    }

    val tileBg = if (isSelected) {
        categoryData.color.copy(alpha = if (colors.isDark) 0.15f else 0.08f)
    } else {
        colors.surface
    }

    val borderColor = if (isSelected) categoryData.color else colors.line
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val contentAlpha = if (isDimmed) 0.45f else 1.0f

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = animProgress()
                alpha = p * contentAlpha
                val animScale = 0.97f + (0.03f * p)
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
            .testTag("bento_tile_${categoryData.category.name.lowercase()}")
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
                            .background(categoryData.color)
                    )
                    Text(
                        text = categoryData.name,
                        fontSize = 12.5.sp,
                        fontFamily = BodySansFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) categoryData.color else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${categoryData.percentage}%",
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) categoryData.color else colors.textDim
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Value in Monospace
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = categoryData.formattedValue,
                    fontFamily = NumericMonoFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = categoryData.formattedUnit,
                    fontFamily = NumericMonoFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = categoryData.color,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Isolated mini-bar composable (only this small Box recomposes on animation)
            CategoryMiniBar(
                fraction = categoryData.fraction,
                color = categoryData.color,
                reduceMotion = reduceMotion
            )
        }
    }
}

/**
 * Lightweight mini-bar:
 * Uses animateFloatAsState driving Modifier.fillMaxWidth(fraction) on a simple colored Box.
 * Isolated from parent tiles so that fill animations do not trigger parent recompositions.
 */
@Composable
private fun CategoryMiniBar(
    fraction: Float,
    color: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val targetFraction = fraction.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bento_minibar"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.surfaceHi)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress.coerceAtLeast(if (fraction > 0f) 0.04f else 0f))
                .clip(RoundedCornerShape(100.dp))
                .background(color)
        )
    }
}

/**
 * Vault Stats Bento Tile:
 * Fourth tile consolidating the quick stats (Files & Folders count + Transfers action/indicator).
 */
@Composable
private fun VaultStatsBentoTile(
    fileCount: Int,
    folderCount: Int,
    activeTransfersCount: Int,
    animProgress: () -> Float,
    onTransfersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = animProgress()
                alpha = p
                val animScale = 0.97f + (0.03f * p)
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
            // Top Row: Dot + Title + Transfers pill
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
