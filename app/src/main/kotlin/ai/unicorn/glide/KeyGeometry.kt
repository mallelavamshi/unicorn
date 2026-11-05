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

package ai.unicorn.glide

/**
 * Where the letter keys are, in pixels. The decoder works in "key units": the distance between neighboring keys in a
 * row is 1 across, and between rows is 1 down, so a sloppy swipe is equally sloppy on a narrow phone, a wide one and
 * in landscape.
 */
class KeyGeometry(centerX: FloatArray, centerY: FloatArray) {
    /** Pixels between the centers of neighboring keys in a row, and between rows. */
    val pitchX: Float
    val pitchY: Float
    /** Key centers in key units, by key (0 = a ... 25 = z); NaN for a key the layout doesn't have. */
    val x = FloatArray(GlideLexicon.KEYS)
    val y = FloatArray(GlideLexicon.KEYS)

    init {
        require(centerX.size == GlideLexicon.KEYS && centerY.size == GlideLexicon.KEYS)
        val q = 'q' - 'a'
        val p = 'p' - 'a'
        val a = 'a' - 'a'
        pitchX = if (!centerX[q].isNaN() && !centerX[p].isNaN()) (centerX[p] - centerX[q]) / 9f else 1f
        pitchY = if (!centerY[q].isNaN() && !centerY[a].isNaN()) (centerY[a] - centerY[q]) else pitchX
        require(pitchX > 0f && pitchY > 0f) { "Not a QWERTY layout" }
        for (k in 0 until GlideLexicon.KEYS) {
            x[k] = centerX[k] / pitchX
            y[k] = centerY[k] / pitchY
        }
    }

    fun has(key: Int) = !x[key].isNaN()

    /** Whether every letter is on the layout: glide typing only runs then. */
    val isComplete: Boolean get() = (0 until GlideLexicon.KEYS).all { has(it) }

    /** Identity for caches: two geometries with the same centers decode the same. */
    val signature: Long = run {
        var h = 1125899906842597L
        for (k in 0 until GlideLexicon.KEYS) {
            h = 31 * h + (centerX[k] * 16).toLong()
            h = 31 * h + (centerY[k] * 16).toLong()
        }
        h
    }
}
