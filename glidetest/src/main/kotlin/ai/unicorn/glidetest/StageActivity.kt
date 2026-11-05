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
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * A plain, unbranded chat screen for the demo video's typing shots (docs/plan/demo-video.md): a made-up contact, a
 * few bubbles and a message field. It imitates no real messenger. Started by the shot scripts with
 *
 *     am start -n ai.unicorn.glidetest/.StageActivity --es thread '<hey!|>on my way' --es draft 'text'
 *
 * where each "|"-separated message starts with "<" (theirs) or ">" (mine). `incoming` adds one more message of
 * theirs after `incomingDelayMs`. Long-pressing a bubble copies it, like a messenger does; Send moves the field's
 * text into a bubble of mine.
 */
class StageActivity : Activity() {
    private lateinit var thread: LinearLayout
    private lateinit var scroll: ScrollView
    lateinit var field: EditText

    private val ink = Color.parseColor("#1C1035")
    private val muted = Color.parseColor("#6F6782")
    private val violet = Color.parseColor("#7430A4")
    private val theirs = Color.parseColor("#F1EDF7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: "Maya"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        // `email` (the recipient) turns the screen into a plain new-email form instead of a chat
        intent.getStringExtra("email")?.let { to ->
            setContentView(root)
            root.addView(header("New email", "To: $to"))
            root.addView(View(this).apply { setBackgroundColor(Color.parseColor("#ECE8F2")) }, LinearLayout.LayoutParams(-1, dp(1)))
            root.addView(composer(email = true), LinearLayout.LayoutParams(-1, 0, 1f))
            insetsAndBars(root)
            intent.getStringExtra("draft")?.let { field.setText(it); field.setSelection(it.length) }
            return
        }
        root.addView(header(name))
        root.addView(View(this).apply { setBackgroundColor(Color.parseColor("#ECE8F2")) }, LinearLayout.LayoutParams(-1, dp(1)))
        thread = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(thread, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(composer())
        setContentView(root)
        insetsAndBars(root)

        intent.getStringExtra("thread")?.split("|")?.filter { it.length > 1 }?.forEach { addMessage(it.drop(1), mine = it[0] == '>') }
        intent.getStringExtra("draft")?.let { field.setText(it); field.setSelection(it.length) }
        intent.getStringExtra("incoming")?.let { text ->
            thread.postDelayed({ addMessage(text, mine = false) }, intent.getLongExtra("incomingDelayMs", 1500))
        }
    }

    /** Edge to edge (Android 15): pad for the status bar on top and for the navigation bar or the keys below. */
    private fun insetsAndBars(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            }
            if (::scroll.isInitialized) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        field.requestFocus()
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, 0) }
    }

    private fun header(name: String, subtitle: String = "Active now") = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(16), dp(10))
        addView(text("‹", 30f, ink).apply { setPadding(dp(6), 0, dp(10), dp(4)) })
        addView(TextView(context).apply {
            text = name.take(1)
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#F2A7C3"), Color.parseColor("#9D6BD8")))
                .apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(name, 18f, ink, bold = true))
            addView(text(subtitle, 13f, muted))
        })
    }

    private fun composer(email: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = if (email) Gravity.TOP else Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(10))
        field = EditText(context).apply {
            hint = if (email) "Write your email" else "Message"
            textSize = 17f
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            if (email) {
                gravity = Gravity.TOP or Gravity.START
                background = null
                setPadding(dp(8), dp(8), dp(8), dp(8))
            } else {
                maxLines = 5
                background = rounded(Color.parseColor("#F3F1F7"), 22f)
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
        }
        addView(field, LinearLayout.LayoutParams(0, if (email) -1 else -2, 1f))
        addView(TextView(context).apply {
            text = "↑"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(violet) }
            contentDescription = "Send"
            setOnClickListener { send() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(10) })
    }

    private fun send() {
        val text = field.text.toString().trim()
        if (text.isEmpty()) return
        addMessage(text, mine = true)
        field.setText("")
    }

    private fun addMessage(text: String, mine: Boolean) {
        val bubble = text(text, 17f, if (mine) Color.WHITE else ink).apply {
            background = rounded(if (mine) violet else theirs, 20f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setOnLongClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", text))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                true
            }
        }
        thread.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            topMargin = dp(6)
        })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
