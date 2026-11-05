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
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.MoreKeysKeyboard
import com.android.inputmethod.latin.LatinIME
import com.android.inputmethod.latin.R
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Size and position of the keys: the person can make the key area shorter or taller, narrower, slide it to one side
 * (one-handed), or lift it off the bottom edge. The letters keep their QWERTY places; only the whole area changes. Each
 * orientation remembers its own size.
 *
 * Also owns edge taps: when the area is narrower than the screen, a tap in the empty strip beside a row goes to that
 * row's outer key.
 */
object KeyArea {
    /** Off until the emulator checks pass: no menu item, no settings screen, and the engine lays out keys as before. */
    const val ENABLED = true

    /** Learn my aim starts on: it changes nothing until it has 40 clean taps on a letter, and is capped at a quarter key. */
    const val LEARN_AIM_DEFAULT = true
    const val EDGE_TAPS_DEFAULT = true

    private const val PREFS = "unicorn_key_area"
    private const val KEY_PORTRAIT = "portrait"
    private const val KEY_LANDSCAPE = "landscape"
    private const val KEY_EDGE_TAPS = "edge_taps"
    private const val KEY_LEARN_AIM = "learn_aim"
    private const val KEY_EDIT_REQUESTED = "edit_requested_at"
    private const val EDIT_REQUEST_WINDOW_MS = 60_000L
    private const val SIDE_BUTTON_DP = 40f
    private const val SIDE_BUTTON_MARGIN_DP = 2f
    /** Height of Unicorn's toolbar above the suggestions (main_keyboard_frame.xml). */
    private const val TOOLBAR_DP = 44f

    private val main = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var inputView = WeakReference<ViewGroup>(null)
    private var editor = WeakReference<KeyAreaEditor>(null)
    private var swapButton = WeakReference<ImageView>(null)
    private var fullButton = WeakReference<ImageView>(null)

    /** The layout in use, as last laid out by the engine. Main thread only. */
    internal var screen: KeyScreen? = null
        private set
    internal var size = KeyAreaSize.DEFAULT
        private set
    internal var landscape = false
        private set
    private var rowsWidthPx = 0
    private var leftReachPx = 0
    private var rightReachPx = 0
    private var sideButtonsWanted = false
    private var keysShown = true
    private var gutterTop = 0
    private var gutterBottom = 0
    private val gutterPaint = Paint()
    private var gutterShaderHeight = -1

    // Edge taps forwarded to the keys: where the keys are, in the input view
    private var forwardX = 0f
    private var forwardY = 0f

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_EDIT_REQUESTED -> main.post { if (inputView.get()?.isShown == true) openEditorIfRequested() }
            KEY_PORTRAIT, KEY_LANDSCAPE, KEY_EDGE_TAPS, KEY_LEARN_AIM -> scheduleReload()
        }
    }

    private val reload = Runnable {
        Unicorn.safely("KeyArea.reload") {
            val view = inputView.get() ?: return@safely
            if (view.isShown) latinIme(view.context)?.unicornReloadKeys()
        }
    }

    fun prefs(context: Context): SharedPreferences = prefs ?: context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .also {
            prefs = it
            it.registerOnSharedPreferenceChangeListener(listener)
        }

    fun sizeFor(context: Context, isLandscape: Boolean): KeyAreaSize =
        KeyAreaSize.decode(prefs(context).getString(if (isLandscape) KEY_LANDSCAPE else KEY_PORTRAIT, null))

    /** Saves the size for one orientation; the keys follow at once if they are showing. */
    fun save(context: Context, isLandscape: Boolean, value: KeyAreaSize) {
        prefs(context).edit().putString(if (isLandscape) KEY_LANDSCAPE else KEY_PORTRAIT, value.encode()).apply()
    }

    fun resetAll(context: Context) {
        prefs(context).edit().remove(KEY_PORTRAIT).remove(KEY_LANDSCAPE).apply()
    }

    fun edgeTaps(context: Context): Boolean = prefs(context).getBoolean(KEY_EDGE_TAPS, EDGE_TAPS_DEFAULT)
    fun setEdgeTaps(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_EDGE_TAPS, on).apply()
    fun learnAim(context: Context): Boolean = prefs(context).getBoolean(KEY_LEARN_AIM, LEARN_AIM_DEFAULT)
    fun setLearnAim(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_LEARN_AIM, on).apply()

    /** From the app: open the resize handles the next time the keys come up (or now, if they are up). */
    fun requestEditor(context: Context) {
        prefs(context).edit().putLong(KEY_EDIT_REQUESTED, System.currentTimeMillis()).apply()
    }

    class Placement(@JvmField val widthPx: Int, @JvmField val keysHeightPx: Int)

    /**
     * Called as the engine lays out the keys: returns their width and the height of the key rows, and places the
     * views. [fullWidthPx] and [defaultKeysHeightPx] are what the engine would use on its own.
     */
    @JvmStatic
    fun layOut(context: Context, view: View?, fullWidthPx: Int, defaultKeysHeightPx: Int): Placement {
        if (!ENABLED) return Placement(fullWidthPx, defaultKeysHeightPx)
        try {
            val res = context.resources
            val metrics = res.displayMetrics
            landscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val s = KeyScreen(fullWidthPx, metrics.heightPixels, metrics.density, defaultKeysHeightPx, reservedPx(context, view))
            val area = sizeFor(context, landscape).clampedTo(s)
            screen = s
            size = area
            rowsWidthPx = area.widthPx(s)
            val edges = edgeTaps(context)
            val pitch = rowsWidthPx / 10
            leftReachPx = if (edges) KeyAreaSize.edgeReachPx(area.leftPx(s), pitch) else 0
            rightReachPx = if (edges) KeyAreaSize.edgeReachPx(area.rightGapPx(s), pitch) else 0
            TapAim.init(context)
            TapAim.setLandscape(landscape)
            TapAim.setEnabled(learnAim(context))
            (view as? ViewGroup)?.let { place(it) }
            return Placement(rowsWidthPx, area.keysHeightPx(s))
        } catch (e: Exception) {
            Unicorn.noteFailure("KeyArea.layOut", e)
            size = KeyAreaSize.DEFAULT
            rowsWidthPx = fullWidthPx
            leftReachPx = 0
            rightReachPx = 0
            return Placement(fullWidthPx, defaultKeysHeightPx)
        }
    }

    /** The toolbar and suggestions above the keys, and the navigation bar under them, in pixels. */
    private fun reservedPx(context: Context, view: View?): Int {
        val res = context.resources
        val density = res.displayMetrics.density
        val toolbar = (TOOLBAR_DP * density).roundToInt()
        val suggestions = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view?.rootWindowInsets else null
        val navigation = if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            (KeyAreaSize.NAV_BAR_RESERVE_DP * density).roundToInt()
        }
        return toolbar + suggestions + navigation
    }

    /** Adds the side buttons and the resize handles to the input view. Called once it is inflated. */
    @JvmStatic
    fun attach(view: ViewGroup) {
        if (!ENABLED) return
        Unicorn.safely("KeyArea.attach") {
            prefs(view.context)
            inputView = WeakReference(view)
            val style = KeyboardStyle(view.context)
            val swap = sideButton(style, R.drawable.ic_unicorn_side_swap, "Move to the other side") {
                save(view.context, landscape, size.copy(position = if (size.position > 0.5f) 0f else 1f))
            }
            val full = sideButton(style, R.drawable.ic_unicorn_open_full, "Use the full width") {
                save(view.context, landscape, size.copy(widthFraction = 1f, position = 0.5f))
            }
            val buttonSize = style.dp(SIDE_BUTTON_DP)
            view.addView(swap, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.BOTTOM or Gravity.LEFT))
            view.addView(full, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.BOTTOM or Gravity.LEFT))
            swapButton = WeakReference(swap)
            fullButton = WeakReference(full)
            val edit = KeyAreaEditor(view.context).apply { visibility = View.GONE }
            view.addView(edit, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            editor = WeakReference(edit)
        }
    }

    private fun sideButton(style: KeyboardStyle, icon: Int, description: String, onClick: () -> Unit) = ImageView(style.context).apply {
        setImageResource(icon)
        setColorFilter(style.text)
        scaleType = ImageView.ScaleType.CENTER
        background = style.rounded(style.chip, radiusDp = SIDE_BUTTON_DP / 2)
        contentDescription = description
        isFocusable = false
        visibility = View.GONE
        setOnClickListener { Unicorn.safely("KeyArea.side") { onClick() } }
    }

    private fun place(view: ViewGroup) {
        val s = screen ?: return
        val frame = view.findViewById<View>(R.id.main_keyboard_frame) ?: return
        val params = frame.layoutParams as? FrameLayout.LayoutParams ?: return
        val narrowed = !size.isFullWidth
        params.gravity = Gravity.BOTTOM or Gravity.LEFT
        params.width = if (narrowed) rowsWidthPx else ViewGroup.LayoutParams.WRAP_CONTENT
        params.leftMargin = size.leftPx(s)
        params.bottomMargin = size.liftPx(s)
        frame.layoutParams = params

        val palette = KeyboardStyle.paletteFor(view.context)
        gutterTop = palette.background
        gutterBottom = palette.backgroundEnd
        gutterShaderHeight = -1

        // The two side buttons sit in the wider empty side beside the toolbar and the suggestions, well above the
        // letters, so a thumb that overshoots Q or A can never press them
        val swap = swapButton.get()
        val full = fullButton.get()
        if (swap != null && full != null) {
            val leftGap = size.leftPx(s)
            val rightGap = size.rightGapPx(s)
            val onLeft = leftGap >= rightGap
            val gap = if (onLeft) leftGap else rightGap
            val toolbar = (TOOLBAR_DP * s.density).roundToInt()
            val suggestions = view.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
            val buttonSize = minOf((SIDE_BUTTON_DP * s.density).roundToInt(), suggestions, toolbar)
            sideButtonsWanted = narrowed && gap >= buttonSize + (SIDE_BUTTON_MARGIN_DP * 2 * s.density).roundToInt()
            val centreX = if (onLeft) gap / 2 else s.widthPx - gap / 2
            val aboveKeys = size.liftPx(s) + size.keysHeightPx(s)
            for ((button, bottom) in listOf(
                swap to aboveKeys + suggestions + (toolbar - buttonSize) / 2,
                full to aboveKeys + (suggestions - buttonSize) / 2,
            )) {
                button.visibility = if (sideButtonsWanted && keysShown && !isEditing()) View.VISIBLE else View.GONE
                val p = button.layoutParams as FrameLayout.LayoutParams
                p.width = buttonSize
                p.height = buttonSize
                p.leftMargin = centreX - buttonSize / 2
                p.bottomMargin = bottom
                button.layoutParams = p
            }
        }
        editor.get()?.onLayoutChanged()
        view.invalidate()
    }

    /** The letters are showing (true), or the emoji or nothing is (false). */
    @JvmStatic
    fun onKeysShown(shown: Boolean) {
        keysShown = shown
        if (!shown) closeEditor()
        val visibility = if (sideButtonsWanted && shown && !isEditing()) View.VISIBLE else View.GONE
        swapButton.get()?.visibility = visibility
        fullButton.get()?.visibility = visibility
    }

    /** Paints the empty sides and the lifted space in the theme's colour. Called before the input view's children draw. */
    @JvmStatic
    fun drawBehind(view: ViewGroup, canvas: Canvas) {
        if (size.isFullWidth && size.liftDp <= 0f) return
        val frame = view.findViewById<View>(R.id.main_keyboard_frame) ?: return
        if (frame.visibility != View.VISIBLE) return
        val top = frame.top.toFloat()
        val bottom = (view.height - view.paddingBottom).toFloat()
        if (bottom <= top) return
        if (gutterShaderHeight != view.height) {
            gutterPaint.shader = LinearGradient(0f, top, 0f, bottom, gutterTop, gutterBottom, Shader.TileMode.CLAMP)
            gutterShaderHeight = view.height
        }
        canvas.drawRect(0f, top, view.width.toFloat(), bottom, gutterPaint)
    }

    /**
     * Edge taps: the key rows' x coordinate [x] (0 at their left edge), moved onto the outer key when the tap is in the
     * empty strip beside the rows. Pop-ups and other key sets are left alone.
     */
    @JvmStatic
    fun reachEdge(keyboard: Keyboard?, x: Int): Int {
        if (keyboard == null || keyboard is MoreKeysKeyboard || (leftReachPx == 0 && rightReachPx == 0)) return x
        if (keyboard.mOccupiedWidth != rowsWidthPx) return x
        return KeyAreaSize.reachEdge(x, keyboard.mOccupiedWidth, leftReachPx, rightReachPx)
    }

    /** True when a touch starts in the edge-tap strip beside the keys; the rest of it then goes to the keys. */
    @JvmStatic
    fun interceptEdgeTap(view: ViewGroup, event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN || (leftReachPx == 0 && rightReachPx == 0) || isEditing()) return false
        val keys = view.findViewById<View>(R.id.keyboard_view) ?: return false
        if (!keys.isShown) return false
        var left = 0f
        var top = 0f
        var v: View = keys
        while (v !== view) {
            left += v.left + v.translationX
            top += v.top + v.translationY
            v = v.parent as? View ?: return false
        }
        val x = event.x
        val y = event.y
        if (y < top || y >= top + keys.height) return false
        val right = left + keys.width
        val inReach = (x < left && x >= left - leftReachPx) || (x >= right && x < right + rightReachPx)
        if (!inReach) return false
        forwardX = left
        forwardY = top
        return true
    }

    @JvmStatic
    fun forwardEdgeTap(view: ViewGroup, event: MotionEvent): Boolean {
        val keys = view.findViewById<View>(R.id.keyboard_view) ?: return false
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(-forwardX, -forwardY)
        keys.dispatchTouchEvent(copy)
        copy.recycle()
        return true
    }

    // Resize handles ---------------------------------------------------------------------------------------------

    @JvmStatic
    fun isEditing(): Boolean = editor.get()?.visibility == View.VISIBLE

    /** Pixels kept free under the keys. */
    @JvmStatic
    fun liftPx(): Int = screen?.let { size.liftPx(it) } ?: 0

    fun openEditor() {
        Unicorn.safely("KeyArea.openEditor") {
            val edit = editor.get() ?: return@safely
            edit.visibility = View.VISIBLE
            edit.bringToFront()
            edit.onLayoutChanged()
            swapButton.get()?.visibility = View.GONE
            fullButton.get()?.visibility = View.GONE
        }
    }

    fun closeEditor() {
        val edit = editor.get() ?: return
        if (edit.visibility == View.GONE) return
        edit.visibility = View.GONE
        inputView.get()?.let { place(it) }
    }

    /** The size being edited, saved for this orientation. */
    internal fun saveCurrent(context: Context, value: KeyAreaSize) = save(context, landscape, value)

    /** Toolbar menu: one-handed on the right, or back to the full width. */
    fun toggleOneHanded(context: Context) {
        save(context, landscape, if (size.isFullWidth) KeyAreaSize.oneHanded(onRight = true, current = size) else size.copy(widthFraction = 1f, position = 0.5f))
    }

    @JvmStatic
    fun onStartInputView(context: Context) {
        Unicorn.safely("KeyArea.onStartInputView") {
            TapAim.onStartInputView()
            // A size changed in the app while the keys were away
            val s = screen
            if (s != null && sizeFor(context, landscape).clampedTo(s) != size) scheduleReload()
            main.post { openEditorIfRequested() }
        }
    }

    @JvmStatic
    fun onFinishInputView() {
        Unicorn.safely("KeyArea.onFinishInputView") {
            closeEditor()
            TapAim.save()
        }
    }

    private fun openEditorIfRequested() {
        val p = prefs ?: return
        val at = p.getLong(KEY_EDIT_REQUESTED, 0L)
        if (at == 0L) return
        p.edit().remove(KEY_EDIT_REQUESTED).apply()
        if (System.currentTimeMillis() - at < EDIT_REQUEST_WINDOW_MS) openEditor()
    }

    private fun scheduleReload() {
        main.removeCallbacks(reload)
        main.post(reload)
    }

    private fun latinIme(context: Context): LatinIME? {
        var c: Context? = context
        while (c != null) {
            if (c is LatinIME) return c
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }
}
