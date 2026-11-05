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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyAreaSizeTest {
    // A 412 x 915 dp phone at 2.625x (1080 x 2402 px), default key rows 205.6 dp
    private val phone = KeyScreen(1080, 2402, 2.625f, 540)
    private val landscape = KeyScreen(2402, 1080, 2.625f, 486)

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.002f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun defaultIsTheEngineLayout() {
        val size = KeyAreaSize.DEFAULT
        assertEquals(1080, size.widthPx(phone))
        assertEquals(0, size.leftPx(phone))
        assertEquals(0, size.rightGapPx(phone))
        assertEquals(540, size.keysHeightPx(phone))
        assertEquals(0, size.liftPx(phone))
        assertTrue(size.isFullWidth)
        assertTrue(size.isDefault)
    }

    @Test
    fun oneHandedDocksToEitherSide() {
        val right = KeyAreaSize.oneHanded(onRight = true)
        assertEquals(886, right.widthPx(phone))
        assertEquals(194, right.leftPx(phone))
        assertEquals(0, right.rightGapPx(phone))
        assertTrue(right.isOneHanded)
        val left = KeyAreaSize.oneHanded(onRight = false)
        assertEquals(0, left.leftPx(phone))
        assertEquals(194, left.rightGapPx(phone))
    }

    @Test
    fun letterKeysNeverGetNarrowerThan29Dp() {
        val tiny = KeyAreaSize(widthFraction = 0.3f).clampedTo(phone)
        // 290 dp of 411.4 dp
        near(0.7049f, tiny.widthFraction, 0.001f)
        val keyDp = tiny.widthPx(phone) / phone.density / 10
        assertTrue(keyDp >= 28.9f, "key was $keyDp dp")
        // In landscape the 60% floor is the limit
        near(0.6f, KeyAreaSize(widthFraction = 0.3f).clampedTo(landscape).widthFraction)
    }

    @Test
    fun heightStaysWithinLimits() {
        near(0.75f, KeyAreaSize(heightScale = 0.2f).clampedTo(phone).heightScale)
        near(1.35f, KeyAreaSize(heightScale = 3f).clampedTo(phone).heightScale)
        // Landscape: never taller than half of the 411 dp screen height
        val tall = KeyAreaSize(heightScale = 3f).clampedTo(landscape)
        assertTrue(tall.keysHeightPx(landscape) <= landscape.heightPx / 2 + 1)
    }

    // The same landscape screen with the toolbar (44 dp), suggestions (36 dp) and navigation bar (48 dp) reserved
    private val landscapeWithBars = KeyScreen(2400, 1080, 2.625f, 486, ((44 + 36 + 48) * 2.625f).toInt())

    private fun inputViewPx(size: KeyAreaSize, screen: KeyScreen) =
        size.keysHeightPx(screen) + size.liftPx(screen) + screen.reservedPx

    @Test
    fun theWholeInputViewFitsALandscapeScreen1080PxTall() {
        val budget = (1080 * KeyAreaSize.MAX_INPUT_SHARE).toInt() + 1
        for (size in listOf(
            KeyAreaSize(heightScale = 3f, liftDp = 500f),
            KeyAreaSize(heightScale = 1f, liftDp = 500f),
            KeyAreaSize(heightScale = 0.75f, liftDp = 500f),
            KeyAreaSize(heightScale = 1.35f, liftDp = 96f, widthFraction = 0.82f, position = 1f),
        )) {
            val total = inputViewPx(size, landscapeWithBars)
            assertTrue(total <= budget, "$size takes $total px of 1080")
        }
        // Tallest keys leave only a little lift
        val tallest = KeyAreaSize(heightScale = 3f, liftDp = 500f).clampedTo(landscapeWithBars)
        assertEquals(540, tallest.keysHeightPx(landscapeWithBars))
        near(16f, tallest.liftDp, 0.1f)
    }

    @Test
    fun theDefaultSizeIsNeverChanged() {
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.DEFAULT.clampedTo(landscapeWithBars))
        assertEquals(486, KeyAreaSize.DEFAULT.keysHeightPx(landscapeWithBars))
        // Even where the default already fills the room, it keeps its height; taller and lift are refused
        val cramped = KeyScreen(1600, 720, 2f, 324, 300)
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.DEFAULT.clampedTo(cramped))
        val asked = KeyAreaSize(heightScale = 1.3f, liftDp = 40f).clampedTo(cramped)
        near(1f, asked.heightScale)
        near(0f, asked.liftDp)
        assertEquals(KeyAreaSize.MIN_HEIGHT_SCALE, KeyAreaSize.Limits.of(cramped).minHeightScale)
    }

    @Test
    fun portraitStillGetsTheFullRange() {
        val phoneWithBars = KeyScreen(1080, 2402, 2.625f, 540, ((44 + 40 + 48) * 2.625f).toInt())
        val largest = KeyAreaSize(heightScale = 3f, liftDp = 500f).clampedTo(phoneWithBars)
        near(1.35f, largest.heightScale)
        near(96f, largest.liftDp)
    }

    @Test
    fun liftIsCapped() {
        near(96f, KeyAreaSize(liftDp = 500f).clampedTo(phone).liftDp)
        // 15% of 411 dp in landscape
        near(61.7f, KeyAreaSize(liftDp = 500f).clampedTo(landscape).liftDp, 0.1f)
        near(0f, KeyAreaSize(liftDp = -20f).clampedTo(phone).liftDp)
        assertEquals(252, KeyAreaSize(liftDp = 96f).liftPx(phone))
    }

    @Test
    fun draggingTheLeftEdgeKeepsTheRightEdge() {
        val size = KeyAreaSize.DEFAULT.withLeftEdge(phone, 200f)
        assertEquals(200, size.leftPx(phone))
        assertEquals(1080, size.leftPx(phone) + size.widthPx(phone))
        // Past the minimum width, it stops
        val stopped = KeyAreaSize.DEFAULT.withLeftEdge(phone, 1000f)
        assertEquals(1080, stopped.leftPx(phone) + stopped.widthPx(phone))
        assertTrue(stopped.widthPx(phone) >= (290 * phone.density).toInt() - 1)
    }

    @Test
    fun draggingTheRightEdgeKeepsTheLeftEdge() {
        val docked = KeyAreaSize.oneHanded(onRight = false)
        val size = docked.withRightEdge(phone, 900f)
        assertEquals(0, size.leftPx(phone))
        assertEquals(900, size.widthPx(phone))
        // Not narrower than the minimum
        assertEquals(761, docked.withRightEdge(phone, 300f).widthPx(phone))
        // Back out to the edge snaps to the full width
        val full = size.withRightEdge(phone, 1079f)
        assertTrue(full.isFullWidth)
        assertEquals(1080, full.widthPx(phone))
    }

    @Test
    fun movingSlidesWithinTheScreen() {
        val size = KeyAreaSize.oneHanded(onRight = false)
        val middle = size.movedTo(phone, 97f)
        near(0.5f, middle.position, 0.01f)
        assertEquals(size.widthPx(phone), middle.widthPx(phone))
        val far = size.movedTo(phone, 5000f)
        assertEquals(0, far.rightGapPx(phone))
        val back = size.movedTo(phone, -300f)
        assertEquals(0, back.leftPx(phone))
    }

    @Test
    fun encodesAndDecodes() {
        val size = KeyAreaSize(1.1f, 0.82f, 1f, 24f)
        assertEquals(size, KeyAreaSize.decode(size.encode()))
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.decode(null))
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.decode("garbage"))
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.decode("1,2,NaN,4"))
        assertEquals(KeyAreaSize.DEFAULT, KeyAreaSize.decode("1,2,3"))
    }

    @Test
    fun edgeTapsReachTheOuterKeyOnlyNearby() {
        // One-handed on the right: 194 px empty on the left, keys 88 px wide
        val reach = KeyAreaSize.edgeReachPx(194, 88)
        assertEquals(66, reach)
        assertEquals(0, KeyAreaSize.reachEdge(-1, 886, reach, 0))
        assertEquals(0, KeyAreaSize.reachEdge(-66, 886, reach, 0))
        assertEquals(-67, KeyAreaSize.reachEdge(-67, 886, reach, 0))
        assertEquals(886, KeyAreaSize.reachEdge(886, 886, reach, 0), "no reach on the right")
        assertEquals(400, KeyAreaSize.reachEdge(400, 886, reach, 0))
        // A narrow gap gives all of it
        assertEquals(20, KeyAreaSize.edgeReachPx(20, 88))
        assertEquals(0, KeyAreaSize.edgeReachPx(0, 88))
        // Centered and narrowed: both sides reach
        assertEquals(885, KeyAreaSize.reachEdge(900, 886, 20, 20))
        assertEquals(906, KeyAreaSize.reachEdge(906, 886, 20, 20))
    }
}
