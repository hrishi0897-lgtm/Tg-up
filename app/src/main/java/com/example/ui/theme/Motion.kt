package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Composition local providing system-level reduced-motion preference.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Reads the system accessibility settings to determine whether animations should be shortened or skipped.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val durationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transitionScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            durationScale == 0f || transitionScale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Common motion curves and spring physics.
 */
object MotionSpecs {
    // Shared axis screen curve
    val SharedAxisEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // Bouncy tactile spring for interactive controls (FAB, primary buttons)
    val PlayfulSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Gentle spring for structural motion (tab slider, progress bars, dialog scale)
    val GentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    val ListPlacementSpring = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = 0.8f,
        stiffness = 350f
    )

    val ListFadeIn = fadeIn(animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing))
    val ListFadeOut = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing))
}

/**
 * Tactile spring-scale modifier on button press.
 */
@Composable
fun Modifier.pressScale(
    pressedScale: Float = 0.94f,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else MotionSpecs.PlayfulSpring,
        label = "press_scale_anim"
    )

    return this.graphicsLayer {
        scaleX = scaleState.value
        scaleY = scaleState.value
    }
}

/**
 * Clickable with tactile bouncy spring feedback.
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(pressedScale = pressedScale, interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * A smooth animated dialog wrapper that scales and fades in from center
 * with a decelerate curve and fades the scrim backdrop in alongside it.
 */
@Composable
fun VaultAnimatedDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        // Scrim background with smooth fade-in
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = if (reduceMotion) fadeIn(tween(100)) else (
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 380f
                        ),
                        initialScale = 0.85f
                    ) + fadeIn(tween(durationMillis = 200, easing = LinearOutSlowInEasing))
                ),
                exit = if (reduceMotion) fadeOut(tween(100)) else (
                    scaleOut(
                        animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                        targetScale = 0.88f
                    ) + fadeOut(tween(durationMillis = 150))
                )
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* prevent backdrop dismissal */ }
                        )
                ) {
                    content()
                }
            }
        }
    }
}
