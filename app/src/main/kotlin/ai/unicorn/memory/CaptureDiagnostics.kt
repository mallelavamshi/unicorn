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

package ai.unicorn.memory

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device log of what prompt saving decided and why, so failures in a specific AI app can be diagnosed on
 * a real phone. It never contains prompt text, only apps, field attributes, triggers and lengths.
 */
class CaptureDiagnostics(context: Context) {
    companion object {
        private const val TAG = "UnicornCapture"
        private const val MAX_LINES = 300
    }

    private val file = File(context.createDeviceProtectedStorageContext().filesDir, "capture-diagnostics.log")
    private val lines = ArrayDeque<String>()
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT)

    init {
        runCatching { if (file.exists()) file.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) } }
    }

    @Synchronized
    fun log(event: String) {
        Log.i(TAG, event)
        lines.addLast("${format.format(Date())} $event")
        while (lines.size > MAX_LINES) lines.removeFirst()
        runCatching { file.writeText(lines.joinToString("\n")) }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        runCatching { file.delete() }
    }
}
