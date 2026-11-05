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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapAimModelTest {
    private val e = TapAimModel.letterOf('e'.code)
    private val r = TapAimModel.letterOf('r'.code)
    private val d = TapAimModel.letterOf('d'.code)

    // Keys 100 px wide, rows 120 px tall. E and R on the top row, D under them (half a key in)
    private fun key(letter: Int, left: Float, top: Float) =
        TapAimModel.Candidate(letter, left, top, left + 100f, top + 120f, left + 50f, top + 60f, 100f, 120f)

    private val keys = listOf(key(e, 200f, 0f), key(r, 300f, 0f), key(d, 250f, 120f))

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun withNothingLearnedTheTouchAreasAreUnchanged() {
        val model = TapAimModel()
        assertEquals(e, model.choose(299f, 60f, keys))
        assertEquals(r, model.choose(300f, 60f, keys))
        assertEquals(e, model.choose(250f, 119f, keys))
        assertEquals(d, model.choose(250f, 120f, keys))
        assertEquals(-1, model.choose(50f, 60f, keys))
        assertFalse(model.isLearning)
    }

    @Test
    fun nothingMovesBeforeFortyTaps() {
        val model = TapAimModel()
        repeat(39) { model.add(e, 0.2f, 0f) }
        assertEquals(0f, model.offsetX(e))
        assertEquals(r, model.choose(305f, 60f, keys))
        model.add(e, 0.2f, 0f)
        assertTrue(model.isLearning)
        // Two thirds of the average at 40 taps
        near(0.1333f, model.offsetX(e))
    }

    @Test
    fun aPersonWhoLandsRightOfEStillGetsEOnTheBorder() {
        val model = TapAimModel()
        repeat(40) { model.add(e, 0.2f, 0f) }
        // Just inside R's plain area, but nearer where this person lands for E
        assertEquals(e, model.choose(305f, 60f, keys))
        // Well inside R it stays R
        assertEquals(r, model.choose(330f, 60f, keys))
    }

    @Test
    fun aPersonWhoLandsLowStillGetsTheTopRow() {
        val model = TapAimModel()
        repeat(200) { model.add(e, 0f, 0.3f) }
        assertEquals(e, model.choose(250f, 125f, keys))
        assertEquals(d, model.choose(250f, 160f, keys))
    }

    @Test
    fun theMoveIsCappedAtAQuarterKey() {
        val model = TapAimModel()
        repeat(1000) { model.add(e, 0.55f, -0.55f) }
        near(TapAimModel.CAP, model.offsetX(e), 0.0001f)
        near(-TapAimModel.CAP, model.offsetY(e), 0.0001f)
        // So a tap more than a quarter key into R is always R
        assertEquals(r, model.choose(326f, 60f, keys))
    }

    @Test
    fun wildTapsAreIgnored() {
        val model = TapAimModel()
        model.add(e, 0.9f, 0f)
        model.add(e, 0f, -0.7f)
        model.add(e, Float.NaN, 0f)
        model.add(-1, 0f, 0f)
        model.add(26, 0f, 0f)
        assertEquals(0, model.samples(e))
    }

    @Test
    fun followsANewGrip() {
        val model = TapAimModel()
        repeat(300) { model.add(e, 0.2f, 0f) }
        repeat(900) { model.add(e, -0.2f, 0f) }
        assertTrue(model.offsetX(e) < -0.17f, "was ${model.offsetX(e)}")
    }

    @Test
    fun encodesAndDecodes() {
        val model = TapAimModel()
        repeat(50) { model.add(r, -0.1f, 0.25f) }
        val copy = TapAimModel.decode(model.encode())
        assertEquals(50, copy.samples(r))
        near(model.offsetX(r), copy.offsetX(r), 0.0001f)
        near(model.offsetY(r), copy.offsetY(r), 0.0001f)
        assertEquals(0, TapAimModel.decode(null).samples(r))
        assertEquals(0, TapAimModel.decode("1:2:3").samples(r))
        assertEquals(0, TapAimModel.decode(model.encode().replace("50:", "x:")).samples(r))
    }

    @Test
    fun lettersOnly() {
        assertEquals(0, TapAimModel.letterOf('a'.code))
        assertEquals(25, TapAimModel.letterOf('Z'.code))
        assertEquals(-1, TapAimModel.letterOf('1'.code))
        assertEquals(-1, TapAimModel.letterOf(' '.code))
        assertEquals(-1, TapAimModel.letterOf(-5))
    }
}
