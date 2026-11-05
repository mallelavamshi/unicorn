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

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File

/**
 * Plays one shot of the demo video (docs/plan/demo-video-x.md) from a step file, so a shot can be changed and
 * re-shot without rebuilding. The host pushes the file and records the screen around
 *
 *     am instrument -w -e mode shot -e file /data/local/tmp/demo-shot.txt ai.unicorn.glidetest/.GlideInstrumentation
 *
 * One step per line ("#" starts a comment):
 *
 *     extra thread <hey!|>on my way      an extra for the next `stage` (thread, draft, incoming, name, email...)
 *     stage                              opens StageActivity with those extras and waits for the keys
 *     launch com.example/.Activity       starts another app's activity
 *     tap Tone                           taps the first on-screen node whose text or description matches (regex)
 *     longpress omg yes                  the same, held like a finger
 *     wait Saturday 20000                waits up to that many ms for a matching node
 *     tapxy 540 1200 / drag x1 y1 x2 y2 ms
 *     type Plan a cozy weekend           taps the keys; `typeat 160 text` sets the pace in ms per key
 *     glide see you at noon              glides the words
 *     key enter                          taps one key by its logged name (enter, delete, shift, space...)
 *     back / home / sleep 1500
 *
 * Every step is logged under the tag DemoShot, so the cut can be timed from the log.
 */
class DemoShots(private val instrumentation: Instrumentation) {
    private val ui = instrumentation.uiAutomation
    private val glide = DemoGlide(instrumentation)
    private val extras = LinkedHashMap<String, String>()

    init {
        val info = ui.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        ui.serviceInfo = info
    }

    fun run(file: File): Boolean {
        for (raw in file.readLines()) {
            val line = raw.substringBefore(" #").trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val command = line.substringBefore(' ')
            val arg = line.substringAfter(' ', "")
            Log.w(TAG, "step ${SystemClock.uptimeMillis()} $line")
            val ok = when (command) {
                "extra" -> { extras[arg.substringBefore(' ')] = arg.substringAfter(' ', ""); true }
                "stage" -> stage()
                "launch" -> { shell("am start -W -n $arg"); true }
                "tap" -> node(arg.substringBeforeLast(' ').takeIf { arg.lastWord().isNumber() } ?: arg, arg.timeout())
                    ?.let { tapCenter(it, holdMs = 45); true } ?: false
                "longpress" -> node(arg, 5000)?.let { tapCenter(it, holdMs = 750); true } ?: false
                // tapime / waitime: the same, but only in Unicorn's own window (its panel, cards and keys)
                "tapime" -> node(arg.substringBeforeLast(' ').takeIf { arg.lastWord().isNumber() } ?: arg, arg.timeout(), imeOnly = true)
                    ?.let { tapCenter(it, holdMs = 45); true } ?: false
                "waitime" -> node(arg.substringBeforeLast(' ').takeIf { arg.lastWord().isNumber() } ?: arg, arg.timeout(), imeOnly = true) != null
                "wait" -> node(arg.substringBeforeLast(' ').takeIf { arg.lastWord().isNumber() } ?: arg, arg.timeout()) != null
                "tapxy" -> arg.numbers().let { tap(it[0], it[1], 45); true }
                "drag" -> arg.numbers().let { drag(it[0], it[1], it[2], it[3], it[4].toLong()); true }
                // dragfrom Classic Dark -850 0 500: a drag starting on a node, by an offset (a sideways list)
                "dragfrom" -> {
                    val parts = arg.split(' ')
                    val n = parts.takeLast(3).map { it.toFloat() }
                    node(parts.dropLast(3).joinToString(" "), 5000)?.let {
                        val r = Rect().also { r -> it.getBoundsInScreen(r) }
                        drag(r.exactCenterX(), r.exactCenterY(), r.exactCenterX() + n[0], r.exactCenterY() + n[1], n[2].toLong())
                        true
                    } ?: false
                }
                // scrollto Learn my aim: swipes the screen up until a node shows (at most 8 times)
                "scrollto" -> {
                    var found = node(arg, 600)
                    var tries = 0
                    while (found == null && tries++ < 8) {
                        drag(540f, 1700f, 540f, 1000f, 350)
                        SystemClock.sleep(500)
                        found = node(arg, 600)
                    }
                    found != null
                }
                "type" -> glide.type(arg, 110)
                "typeat" -> glide.type(arg.substringAfter(' '), arg.substringBefore(' ').toLong())
                "glide" -> glide.run(arg.split(' ').filter { it.isNotEmpty() }, 450, 7)
                "key" -> glide.tapKey(arg)
                "back" -> { shell("input keyevent KEYCODE_BACK"); true }
                "home" -> { shell("input keyevent KEYCODE_HOME"); true }
                "sleep" -> { SystemClock.sleep(arg.toLong()); true }
                // dump: logs every visible node with text or a description, for writing new steps
                "dump" -> { dump(); true }
                else -> false
            }
            if (!ok) {
                Log.w(TAG, "failed: $line")
                return false
            }
        }
        Log.w(TAG, "done ${SystemClock.uptimeMillis()}")
        return true
    }

    private fun stage(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(instrumentation.targetContext, StageActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        for ((k, v) in extras) {
            if (k.endsWith("Ms")) intent.putExtra(k, v.toLong()) else intent.putExtra(k, v)
        }
        extras.clear()
        instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        // The keys slide up after the activity shows
        return node("Ask Unicorn|Grammar", 10_000) != null
    }

    /** The first visible node, in any window, whose text or description matches [pattern]. */
    private fun node(pattern: String, timeoutMs: Long, imeOnly: Boolean = false): AccessibilityNodeInfo? {
        val regex = Regex(pattern)
        val until = SystemClock.uptimeMillis() + timeoutMs
        do {
            // Compose screens change without invalidating the cached tree, so always read it fresh
            if (Build.VERSION.SDK_INT >= 34) ui.clearCache()
            val roots = try {
                ui.windows.filter { !imeOnly || it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.mapNotNull { it.root }
            } catch (e: Exception) {
                emptyList()
            }.ifEmpty { if (imeOnly) emptyList() else listOfNotNull(ui.rootInActiveWindow) }
            for (root in roots) find(root, regex)?.let { return it }
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        return null
    }

    private fun find(node: AccessibilityNodeInfo?, regex: Regex): AccessibilityNodeInfo? {
        if (node == null) return null
        val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        if (node.isVisibleToUser && labels.any { regex.containsMatchIn(it) }) return node
        for (i in 0 until node.childCount) find(node.getChild(i), regex)?.let { return it }
        return null
    }

    private fun dump() {
        if (Build.VERSION.SDK_INT >= 34) ui.clearCache()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (node.isVisibleToUser && (node.text != null || node.contentDescription != null)) {
                Log.w(TAG, "node ${"  ".repeat(depth)}text=${node.text} desc=${node.contentDescription} " +
                    "class=${node.className?.toString()?.substringAfterLast('.')} bounds=${r.toShortString()}")
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        ui.windows.forEach { walk(it.root, 0) }
    }

    private fun tapCenter(node: AccessibilityNodeInfo, holdMs: Long) {
        val r = Rect()
        node.getBoundsInScreen(r)
        tap(r.exactCenterX(), r.exactCenterY(), holdMs)
    }

    private fun tap(x: Float, y: Float, holdMs: Long) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(holdMs)
        send(down, MotionEvent.ACTION_UP, x, y)
    }

    private fun drag(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x1, y1)
        SystemClock.sleep(120)
        val steps = (ms / 16).coerceAtLeast(2)
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            // Ease in and out, like a finger
            val e = f * f * (3 - 2 * f)
            send(down, MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * e, y1 + (y2 - y1) * e)
            SystemClock.sleep(16)
        }
        SystemClock.sleep(80)
        send(down, MotionEvent.ACTION_UP, x2, y2)
    }

    private fun send(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        ui.injectInputEvent(event, true)
        event.recycle()
    }

    private fun shell(command: String) {
        val fd = ui.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun String.lastWord() = substringAfterLast(' ')
    private fun String.isNumber() = isNotEmpty() && all { it.isDigit() }
    private fun String.timeout() = lastWord().takeIf { it.isNumber() && contains(' ') }?.toLong() ?: 5000L
    private fun String.numbers() = split(' ').filter { it.isNotEmpty() }.map { it.toFloat() }

    companion object {
        const val TAG = "DemoShot"
    }
}
