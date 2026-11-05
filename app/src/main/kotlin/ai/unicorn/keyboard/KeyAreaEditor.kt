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
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.inputmethod.latin.R
import kotlin.math.abs

/**
 * The resize handles shown over the keys: drag the top for height, either side for width, the middle to slide the
 * keys sideways or lift them off the bottom. The outline follows the finger and the keys are laid out again a few
 * times a second, so the result is visible while dragging. Reset and Done sit in the toolbar's place.
 */
@SuppressLint("ViewConstructor")
class KeyAreaEditor(context: Context) : FrameLayout(context) {
    private val style = KeyboardStyle(context)
    private val density = resources.displayMetrics.density
    private val outline = RectF()
    private var keysTop = 0f

    private val scrim = Paint().apply { color = 0x80000000.toInt() }
    private val dim = Paint().apply { color = 0x33000000 }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = this@KeyAreaEditor.style.accent
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@KeyAreaEditor.style.accent }
    private val handleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@KeyAreaEditor.style.secondaryText
        textAlign = Paint.Align.CENTER
    }
    /** Covers the toolbar and suggestions while editing, so Reset, Done and the hint sit on a clean bar. */
    private val barPaint = Paint().apply { color = this@KeyAreaEditor.style.background or 0xFF000000.toInt() }
    private val arrow = Path()
    private val handleRect = RectF()

    private val reset = style.pill("Reset") {
        candidate = null
        KeyArea.saveCurrent(context, KeyAreaSize.DEFAULT)
    }
    private val done = style.pill("Done", selected = true) { KeyArea.closeEditor() }

    private enum class Handle { TOP, LEFT, RIGHT, MOVE }

    private class Drag(val handle: Handle, val x: Float, val y: Float, val start: KeyAreaSize, val startLeft: Float, val startRight: Float)

    private var drag: Drag? = null
    /** The size under the finger, until the keys have caught up with it. */
    private var candidate: KeyAreaSize? = null
    private var lastSave = 0L

    init {
        setWillNotDraw(false)
        isClickable = true
        contentDescription = "Size and position. Drag the edges to resize the keys, or the middle to move them."
        addView(reset, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(done, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Float) = value * density

    private fun shown(): KeyAreaSize = candidate ?: KeyArea.size

    /** The keys were laid out again (or the editor just opened). */
    fun onLayoutChanged() {
        if (drag == null && candidate != null && candidate == KeyArea.size) candidate = null
        if (width > 0) placeOutline()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        placeOutline()
    }

    private fun placeOutline() {
        val screen = KeyArea.screen ?: return
        val size = shown()
        val parent = parent as? ViewGroup
        val frame = parent?.findViewById<View>(R.id.main_keyboard_frame)
        val keys = parent?.findViewById<View>(R.id.keyboard_view)
        // The toolbar and suggestions above the key rows keep their height
        val chrome = if (frame != null && keys != null && keys.height > 0) (frame.height - keys.height).toFloat() else dp(84f)
        val bottom = height - size.liftPx(screen).toFloat()
        val left = size.leftPx(screen).toFloat()
        keysTop = bottom - size.keysHeightPx(screen)
        outline.set(left, keysTop - chrome, left + size.widthPx(screen), bottom)
        val inset = dp(8f)
        val barMiddle = outline.top + chrome.coerceAtMost(dp(44f)) / 2
        reset.translationX = outline.left + inset
        reset.translationY = barMiddle - reset.height / 2f
        done.translationX = outline.right - inset - done.width
        done.translationY = barMiddle - done.height / 2f
        // The side handles can sit on the screen edge: keep the system's back swipe off them
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val middle = ((keysTop + outline.bottom) / 2).toInt()
            val half = dp(60f).toInt()
            val reach = dp(28f).toInt()
            systemGestureExclusionRects = listOf(
                Rect(outline.left.toInt() - reach, middle - half, outline.left.toInt() + reach, middle + half),
                Rect(outline.right.toInt() - reach, middle - half, outline.right.toInt() + reach, middle + half),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        placeOutline()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Everything outside the key area is dimmed hard, the keys lightly, so the outline reads as the thing to move
        canvas.drawRect(0f, 0f, w, outline.top.coerceAtLeast(0f), scrim)
        canvas.drawRect(0f, outline.bottom, w, h, scrim)
        canvas.drawRect(0f, outline.top, outline.left, outline.bottom, scrim)
        canvas.drawRect(outline.right, outline.top, w, outline.bottom, scrim)
        canvas.drawRect(outline.left, keysTop, outline.right, outline.bottom, dim)
        canvas.drawRect(outline.left, outline.top, outline.right, keysTop, barPaint)
        val corner = dp(8f)
        canvas.drawRoundRect(outline, corner, corner, outlinePaint)

        val keysMiddle = (keysTop + outline.bottom) / 2
        // Handles on the screen's edge are drawn just inside it
        val edge = dp(8f)
        pill(canvas, outline.centerX(), outline.top.coerceAtLeast(edge), dp(56f), dp(12f))
        pill(canvas, outline.left.coerceAtLeast(edge), keysMiddle, dp(12f), dp(56f))
        pill(canvas, outline.right.coerceAtMost(w - edge), keysMiddle, dp(12f), dp(56f))

        // The move handle: a circle with arrows in four directions
        val r = dp(24f)
        val cx = outline.centerX()
        val cy = keysMiddle
        canvas.drawCircle(cx, cy, r, handlePaint)
        canvas.drawCircle(cx, cy, r, handleEdge)
        val a = dp(5f)
        val reach = dp(15f)
        for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
            val tipX = cx + dx * reach
            val tipY = cy + dy * reach
            arrow.reset()
            arrow.moveTo(tipX, tipY)
            arrow.lineTo(tipX - dx * a + dy * a, tipY - dy * a + dx * a)
            arrow.lineTo(tipX - dx * a - dy * a, tipY - dy * a - dx * a)
            arrow.close()
            canvas.drawPath(arrow, arrowPaint)
        }
        canvas.drawRect(cx - dp(6f), cy - dp(1f), cx + dp(6f), cy + dp(1f), arrowPaint)
        canvas.drawRect(cx - dp(1f), cy - dp(6f), cx + dp(1f), cy + dp(6f), arrowPaint)

        // The hint goes in the suggestions' place, under Reset and Done
        val hint = "Drag an edge to resize, the middle to move"
        hintPaint.textSize = dp(13f)
        val room = outline.width() - dp(24f)
        val measured = hintPaint.measureText(hint)
        if (measured > room) hintPaint.textSize *= room / measured
        val toolbarBottom = outline.top + dp(44f)
        if (keysTop - toolbarBottom >= hintPaint.textSize) {
            canvas.drawText(hint, outline.centerX(), (toolbarBottom + keysTop) / 2 + hintPaint.textSize / 3, hintPaint)
        }
    }

    private fun pill(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        handleRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val radius = minOf(w, h) / 2
        canvas.drawRoundRect(handleRect, radius, radius, handlePaint)
        canvas.drawRoundRect(handleRect, radius, radius, handleEdge)
    }

    private fun handleAt(x: Float, y: Float): Handle? {
        val slop = dp(24f)
        if (y < outline.top - slop || y > outline.bottom + slop) return null
        if (y >= keysTop && abs(x - outline.left) <= slop) return Handle.LEFT
        if (y >= keysTop && abs(x - outline.right) <= slop) return Handle.RIGHT
        if (x < outline.left || x > outline.right) return null
        // The whole toolbar strip moves the top edge; the whole key area moves the keys
        return if (y < keysTop) Handle.TOP else Handle.MOVE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        Unicorn.safely("KeyAreaEditor.touch") {
            val screen = KeyArea.screen ?: return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val handle = handleAt(event.x, event.y) ?: return true
                    val start = shown()
                    drag = Drag(handle, event.rawX, event.rawY, start, start.leftPx(screen).toFloat(),
                        (start.leftPx(screen) + start.widthPx(screen)).toFloat())
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val d = drag ?: return true
                    candidate = dragged(d, event.rawX - d.x, event.rawY - d.y, screen)
                    placeOutline()
                    invalidate()
                    val now = SystemClock.uptimeMillis()
                    if (now - lastSave >= LIVE_LAYOUT_MS) {
                        lastSave = now
                        candidate?.let { KeyArea.saveCurrent(context, it) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val d = drag ?: return true
                    drag = null
                    val result = dragged(d, event.rawX - d.x, event.rawY - d.y, screen)
                    candidate = result
                    KeyArea.saveCurrent(context, result)
                }
                MotionEvent.ACTION_CANCEL -> {
                    drag = null
                    candidate?.let { KeyArea.saveCurrent(context, it) }
                }
            }
        }
        return true
    }

    private fun dragged(d: Drag, dx: Float, dy: Float, screen: KeyScreen): KeyAreaSize = when (d.handle) {
        Handle.TOP -> d.start.copy(heightScale = d.start.heightScale - dy / screen.defaultKeysHeightPx).clampedTo(screen)
        Handle.LEFT -> d.start.withLeftEdge(screen, d.startLeft + dx)
        Handle.RIGHT -> d.start.withRightEdge(screen, d.startRight + dx)
        Handle.MOVE -> {
            // A small wobble across the main direction doesn't count, so sliding sideways doesn't also lift the keys
            val sideways = if (abs(dx) < dp(WOBBLE_DP)) 0f else dx
            val up = if (abs(dy) < dp(WOBBLE_DP)) 0f else dy
            d.start.movedTo(screen, d.startLeft + sideways).copy(liftDp = d.start.liftDp - up / screen.density).clampedTo(screen)
        }
    }

    private companion object {
        /** How often the keys are laid out again while a handle is dragged. */
        const val LIVE_LAYOUT_MS = 120L
        const val WOBBLE_DP = 16f
    }
}
