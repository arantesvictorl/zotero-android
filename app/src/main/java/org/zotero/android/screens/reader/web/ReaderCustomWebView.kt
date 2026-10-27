package org.zotero.android.screens.reader.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ActionMode
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

class ReaderCustomWebView(context: Context, attrs: AttributeSet? = null) : WebView(context, attrs) {

    var selectionActionModeDelegate: ReaderSelectionActionModeDelegate? = null

    private var activeSelectionActionMode: ActionMode? = null

    var onUserGestureDetected: (() -> Unit)? = null
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureAlreadyReportedThisTouch = false
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    /** When armed (toolbar button), the next drag draws an area selection with any pointer. */
    var areaSelectionArmed = false
    var onAreaSelected: ((RectF) -> Unit)? = null

    private var areaGestureActive = false
    private var areaStartX = 0f
    private var areaStartY = 0f
    private val areaRect = RectF()
    private val density = resources.displayMetrics.density
    private val areaFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0x332F80ED
    }
    private val areaStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF2F80ED.toInt()
        strokeWidth = 2 * density
        pathEffect = DashPathEffect(floatArrayOf(8 * density, 5 * density), 0f)
    }

    private fun isStylusButtonPressed(ev: MotionEvent): Boolean {
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }
        // Depending on the device, the S Pen side button is reported as stylus primary or secondary button.
        val buttons = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
        return ev.buttonState and buttons != 0
    }

    /**
     * Area selection: a rectangle drawn with the S Pen side button held (or after arming it from the
     * toolbar). The gesture is consumed here so WebView doesn't scroll or start a native selection.
     */
    private fun handleAreaSelection(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!areaSelectionArmed && !isStylusButtonPressed(ev)) {
                    return false
                }
                areaGestureActive = true
                areaStartX = ev.x
                areaStartY = ev.y
                areaRect.set(ev.x, ev.y, ev.x, ev.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!areaGestureActive) {
                    return false
                }
                areaRect.set(
                    minOf(areaStartX, ev.x),
                    minOf(areaStartY, ev.y),
                    maxOf(areaStartX, ev.x),
                    maxOf(areaStartY, ev.y),
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!areaGestureActive) {
                    return false
                }
                areaGestureActive = false
                val rect = RectF(areaRect)
                areaRect.setEmpty()
                invalidate()
                if (rect.width() > 12 * density && rect.height() > 8 * density) {
                    onAreaSelected?.invoke(rect)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!areaGestureActive) {
                    return false
                }
                areaGestureActive = false
                areaRect.setEmpty()
                invalidate()
                return true
            }
        }
        return areaGestureActive
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (areaGestureActive && !areaRect.isEmpty) {
            canvas.save()
            canvas.translate(scrollX.toFloat(), scrollY.toFloat())
            canvas.drawRect(areaRect, areaFillPaint)
            canvas.drawRect(areaRect, areaStrokePaint)
            canvas.restore()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (handleAreaSelection(ev)) {
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureDownX = ev.x
                gestureDownY = ev.y
                gestureAlreadyReportedThisTouch = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!gestureAlreadyReportedThisTouch) {
                    gestureAlreadyReportedThisTouch = true
                    onUserGestureDetected?.invoke()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureAlreadyReportedThisTouch &&
                    (abs(ev.x - gestureDownX) > touchSlop || abs(ev.y - gestureDownY) > touchSlop)
                ) {
                    gestureAlreadyReportedThisTouch = true
                    onUserGestureDetected?.invoke()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun invalidateSelectionActionMode() {
        activeSelectionActionMode?.invalidate()
    }

    override fun startActionModeForChild(
        originalView: View,
        callback: ActionMode.Callback
    ): ActionMode {
        return super.startActionModeForChild(originalView, callback)
    }

    override fun startActionModeForChild(
        originalView: View,
        callback: ActionMode.Callback,
        type: Int
    ): ActionMode {
        return super.startActionModeForChild(originalView, callback, type)
    }

    public override fun startActionMode(callback: ActionMode.Callback): ActionMode {
        val actionMode = startActionModeForChild(this, wrapSelectionCallback(callback))
        activeSelectionActionMode = actionMode
        return actionMode
    }

    public override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode {
        val actionMode = startActionModeForChild(this, wrapSelectionCallback(callback), type)
        activeSelectionActionMode = actionMode
        return actionMode
    }

    private fun wrapSelectionCallback(callback: ActionMode.Callback): ActionMode.Callback {
        return ReaderWevViewSelectActionModeCallback(
            context = context,
            original = callback,
            delegate = { selectionActionModeDelegate },
            onDestroyed = { activeSelectionActionMode = null }
        )
    }

    override fun showContextMenu(): Boolean {
        return super.showContextMenu()
    }
}