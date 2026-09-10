package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.MonoStatValueLarge
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
 * Storage Donut Ring + Category Legend matching the HTML mockup:
 * - 3-segment donut ring: Documents (Violet #8C7CFF), Media (Teal #35E0C2), Other (Amber #F2B84B)
 * - Glow / drop-shadow per segment
 * - Staggered entrance animation on load
 * - Tappable category legend below/beside: tapping a category highlights its segment & dims others; tapping again deselects
 */
@Composable
fun StorageDonutRingCard(
    stats: StorageStats,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current

    var selectedCategory by remember { mutableStateOf<StorageCategory?>(null) }

    // Staggered entrance animations
    val docAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val mediaAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val otherAnim = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            val animSpec = tween<Float>(durationMillis = 650, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
            launch {
                docAnim.animateTo(1f, animationSpec = animSpec)
            }
            launch {
                delay(80)
                mediaAnim.animateTo(1f, animationSpec = animSpec)
            }
            launch {
                delay(160)
                otherAnim.animateTo(1f, animationSpec = animSpec)
            }
        }
    }

    val docBytes = stats.breakdown.documentsBytes
    val mediaBytes = stats.breakdown.mediaBytes
    val otherBytes = stats.breakdown.otherBytes
    val totalBytes = stats.totalBytesStored

    // Center display value & unit
    val (centerVal, centerUnit) = remember(selectedCategory, totalBytes, docBytes, mediaBytes, otherBytes) {
        when (selectedCategory) {
            StorageCategory.DOCUMENTS -> {
                val (v, u) = formatBytesDisplay(docBytes)
                Pair("$v $u", "Documents")
            }
            StorageCategory.MEDIA -> {
                val (v, u) = formatBytesDisplay(mediaBytes)
                Pair("$v $u", "Media")
            }
            StorageCategory.OTHER -> {
                val (v, u) = formatBytesDisplay(otherBytes)
                Pair("$v $u", "Other")
            }
            null -> {
                val (v, u) = formatBytesDisplay(totalBytes)
                Pair("$v $u", "used")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        Column {
            // Header row: "Vault storage" + "No archive limit" badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vault storage",
                    fontSize = 13.5.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Medium,
                    color = colors.textDim
                )
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

            Spacer(modifier = Modifier.height(20.dp))

            // Body: Donut Ring on left + Category list on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 120dp Storage Ring Donut
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 11.dp.toPx()
                        val activeStrokeWidthPx = 13.dp.toPx()
                        val paddingPx = 8.dp.toPx()
                        val diameter = size.minDimension - paddingPx * 2
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)

                        // 1. Background Track
                        drawArc(
                            color = colors.surfaceHi,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx)
                        )

                        // If totalBytes <= 0, show background track only
                        if (totalBytes > 0) {
                            val nonZeroCats = listOf(
                                Triple(StorageCategory.DOCUMENTS, docBytes, CategoryViolet),
                                Triple(StorageCategory.MEDIA, mediaBytes, CategoryTeal),
                                Triple(StorageCategory.OTHER, otherBytes, CategoryAmber)
                            ).filter { it.second > 0L }

                            val gapDegrees = if (nonZeroCats.size > 1) 8f else 0f
                            val totalGap = nonZeroCats.size * gapDegrees
                            val availableDegrees = 360f - totalGap

                            var currentStartAngle = -90f

                            nonZeroCats.forEach { (cat, bytes, color) ->
                                val proportion = bytes.toFloat() / totalBytes.toFloat()
                                val targetSweep = (proportion * availableDegrees).coerceAtLeast(10f)

                                val animProgress = when (cat) {
                                    StorageCategory.DOCUMENTS -> docAnim.value
                                    StorageCategory.MEDIA -> mediaAnim.value
                                    StorageCategory.OTHER -> otherAnim.value
                                }
                                val sweep = targetSweep * animProgress

                                val isCatActive = selectedCategory == null || selectedCategory == cat
                                val isCatSelected = selectedCategory == cat
                                val alpha = if (selectedCategory == null) 1f else if (isCatSelected) 1f else 0.22f

                                if (sweep > 0f) {
                                    // Glow under-layer
                                    if (isCatActive) {
                                        drawArc(
                                            color = color.copy(alpha = if (isCatSelected) 0.5f else 0.28f),
                                            startAngle = currentStartAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(
                                                width = if (isCatSelected) activeStrokeWidthPx + 6.dp.toPx() else strokeWidthPx + 3.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }

                                    // Crisp main segment arc
                                    drawArc(
                                        color = color.copy(alpha = alpha),
                                        startAngle = currentStartAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(
                                            width = if (isCatSelected) activeStrokeWidthPx else strokeWidthPx,
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }

                                currentStartAngle += targetSweep + gapDegrees
                            }
                        }
                    }

                    // Center storage value & unit
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = centerVal,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            },
                            label = "ring_center_val"
                        ) { v ->
                            Text(
                                text = v,
                                style = MonoStatValueLarge.copy(color = colors.text, fontSize = 16.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = centerUnit,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            },
                            label = "ring_center_unit"
                        ) { u ->
                            Text(
                                text = u,
                                fontSize = 11.sp,
                                fontFamily = BodySansFont,
                                color = colors.textFaint,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Category List (Documents, Media, Other)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryRow(
                        category = StorageCategory.DOCUMENTS,
                        name = "Documents",
                        color = CategoryViolet,
                        bytes = docBytes,
                        isSelected = selectedCategory == StorageCategory.DOCUMENTS,
                        isAnySelected = selectedCategory != null,
                        onClick = {
                            selectedCategory = if (selectedCategory == StorageCategory.DOCUMENTS) null else StorageCategory.DOCUMENTS
                        }
                    )

                    CategoryRow(
                        category = StorageCategory.MEDIA,
                        name = "Media",
                        color = CategoryTeal,
                        bytes = mediaBytes,
                        isSelected = selectedCategory == StorageCategory.MEDIA,
                        isAnySelected = selectedCategory != null,
                        onClick = {
                            selectedCategory = if (selectedCategory == StorageCategory.MEDIA) null else StorageCategory.MEDIA
                        }
                    )

                    CategoryRow(
                        category = StorageCategory.OTHER,
                        name = "Other",
                        color = CategoryAmber,
                        bytes = otherBytes,
                        isSelected = selectedCategory == StorageCategory.OTHER,
                        isAnySelected = selectedCategory != null,
                        onClick = {
                            selectedCategory = if (selectedCategory == StorageCategory.OTHER) null else StorageCategory.OTHER
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: StorageCategory,
    name: String,
    color: Color,
    bytes: Long,
    isSelected: Boolean,
    isAnySelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val (v, u) = remember(bytes) { formatBytesDisplay(bytes) }
    val displaySize = "$v $u"

    val alpha by animateFloatAsState(
        targetValue = if (!isAnySelected || isSelected) 1f else 0.4f,
        animationSpec = tween(200),
        label = "cat_row_alpha"
    )

    val rowBg = if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Text(
                text = name,
                fontSize = 13.sp,
                fontFamily = BodySansFont,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.text.copy(alpha = alpha)
            )
        }

        Text(
            text = displaySize,
            fontSize = 12.5.sp,
            fontFamily = NumericMonoFont,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.textDim.copy(alpha = alpha)
        )
    }
}
