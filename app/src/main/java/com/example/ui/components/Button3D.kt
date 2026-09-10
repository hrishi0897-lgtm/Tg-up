package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.TealFabGradient
import com.example.ui.theme.VioletButtonGradient

/**
 * Custom 3D "pressed" button component matching the HTML/CSS redesign mockup:
 * Has a colored bottom "edge" shadow it visually sits on, which compresses on
 * press (moving the top face downward) and springs back on release.
 */
@Composable
fun Button3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = LocalTeleVaultColors.current.surfaceHi,
    edgeColor: Color = LocalTeleVaultColors.current.btnEdge,
    borderColor: Color = LocalTeleVaultColors.current.line,
    backgroundBrush: Brush? = null,
    pressDepth: Dp = 3.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled && !reduceMotion) pressDepth else 0.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "btn3d_press_offset"
    )

    Box(
        modifier = modifier
            .padding(bottom = pressDepth)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        // Bottom 3D Edge Layer (shows below face when unpressed, hidden when pressed)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = pressDepth)
                .clip(shape)
                .background(edgeColor)
                .border(1.dp, borderColor, shape)
        )

        // Top Face Layer (compresses down onto the edge on press)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = currentOffset)
                .clip(shape)
                .then(
                    if (backgroundBrush != null) Modifier.background(backgroundBrush)
                    else Modifier.background(backgroundColor)
                )
                .border(1.dp, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            // Subtle top gloss highlight overlay (mimics CSS ::after linear-gradient(180deg, rgba(255,255,255,0.16), transparent 45%))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.16f),
                            0.45f to Color.Transparent
                        )
                    )
            )
            content()
        }
    }
}

/**
 * 38dp x 38dp Icon Button with 3D pressed edge shadow (Header buttons: Theme toggle, Sync, Settings)
 */
@Composable
fun IconButton3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 38.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = LocalTeleVaultColors.current.surfaceHi,
    edgeColor: Color = LocalTeleVaultColors.current.btnEdge,
    borderColor: Color = LocalTeleVaultColors.current.line,
    backgroundBrush: Brush? = null,
    pressDepth: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Button3D(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = shape,
        backgroundColor = backgroundColor,
        edgeColor = edgeColor,
        borderColor = borderColor,
        backgroundBrush = backgroundBrush,
        pressDepth = pressDepth,
        content = content
    )
}

/**
 * 44dp x 44dp Square Button with 3D pressed edge shadow (Search bar action buttons: Sort, View mode)
 */
@Composable
fun SquareButton3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = LocalTeleVaultColors.current.surfaceHi,
    edgeColor: Color = LocalTeleVaultColors.current.btnEdge,
    borderColor: Color = LocalTeleVaultColors.current.line,
    pressDepth: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Button3D(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = shape,
        backgroundColor = backgroundColor,
        edgeColor = edgeColor,
        borderColor = borderColor,
        pressDepth = pressDepth,
        content = content
    )
}

/**
 * 56dp x 56dp FAB with 3D pressed edge shadow (#17806B edge, Teal gradient face, 5dp press depth)
 */
@Composable
fun Fab3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(18.dp),
    pressDepth: Dp = 5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Button3D(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = shape,
        backgroundBrush = TealFabGradient,
        edgeColor = Color(0xFF17806B),
        borderColor = Color(0xFF17806B).copy(alpha = 0.5f),
        pressDepth = pressDepth,
        content = content
    )
}

/**
 * Primary Pill/Button with 3D pressed edge shadow (#3B32A8 edge, Violet gradient face, 5dp press depth)
 */
@Composable
fun UploadButton3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    pressDepth: Dp = 5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isPressed = remember { MutableInteractionSource() }
    val pressedState by isPressed.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current

    val currentOffset by animateDpAsState(
        targetValue = if (pressedState && enabled && !reduceMotion) pressDepth else 0.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "upload_btn3d_press_offset"
    )

    Box(
        modifier = modifier
            .padding(bottom = pressDepth)
            .clickable(
                interactionSource = isPressed,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        // Bottom Edge
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = pressDepth)
                .clip(shape)
                .background(Color(0xFF3B32A8))
                .border(1.dp, Color(0xFF3B32A8).copy(alpha = 0.6f), shape)
        )

        // Top Face
        Box(
            modifier = Modifier
                .offset(y = currentOffset)
                .clip(shape)
                .background(VioletButtonGradient)
                .border(1.dp, Color(0x608C7CFF), shape)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
