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

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * Glide typing on the emulator, end to end: run by tools/glide-stress.sh with
 *
 *     adb shell am instrument -w -e mode e2e ai.unicorn.glidetest/.GlideInstrumentation
 *     adb shell am instrument -w -e mode soak -e minutes 22 ai.unicorn.glidetest/.GlideInstrumentation
 *
 * Writes its report to the app's files folder (glide-e2e.md or glide-soak.md) and says PASS or FAIL.
 */
class GlideInstrumentation : Instrumentation() {
    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        super.onStart()
        val mode = arguments.getString("mode") ?: "e2e"
        if (mode == "shot") {
            val done = DemoShots(this).run(File(arguments.getString("file") ?: "/data/local/tmp/demo-shot.txt"))
            finish(if (done) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle())
            return
        }
        if (mode == "demo") {
            val demo = DemoGlide(this)
            val typed = arguments.getString("type")
            val done = if (typed != null) {
                demo.type(typed, arguments.getString("intervalMs")?.toLong() ?: 110L)
            } else {
                val words = arguments.getString("words").orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
                demo.run(words, arguments.getString("pauseMs")?.toLong() ?: 450L, arguments.getString("seed")?.toInt() ?: 7)
            }
            finish(if (done) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle())
            return
        }
        val report = Report()
        val passed = try {
            val device = Device(this)
            device.start()
            val words = WordLists(context.assets)
            when (mode) {
                "soak" -> Soak(device, words, report, arguments.getString("minutes")?.toDouble() ?: 20.0).run()
                else -> EndToEnd(device, words, report).run()
            }
        } catch (t: Throwable) {
            report.line("FAIL  the test itself stopped: ${t.javaClass.simpleName}: ${t.message}")
            report.line("```\n${Log.getStackTraceString(t)}\n```")
            false
        }
        val file = File(context.getExternalFilesDir(null), "glide-$mode.md")
        file.writeText(report.toString())
        val result = Bundle()
        result.putString("result", if (passed) "PASS" else "FAIL")
        result.putString("report", file.absolutePath)
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}

class Report {
    private val text = StringBuilder()
    var failures = 0
        private set

    fun line(s: String = "") {
        text.appendLine(s)
        Log.i("GlideTest", s)
    }

    fun check(name: String, passed: Boolean, detail: String = "") {
        if (!passed) failures++
        line("- ${if (passed) "pass" else "**FAIL**"}: $name${if (detail.isNotEmpty()) " ($detail)" else ""}")
    }

    override fun toString() = text.toString()
}

class WordLists(assets: android.content.res.AssetManager) {
    val sections: Map<String, List<String>>
    val sentences: List<List<String>>

    init {
        val map = LinkedHashMap<String, MutableList<String>>()
        var section = "common"
        assets.open("e2e-words.txt").bufferedReader().readLines().forEach { line ->
            val t = line.trim()
            when {
                t.isEmpty() || t.startsWith("#") -> Unit
                t.startsWith("[") -> section = t.trim('[', ']')
                else -> map.getOrPut(section) { ArrayList() }.addAll(t.split(Regex("\\s+")))
            }
        }
        sections = map
        sentences = assets.open("sentences.txt").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().split(Regex("\\s+")) }
    }

    val all: List<String> get() = sections.values.flatten()
}
