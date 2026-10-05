package com.example.spendsync.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.compositionLocalOf

/** The kinds of motion the user can switch off one by one (Settings -> Animations). */
enum class MotionKind { Counts, Entrance, Transitions, Typing, Skeleton, Press, Charts }

/**
 * Which animations are on. [all] is the master switch: off means nothing moves. It is also forced off when Android's
 * own "remove animations" accessibility setting is on. Stored on this phone only (it is about this device's comfort).
 */
data class MotionPrefs(
    val all: Boolean = true,
    val counts: Boolean = true,
    val entrance: Boolean = true,
    val transitions: Boolean = true,
    val typing: Boolean = true,
    val skeleton: Boolean = true,
    val press: Boolean = true,
    val charts: Boolean = true,
) {
    fun enabled(kind: MotionKind): Boolean = all && when (kind) {
        MotionKind.Counts -> counts
        MotionKind.Entrance -> entrance
        MotionKind.Transitions -> transitions
        MotionKind.Typing -> typing
        MotionKind.Skeleton -> skeleton
        MotionKind.Press -> press
        MotionKind.Charts -> charts
    }
}

/** Read with `LocalMotion.current.enabled(MotionKind.X)`; provided once at the top of the app. */
val LocalMotion = compositionLocalOf { MotionPrefs() }

/**
 * Wraps an `AnimatedContent` transition so it becomes an instant swap when screen transitions are off:
 * `transitionSpec = motionSpec(LocalMotion.current.enabled(MotionKind.Transitions)) { ... }`.
 */
fun <S> motionSpec(on: Boolean, block: AnimatedContentTransitionScope<S>.() -> ContentTransform): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    { if (on) block() else ContentTransform(EnterTransition.None, ExitTransition.None) }
