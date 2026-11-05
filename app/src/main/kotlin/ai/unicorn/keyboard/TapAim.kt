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

import ai.unicorn.Unicorn
import android.content.Context
import android.content.SharedPreferences
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.MoreKeysKeyboard
import com.android.inputmethod.latin.common.Constants
import kotlin.math.abs

/**
 * Where one person lands on each letter, in key pitches from the key's centre (right and down are positive). Kept
 * on the phone only. Pure math, so it can be unit tested.
 */
class TapAimModel {
    private val count = IntArray(LETTERS)
    private val meanX = FloatArray(LETTERS)
    private val meanY = FloatArray(LETTERS)

    fun samples(letter: Int): Int = count[letter]

    /** Adds one clean tap on [letter] (0 = a), [dx] and [dy] in key pitches. Wild taps are ignored. */
    fun add(letter: Int, dx: Float, dy: Float) {
        if (letter !in 0 until LETTERS || dx.isNaN() || dy.isNaN() || abs(dx) > MAX_SAMPLE || abs(dy) > MAX_SAMPLE) return
        val n = count[letter]
        // A plain average at first, then a moving one so a new grip or phone case is picked up
        val weight = 1f / (minOf(n, WINDOW - 1) + 1)
        meanX[letter] += (dx - meanX[letter]) * weight
        meanY[letter] += (dy - meanY[letter]) * weight
        if (n < Int.MAX_VALUE) count[letter] = n + 1
    }

    /** How far the touch area of [letter] is moved, in key pitches: nothing until it has enough taps, then capped. */
    fun offsetX(letter: Int): Float = applied(letter, meanX)
    fun offsetY(letter: Int): Float = applied(letter, meanY)

    private fun applied(letter: Int, mean: FloatArray): Float {
        val n = count[letter]
        if (n < MIN_SAMPLES) return 0f
        return (mean[letter] * n / (n + SHRINK)).coerceIn(-CAP, CAP)
    }

    val isLearning: Boolean get() = (0 until LETTERS).any { count[it] >= MIN_SAMPLES }

    fun clear() {
        count.fill(0)
        meanX.fill(0f)
        meanY.fill(0f)
    }

    /** "count:x:y" for each letter, separated by spaces. */
    fun encode(): String = (0 until LETTERS).joinToString(" ") { "${count[it]}:${meanX[it]}:${meanY[it]}" }

    /** A key the tap could go to: its touch area and centre in pixels, and the size of one key. */
    class Candidate(
        @JvmField val letter: Int,
        @JvmField val left: Float,
        @JvmField val top: Float,
        @JvmField val right: Float,
        @JvmField val bottom: Float,
        @JvmField val centreX: Float,
        @JvmField val centreY: Float,
        @JvmField val pitchX: Float,
        @JvmField val pitchY: Float,
    )

    /**
     * Which letter a tap at [x], [y] is for: each candidate's touch area moves by its learned offset, and among the
     * areas that contain the tap the one whose moved centre is nearest wins. With nothing learned this is exactly the
     * plain touch areas. Returns -1 when no moved area contains the tap (the caller keeps the plain answer).
     */
    fun choose(x: Float, y: Float, candidates: List<Candidate>): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (c in candidates) {
            val ox = offsetX(c.letter) * c.pitchX
            val oy = offsetY(c.letter) * c.pitchY
            if (x < c.left + ox || x >= c.right + ox || y < c.top + oy || y >= c.bottom + oy) continue
            val dx = (x - (c.centreX + ox)) / c.pitchX
            val dy = (y - (c.centreY + oy)) / c.pitchY
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = c.letter
            }
        }
        return best
    }

    companion object {
        const val LETTERS = 26
        /** Taps on a letter before its touch area moves at all. */
        const val MIN_SAMPLES = 40
        /** Pulls a young average toward the key's centre: at 40 taps two thirds of the average is used. */
        const val SHRINK = 20f
        /** The furthest a touch area moves, in key pitches. */
        const val CAP = 0.25f
        /** Taps further than this from the centre aren't aiming at the key. */
        const val MAX_SAMPLE = 0.6f
        /** About how many recent taps the average follows. */
        const val WINDOW = 300

        fun decode(text: String?): TapAimModel {
            val model = TapAimModel()
            val parts = text?.split(' ') ?: return model
            if (parts.size != LETTERS) return model
            for ((i, part) in parts.withIndex()) {
                val fields = part.split(':')
                if (fields.size != 3) return TapAimModel()
                val n = fields[0].toIntOrNull() ?: return TapAimModel()
                val x = fields[1].toFloatOrNull() ?: return TapAimModel()
                val y = fields[2].toFloatOrNull() ?: return TapAimModel()
                if (n < 0 || x.isNaN() || y.isNaN() || abs(x) > MAX_SAMPLE || abs(y) > MAX_SAMPLE) return TapAimModel()
                model.count[i] = n
                model.meanX[i] = x
                model.meanY[i] = y
            }
            return model
        }

        /** 0 for a or A ... 25 for z or Z; -1 for anything else. */
        fun letterOf(code: Int): Int = when (code) {
            in 'a'.code..'z'.code -> code - 'a'.code
            in 'A'.code..'Z'.code -> code - 'A'.code
            else -> -1
        }
    }
}

/**
 * Learn my aim: learns where this person lands on each letter from words they type without fixing anything, and
 * moves each letter's touch area a little toward it. Only taps that land right between two touching letters can change.
 * Glide typing reads the keys' real positions and is not affected.
 */
object TapAim {
    private const val PREFS = "unicorn_tap_aim"
    private const val SAVE_EVERY_WORDS = 10
    private const val MAX_PENDING = 48

    private var prefs: SharedPreferences? = null
    private val models = arrayOfNulls<TapAimModel>(2)
    @Volatile private var landscape = false
    @Volatile private var enabled = KeyArea.ENABLED && KeyArea.LEARN_AIM_DEFAULT

    /** Taps of the word being typed: the code and where it landed, in key pitches (NaN for keys that aren't letters). */
    private val pendingCodes = IntArray(MAX_PENDING)
    private val pendingX = FloatArray(MAX_PENDING)
    private val pendingY = FloatArray(MAX_PENDING)
    private var pending = 0
    private var unsavedWords = 0

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    internal fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) pending = 0
    }

    internal fun setLandscape(isLandscape: Boolean) {
        if (landscape != isLandscape) pending = 0
        landscape = isLandscape
    }

    private fun model(): TapAimModel {
        val i = if (landscape) 1 else 0
        return models[i] ?: TapAimModel.decode(prefs?.getString(key(landscape), null)).also { models[i] = it }
    }

    private fun key(isLandscape: Boolean) = if (isLandscape) "landscape" else "portrait"

    /** Forgets how this person taps, in both orientations. */
    fun forget(context: Context) {
        init(context)
        models[0] = TapAimModel()
        models[1] = TapAimModel()
        pending = 0
        prefs?.edit()?.clear()?.apply()
    }

    /** How many letters have learned enough to move, for the settings screen. */
    fun learnedLetters(context: Context, isLandscape: Boolean): Int {
        init(context)
        val m = TapAimModel.decode(prefs?.getString(key(isLandscape), null))
        return (0 until TapAimModel.LETTERS).count { m.samples(it) >= TapAimModel.MIN_SAMPLES }
    }

    /** The engine's hit test picked [primary] for a tap at [x], [y] (key coordinates); returns the key it should be. */
    @JvmStatic
    fun adjust(keyboard: Keyboard?, primary: Key?, x: Int, y: Int): Key? {
        if (!enabled || keyboard == null || primary == null || keyboard is MoreKeysKeyboard) return primary
        if (TapAimModel.letterOf(primary.code) < 0) return primary
        try {
            val model = model()
            if (!model.isLearning) return primary
            val pitchX = keyboard.mMostCommonKeyWidth.toFloat()
            val pitchY = keyboard.mMostCommonKeyHeight.toFloat()
            if (pitchX <= 0f || pitchY <= 0f) return primary
            val keys = keyboard.getNearestKeys(x, y)
            val candidates = ArrayList<TapAimModel.Candidate>(keys.size)
            for (key in keys) {
                val letter = TapAimModel.letterOf(key.code)
                if (letter < 0) continue
                val box = key.hitBox
                candidates.add(TapAimModel.Candidate(
                    letter, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(),
                    key.x + key.width / 2f, key.y + key.height / 2f, pitchX, pitchY,
                ))
            }
            val chosen = model.choose(x.toFloat(), y.toFloat(), candidates)
            if (chosen < 0) return primary
            return keys.firstOrNull { TapAimModel.letterOf(it.code) == chosen } ?: primary
        } catch (e: Exception) {
            Unicorn.noteFailure("TapAim.adjust", e)
            return primary
        }
    }

    /** A key was typed by touch at [keyX], [keyY] (key coordinates). Deletes take back the last tap. */
    @JvmStatic
    fun onTap(keyboard: Keyboard?, code: Int, keyX: Int, keyY: Int) {
        if (!enabled || keyboard == null) return
        if (code == Constants.CODE_DELETE) {
            if (pending > 0) pending--
            return
        }
        val letter = TapAimModel.letterOf(code)
        if (letter < 0 && code != '\''.code && code != '-'.code) return
        if (pending == MAX_PENDING || !Constants.isValidCoordinate(keyX) || !Constants.isValidCoordinate(keyY)) {
            pending = 0
            return
        }
        var dx = Float.NaN
        var dy = Float.NaN
        val key = if (letter >= 0) keyboard.getKey(code) else null
        // Taps that an edge tap moved onto the outer key say nothing about aim
        if (key != null && keyX > 0 && keyX < keyboard.mOccupiedWidth - 1) {
            dx = (keyX - (key.x + key.width / 2f)) / keyboard.mMostCommonKeyWidth
            dy = (keyY - (key.y + key.height / 2f)) / keyboard.mMostCommonKeyHeight
        }
        pendingCodes[pending] = code
        pendingX[pending] = dx
        pendingY[pending] = dy
        pending++
    }

    /** A word went into the app. If it is exactly what was tapped, nothing was corrected, so the taps were on target. */
    fun onWordCommitted(word: String) {
        val count = pending
        pending = 0
        if (!enabled || count == 0 || count != word.length) return
        for (i in 0 until count) {
            if (Character.toLowerCase(pendingCodes[i]) != Character.toLowerCase(word[i].code)) return
        }
        val model = model()
        for (i in 0 until count) model.add(TapAimModel.letterOf(pendingCodes[i]), pendingX[i], pendingY[i])
        if (++unsavedWords >= SAVE_EVERY_WORDS) save()
    }

    /** Starting a new field: half-typed words from before don't count. */
    fun onStartInputView() {
        pending = 0
    }

    fun save() {
        unsavedWords = 0
        val editor = prefs?.edit() ?: return
        models[0]?.let { editor.putString(key(false), it.encode()) }
        models[1]?.let { editor.putString(key(true), it.encode()) }
        editor.apply()
    }
}
