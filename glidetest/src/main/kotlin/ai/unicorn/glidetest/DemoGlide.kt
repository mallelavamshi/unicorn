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

package ai.unicorn.glidetest

import ai.unicorn.glide.synth.SwipeSynth
import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.random.Random

/**
 * Glides words for the demo video (docs/plan/demo-video.md) into whatever field has Unicorn's keys open, usually
 * StageActivity's. Unlike the gate it opens nothing and checks nothing: the shot script opens the scene first, with
 * `setprop log.tag.UnicornGlide DEBUG` set so the keys log where they are.
 *
 *     am instrument -w -e mode demo -e words 'see you at noon' ai.unicorn.glidetest/.GlideInstrumentation
 *     am instrument -w -e mode demo -e type 'plan a cozy weekend' -e intervalMs 110 ai.unicorn.glidetest/.GlideInstrumentation
 *
 * The finger is a little slower than a typical one and close to the key centers, so the trail reads on video.
 * Typing taps the letter keys (and space, comma and period; a capital taps shift first) at an even pace.
 */
class DemoGlide(instrumentation: Instrumentation) {
    private val ui = instrumentation.uiAutomation
    private val pxPerMm = instrumentation.targetContext.resources.displayMetrics.xdpi / 25.4f
    private val finger = SwipeSynth.Noise("demo", 0.5f, 0.7f, 1.5f, 0f, 0f, 2.5f, 0.05f, 0f, 0.72f, 0.8f)

    /** Glides each word, pausing [pauseMs] between them; returns false when the keys' positions aren't logged. */
    fun run(words: List<String>, pauseMs: Long, seed: Int): Boolean {
        val keys = keys() ?: return false
        val random = Random(seed)
        for (word in words) {
            val swipe = SwipeSynth.swipe(word.lowercase(), keys, finger, random)
            drag(swipe.x, swipe.y, swipe.t)
            SystemClock.sleep(pauseMs)
        }
        return true
    }

    /** Taps out [text] on the keys, one key every [intervalMs]; returns false when the keys' positions aren't logged. */
    fun type(text: String, intervalMs: Long): Boolean {
        val keys = keyMap() ?: return false
        for (c in text) {
            val name = when (c) {
                ' ' -> "space"
                ',' -> "comma"
                '.' -> "period"
                else -> c.lowercaseChar().toString()
            }
            val (x, y) = keys[name] ?: continue
            if (c.isUpperCase()) keys["shift"]?.let { (sx, sy) -> tap(sx, sy); SystemClock.sleep(intervalMs) }
            tap(x, y)
            SystemClock.sleep(intervalMs)
        }
        return true
    }

    /** Taps one key by the name the keys log for it (enter, delete, shift, space, symbols, a letter...). */
    fun tapKey(name: String): Boolean {
        val (x, y) = keyMap()?.get(name) ?: return false
        tap(x, y)
        return true
    }

    /** Where each key is, from the last layout the keys logged (letters, space, comma, period, shift and so on). */
    private fun keyMap(): Map<String, Pair<Float, Float>>? {
        // Right after the keys open (or Unicorn restarts) their first layout may not be logged yet
        var line: String? = null
        val until = SystemClock.uptimeMillis() + 6000
        while (line == null && SystemClock.uptimeMillis() < until) {
            val fd = ui.executeShellCommand("logcat -d -s UnicornGlide:D")
            val log = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
            line = log.lines().lastOrNull { it.contains(" keys w=") }
            if (line == null) SystemClock.sleep(250)
        }
        if (line == null) return null
        return Regex("\\b(\\w+)=(-?\\d+),(-?\\d+)").findAll(line)
            .filter { it.groupValues[1] != "at" }
            .associate { it.groupValues[1] to (it.groupValues[2].toFloat() to it.groupValues[3].toFloat()) }
    }

    private fun keys(): SwipeSynth.Keys? {
        val map = keyMap() ?: return null
        val cx = FloatArray(26) { Float.NaN }
        val cy = FloatArray(26) { Float.NaN }
        for (c in 'a'..'z') map[c.toString()]?.let { (x, y) -> cx[c - 'a'] = x; cy[c - 'a'] = y }
        return if (cx.any { it.isNaN() }) null else SwipeSynth.Keys("emulator", cx, cy, pxPerMm)
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(45)
        send(down, MotionEvent.ACTION_UP, x, y)
    }

    private fun drag(xs: FloatArray, ys: FloatArray, times: IntArray) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, xs[0], ys[0])
        for (i in 1 until xs.size) {
            val at = down + times[i]
            while (SystemClock.uptimeMillis() < at) SystemClock.sleep(1)
            send(down, MotionEvent.ACTION_MOVE, xs[i], ys[i])
        }
        send(down, MotionEvent.ACTION_UP, xs.last(), ys.last())
    }

    private fun send(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        ui.injectInputEvent(event, action != MotionEvent.ACTION_MOVE)
        event.recycle()
    }
}
