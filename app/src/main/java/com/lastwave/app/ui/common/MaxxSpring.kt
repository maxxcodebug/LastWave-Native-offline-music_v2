package com.lastwave.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Maxx spring language. One place to tune the whole app's bounce.
 *
 *  - [bouncy]  : layout / scale / size (nav pill, press, enter). Visible overshoot.
 *  - [snappy]  : quick toggles and small controls.
 *  - [soft]    : large surfaces (hero cards, sheets). Slow settle, tiny overshoot.
 *  - [settle]  : NO overshoot. Use for colour and alpha, since overshoot there
 *                produces out-of-range values.
 */
object MaxxSpring {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = 420f)
    fun <T> snappy() = spring<T>(dampingRatio = 0.68f, stiffness = 800f)
    fun <T> soft() = spring<T>(dampingRatio = 0.78f, stiffness = 220f)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)
}

/** Squish on press, spring back on release. */
@Composable
fun Modifier.maxxPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaxxSpring.bouncy(),
        label = "maxxPress",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Clickable with the Maxx press squish and no ripple (the squish is the feedback). */
@Composable
fun Modifier.maxxClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .maxxPress(source, pressedScale)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Staggered spring-in for list items: rises from below, scales up, fades in.
 * Only the first [maxStagger] indices are delayed so long lists never feel slow.
 */
fun Modifier.maxxAppear(index: Int, maxStagger: Int = 10): Modifier = composed {
    // Rows far below the first screen skip the intro so scrolling never replays it.
    val progress = remember { Animatable(if (index > maxStagger + 6) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(maxStagger) * 38).toLong())
        progress.animateTo(1f, MaxxSpring.bouncy())
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 56f
        val s = 0.9f + 0.1f * p
        scaleX = s
        scaleY = s
    }
}
