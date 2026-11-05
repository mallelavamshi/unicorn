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
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Drives Unicorn's keys like a finger: injected touches at the real key positions, which the keys write to the log
 * (tag UnicornGlide, debug level, switched on here) each time they are laid out.
 */
class Device(private val instrumentation: Instrumentation) {
    val ui: UiAutomation = instrumentation.uiAutomation
    lateinit var activity: GlideTestActivity
    var keys: Map<String, Pair<Float, Float>> = emptyMap()
        private set
    var keyboardTop = 0f
        private set
    val pxPerMm: Float = instrumentation.targetContext.resources.displayMetrics.xdpi / 25.4f

    fun shell(command: String): String {
        val fd = ui.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    fun start() {
        shell("setprop log.tag.UnicornGlide DEBUG")
        clearLog()
        val info = ui.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        ui.serviceInfo = info
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(instrumentation.targetContext, GlideTestActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = instrumentation.startActivitySync(intent) as GlideTestActivity
        instrumentation.waitForIdleSync()
        check(waitForKeys(timeoutMs = 60_000)) { "Unicorn's keys never showed (is it the selected input method?)" }
        // The keys load glide typing's words in the background when they first start (seconds on a busy emulator).
        // Warm-up swipes until one decodes, so the checks measure glide typing rather than the start-up.
        val until = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < until) {
            swipe(SwipeSynth.swipe("hello", synthKeys(), SwipeSynth.Noise.CLEAN, kotlin.random.Random(1)))
            SystemClock.sleep(1500)
            if (shell("logcat -d -s UnicornGlide:D").contains("final ms=")) break
        }
        clear()
        clearLog()
    }

    /** Waits for a fresh key layout in the log; [wide] waits for one wider than tall (landscape). */
    fun waitForKeys(wide: Boolean? = null, timeoutMs: Long = 10_000): Boolean {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            val line = shell("logcat -d -s UnicornGlide:D").lines().lastOrNull { it.contains(" keys w=") }
            if (line != null) {
                val parsed = parseKeys(line)
                val size = Regex("w=(\\d+) h=(\\d+)").find(line)?.groupValues
                // Keys in landscape are far wider than tall; in portrait under twice as wide
                val isWide = size != null && size[1].toInt() > 2.5f * size[2].toInt()
                if (parsed.size >= 26 && (wide == null || isWide == wide)) {
                    keys = parsed
                    keyboardTop = Regex("at=(-?\\d+),(-?\\d+)").find(line)?.groupValues?.get(2)?.toFloat() ?: 0f
                    return true
                }
            }
            dismissSystemDialogs()
            if (!activity.isFinishing) tapField()
            SystemClock.sleep(600)
        }
        return false
    }

    private fun parseKeys(line: String): Map<String, Pair<Float, Float>> {
        val out = HashMap<String, Pair<Float, Float>>()
        for (m in Regex("(\\w+)=(-?\\d+),(-?\\d+)").findAll(line)) {
            val name = m.groupValues[1]
            if (name == "at") continue
            out[name] = m.groupValues[2].toFloat() to m.groupValues[3].toFloat()
        }
        return out
    }

    /**
     * Back to a known state after something went astray (a tap that opened the symbols, a turn that didn't settle):
     * focus the field, and if the last layout logged has no letters, press the key that switches back to them.
     */
    fun recover() {
        clear()
        val lines = shell("logcat -d -s UnicornGlide:D").lines().filter { it.contains(" keys w=") }
        if (lines.isNotEmpty() && parseKeys(lines.last()).count { it.key.length == 1 } < 26) {
            keys["symbols"]?.let { (x, y) -> tap(x, y) }
            SystemClock.sleep(600)
        }
        clearLog()
        tapField()
        waitForKeys(timeoutMs = 20_000)
        clear()
    }

    /** "<app> isn't responding" dialogs from other apps that were answered "Wait" (a sign of a struggling emulator). */
    var systemDialogs = 0
        private set

    /**
     * Answers "Wait" on an "isn't responding" dialog from another app (the launcher, the system): it covers the
     * screen and takes the touches. A person would do the same. Unicorn's own would count as an ANR in the gate.
     */
    fun dismissSystemDialogs() {
        try {
            for (window in ui.windows) {
                val root = window.root ?: continue
                if (root.findAccessibilityNodeInfosByText("isn't responding").isEmpty()) continue
                val wait = root.findAccessibilityNodeInfosByText("Wait").firstOrNull() ?: continue
                var node: AccessibilityNodeInfo? = wait
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    systemDialogs++
                    SystemClock.sleep(500)
                }
            }
        } catch (e: Exception) {
            // The window list can change under us; the next check catches it
        }
    }

    /** The keys in the form the swipe generator takes. */
    fun synthKeys(): SwipeSynth.Keys {
        val cx = FloatArray(26) { Float.NaN }
        val cy = FloatArray(26) { Float.NaN }
        for (c in 'a'..'z') keys[c.toString()]?.let { (x, y) -> cx[c - 'a'] = x; cy[c - 'a'] = y }
        return SwipeSynth.Keys("emulator", cx, cy, pxPerMm)
    }

    /** Taps the end of the message field, like a person opening the keys. */
    fun tapField() {
        val location = IntArray(2)
        var size = 0 to 0
        instrumentation.runOnMainSync {
            activity.field.getLocationOnScreen(location)
            size = activity.field.width to activity.field.height
        }
        tap(location[0] + size.first * 0.8f, location[1] + size.second / 2f)
    }

    fun text(): String {
        var text = ""
        instrumentation.runOnMainSync { text = activity.field.text.toString() }
        return text
    }

    /** Puts the field back to [text], cursor at the end (to undo a long-press popup's character). */
    fun setText(text: String) {
        instrumentation.runOnMainSync {
            activity.field.setText(text)
            activity.field.setSelection(text.length)
        }
        SystemClock.sleep(250)
    }

    fun clear() {
        instrumentation.runOnMainSync { activity.field.setText("") }
        SystemClock.sleep(250)
    }

    private fun send(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        // Moves don't wait for the keys to handle them, so a swipe keeps a finger's pace on a slow emulator
        ui.injectInputEvent(event, action != MotionEvent.ACTION_MOVE)
        event.recycle()
    }

    /** A finger along ([xs], [ys]) at the given times (ms from touching down), in real time. */
    fun drag(xs: FloatArray, ys: FloatArray, times: IntArray) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, xs[0], ys[0])
        for (i in 1 until xs.size) {
            val at = down + times[i]
            while (SystemClock.uptimeMillis() < at) SystemClock.sleep(1)
            send(down, MotionEvent.ACTION_MOVE, xs[i], ys[i])
        }
        send(down, MotionEvent.ACTION_UP, xs.last(), ys.last())
    }

    fun swipe(swipe: SwipeSynth.Swipe) = drag(swipe.x, swipe.y, swipe.t)

    /**
     * Two quick taps, sent without waiting for the keys between them: Android's double-tap window is 300 ms, and a
     * finger doesn't wait for a busy phone either.
     */
    fun doubleTap(x: Float, y: Float) {
        for (i in 0 until 2) {
            val down = SystemClock.uptimeMillis()
            val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            press.source = InputDevice.SOURCE_TOUCHSCREEN
            ui.injectInputEvent(press, false)
            press.recycle()
            SystemClock.sleep(40)
            val release = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            release.source = InputDevice.SOURCE_TOUCHSCREEN
            ui.injectInputEvent(release, false)
            release.recycle()
            SystemClock.sleep(60)
        }
    }

    fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(40)
        send(down, MotionEvent.ACTION_UP, x, y)
    }

    fun tapKey(name: String) {
        val (x, y) = keys[name] ?: error("No key $name")
        tap(x, y)
        // A person's pace; much faster and a loaded emulator misses the engine's 200 ms suggestion deadline
        SystemClock.sleep(250)
    }

    /** Taps the letters (and spaces) of [text]. */
    fun typeKeys(text: String) {
        for (c in text) tapKey(if (c == ' ') "space" else c.lowercaseChar().toString())
    }

    /** Waits until the field stops changing after a swipe; returns ms until the first change, or -1. */
    fun awaitChange(before: String, timeoutMs: Long = 2500): Long {
        val start = SystemClock.uptimeMillis()
        var changedAt = -1L
        var last = before
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val now = text()
            if (now != last) {
                if (changedAt < 0) changedAt = SystemClock.uptimeMillis() - start
                last = now
            } else if (changedAt >= 0 && SystemClock.uptimeMillis() - start - changedAt > 200) {
                break
            }
            SystemClock.sleep(10)
        }
        return changedAt
    }

    /** The words in the suggestion strip, with where to tap them. */
    fun suggestions(): List<Pair<String, Rect>> {
        val window = ui.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return emptyList()
        val root = window.root ?: return emptyList()
        val out = ArrayList<Pair<String, Rect>>()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && node.isVisibleToUser) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.bottom <= keyboardTop + 4) out.add(text to r)
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return out
    }

    fun appPid(): String = shell("pidof ai.unicorn.app").trim()

    /** The words the keys decoded for the last swipe (logged only for this test app), best first. */
    fun lastCandidates(): List<String> =
        shell("logcat -d -s UnicornGlide:D").lines().lastOrNull { it.contains("final ms=") }
            ?.substringAfter("words=", "")?.trim()?.split(",")?.filter { it.isNotBlank() && it != "-" } ?: emptyList()

    /** Strip suggestions that are words the keys decoded, other than [current]. */
    fun alternatives(current: String): List<Pair<String, Rect>> {
        val decoded = lastCandidates().map { it.lowercase() }.toSet()
        return suggestions().filter { (text, _) -> text.lowercase() in decoded && !text.equals(current.trim(), ignoreCase = true) }
    }

    /** Decode times in ms: wall clock, and the decoding thread's own CPU time. */
    class Times(val finalWall: List<Int>, val finalCpu: List<Int>, val previewWall: List<Int>, val previewCpu: List<Int>)

    // Decode times read from the log before each clear, until decodeTimes() hands them over
    private val harvested = Array(4) { ArrayList<Int>() }

    /** Clears the log, keeping the decode times in it: turning the phone and recovering clear it too. */
    private fun clearLog() {
        val log = shell("logcat -d -s UnicornGlide:D")
        for ((i, kind) in listOf("final" to 1, "final" to 2, "preview" to 1, "preview" to 2).withIndex()) {
            Regex("${kind.first} ms=(\\d+) cpu=(\\d+)").findAll(log).forEach { harvested[i].add(it.groupValues[kind.second].toInt()) }
        }
        shell("logcat -b main -c")
    }

    /** Decode times the keys logged since the last call. */
    fun decodeTimes(): Times {
        clearLog()
        val times = Times(harvested[0].toList(), harvested[1].toList(), harvested[2].toList(), harvested[3].toList())
        harvested.forEach { it.clear() }
        return times
    }

    fun rotate(landscape: Boolean): Boolean {
        clearLog()
        ui.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        SystemClock.sleep(1500)
        instrumentation.runOnMainSync { activity.showKeys() }
        return waitForKeys(wide = landscape, timeoutMs = 30_000)
    }

    fun unfreezeRotation() {
        ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
        ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
}
