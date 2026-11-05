/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.keyboard

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The screen the keys are laid out on, in pixels, with the engine's default height for the key rows. [reservedPx] is
 * what always sits with the keys: the toolbar, the suggestions and the navigation bar under them.
 */
data class KeyScreen(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val defaultKeysHeightPx: Int,
    val reservedPx: Int = 0,
) {
    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density
}

/**
 * How big the key area is and where it sits, as the person set it in Size and position. The letters never move
 * relative to each other: only the whole area is scaled, slid sideways or lifted off the bottom.
 */
data class KeyAreaSize(
    /** Height of the key rows, as a share of the engine's default. */
    val heightScale: Float = 1f,
    /** Width of the whole area (toolbar, suggestions and keys), as a share of the screen width. */
    val widthFraction: Float = 1f,
    /** Where a narrowed area sits: 0 against the left edge, 0.5 centered, 1 against the right edge. */
    val position: Float = 0.5f,
    /** Empty space kept under the keys, in dp. */
    val liftDp: Float = 0f,
) {
    val isFullWidth: Boolean get() = widthFraction >= FULL_WIDTH
    val isDefault: Boolean get() = this == DEFAULT

    /** Docked to one side and narrow enough to leave room for the side buttons. */
    val isOneHanded: Boolean get() = !isFullWidth && (position <= 0.01f || position >= 0.99f)

    /** The same size moved within the limits for this screen. */
    fun clampedTo(screen: KeyScreen): KeyAreaSize {
        val limits = Limits.of(screen)
        val height = if (heightScale.isNaN()) 1f else heightScale.coerceIn(limits.minHeightScale, limits.maxHeightScale)
        return KeyAreaSize(
            heightScale = height,
            widthFraction = if (widthFraction.isNaN()) 1f else widthFraction.coerceIn(limits.minWidthFraction, 1f),
            position = if (position.isNaN()) 0.5f else position.coerceIn(0f, 1f),
            liftDp = if (liftDp.isNaN()) 0f else liftDp.coerceIn(0f, limits.maxLiftDpWith(screen, height)),
        )
    }

    fun widthPx(screen: KeyScreen): Int = (screen.widthPx * clampedTo(screen).widthFraction).roundToInt()

    /** Pixels between the left edge of the screen and the area. */
    fun leftPx(screen: KeyScreen): Int {
        val free = screen.widthPx - widthPx(screen)
        return (free * clampedTo(screen).position).roundToInt().coerceIn(0, max(0, free))
    }

    fun rightGapPx(screen: KeyScreen): Int = screen.widthPx - widthPx(screen) - leftPx(screen)

    fun keysHeightPx(screen: KeyScreen): Int = (screen.defaultKeysHeightPx * clampedTo(screen).heightScale).roundToInt()

    fun liftPx(screen: KeyScreen): Int = (clampedTo(screen).liftDp * screen.density).roundToInt()

    /**
     * The area after dragging its left edge to [leftPx] (the right edge stays put). Used by the resize handles, which
     * work in screen pixels.
     */
    fun withLeftEdge(screen: KeyScreen, leftPx: Float): KeyAreaSize {
        val right = (leftPx(screen) + widthPx(screen)).toFloat()
        val minWidth = Limits.of(screen).minWidthFraction * screen.widthPx
        val left = leftPx.coerceIn(0f, right - minWidth)
        return withEdges(screen, left, right)
    }

    fun withRightEdge(screen: KeyScreen, rightPx: Float): KeyAreaSize {
        val left = leftPx(screen).toFloat()
        val minWidth = Limits.of(screen).minWidthFraction * screen.widthPx
        val right = rightPx.coerceIn(left + minWidth, screen.widthPx.toFloat())
        return withEdges(screen, left, right)
    }

    /** Slid sideways so the left edge is at [leftPx], keeping the width. */
    fun movedTo(screen: KeyScreen, leftPx: Float): KeyAreaSize {
        val width = widthPx(screen).toFloat()
        val left = leftPx.coerceIn(0f, screen.widthPx - width)
        return withEdges(screen, left, left + width)
    }

    private fun withEdges(screen: KeyScreen, left: Float, right: Float): KeyAreaSize {
        val width = (right - left).coerceAtMost(screen.widthPx.toFloat())
        val free = screen.widthPx - width
        val fraction = width / screen.widthPx
        return copy(
            widthFraction = if (fraction >= FULL_WIDTH) 1f else fraction,
            position = if (free < 1f || fraction >= FULL_WIDTH) 0.5f else (left / free).coerceIn(0f, 1f),
        ).clampedTo(screen)
    }

    /** "1.0,0.82,1.0,0.0": height, width, position, lift. */
    fun encode(): String = "$heightScale,$widthFraction,$position,$liftDp"

    /**
     * How far each setting may go on a screen. The engine's default size is always allowed; taller keys and lift only
     * use room that is left, so the whole input view (toolbar, suggestions, keys, lift and navigation bar) stays within
     * [MAX_INPUT_SHARE] of the screen height in both orientations.
     */
    data class Limits(
        val minHeightScale: Float,
        val maxHeightScale: Float,
        val minWidthFraction: Float,
        /** Lift allowed at the default height. */
        val maxLiftDp: Float,
    ) {
        /** Lift allowed with key rows [heightScale] times the default. */
        fun maxLiftDpWith(screen: KeyScreen, heightScale: Float): Float {
            val keys = screen.defaultKeysHeightPx * heightScale
            val room = (roomPx(screen) - keys) / screen.density
            return min(MAX_LIFT_DP, min(screen.heightDp * MAX_LIFT_SHARE, room)).coerceAtLeast(0f)
        }

        companion object {
            /** Pixels the key rows and the lift may share. */
            fun roomPx(screen: KeyScreen): Float = screen.heightPx * MAX_INPUT_SHARE - screen.reservedPx

            fun of(screen: KeyScreen): Limits {
                val default = screen.defaultKeysHeightPx.toFloat()
                // Never taller than half the screen or than the room left; never forced below the engine's default
                val maxHeight = if (default > 0f) {
                    max(1f, min(MAX_HEIGHT_SCALE, min(screen.heightPx * MAX_SCREEN_SHARE, roomPx(screen)) / default))
                } else {
                    1f
                }
                val limits = Limits(
                    minHeightScale = MIN_HEIGHT_SCALE,
                    maxHeightScale = maxHeight,
                    minWidthFraction = if (screen.widthDp > 0f) max(MIN_WIDTH_FRACTION, min(1f, MIN_LETTER_ROW_DP / screen.widthDp)) else 1f,
                    maxLiftDp = 0f,
                )
                return limits.copy(maxLiftDp = limits.maxLiftDpWith(screen, 1f))
            }
        }
    }

    companion object {
        val DEFAULT = KeyAreaSize()

        const val MIN_HEIGHT_SCALE = 0.75f
        const val MAX_HEIGHT_SCALE = 1.35f
        const val MAX_SCREEN_SHARE = 0.5f
        /** The whole input view, navigation bar included, never takes more of the screen height than this. */
        const val MAX_INPUT_SHARE = 0.85f
        /** Kept free for the navigation bar when it can't be measured yet. */
        const val NAV_BAR_RESERVE_DP = 48f
        const val MIN_WIDTH_FRACTION = 0.6f
        /** Ten letters of at least 29 dp each. */
        const val MIN_LETTER_ROW_DP = 290f
        const val MAX_LIFT_DP = 96f
        const val MAX_LIFT_SHARE = 0.15f
        /** One-handed mode: about Gboard's width, never narrower than the limit. */
        const val ONE_HANDED_WIDTH = 0.82f
        /** Within this of the full width counts as full width, so a drag doesn't leave a 1-pixel gutter. */
        const val FULL_WIDTH = 0.985f
        /** An edge tap reaches at most this far beside the keys, in letter keys. */
        const val EDGE_REACH_KEYS = 0.75f

        fun oneHanded(onRight: Boolean, current: KeyAreaSize = DEFAULT) =
            current.copy(widthFraction = ONE_HANDED_WIDTH, position = if (onRight) 1f else 0f)

        fun decode(text: String?): KeyAreaSize {
            val parts = text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return DEFAULT
            if (parts.size != 4 || parts.any { it.isNaN() || it.isInfinite() }) return DEFAULT
            return KeyAreaSize(parts[0], parts[1], parts[2], parts[3])
        }

        /**
         * How far a tap beside the keys still reaches the outer key, for the gap on one side ([gapPx]) and the width of
         * a letter key ([keyPitchPx]).
         */
        fun edgeReachPx(gapPx: Int, keyPitchPx: Int): Int = max(0, min(gapPx, (keyPitchPx * EDGE_REACH_KEYS).roundToInt()))

        /** A coordinate on the key rows (0 at their left edge), moved onto the outer key when it is within reach. */
        fun reachEdge(x: Int, rowsWidthPx: Int, leftReachPx: Int, rightReachPx: Int): Int = when {
            x < 0 && x >= -leftReachPx -> 0
            x >= rowsWidthPx && x < rowsWidthPx + rightReachPx -> rowsWidthPx - 1
            else -> x
        }
    }
}
