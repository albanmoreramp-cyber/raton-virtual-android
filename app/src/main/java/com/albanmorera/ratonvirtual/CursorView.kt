package com.albanmorera.ratonvirtual

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * El cursor que se dibuja encima de todo: una flecha blanca con un punto de
 * color que indica el estado, y un texto corto al lado ("TOQUE", "PAUSA"...).
 * La PUNTA de la flecha está en (tipOffset, tipOffset) dentro de la vista.
 */
class CursorView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    val tipOffset = 3f * dp
    val viewW = (190 * dp).toInt()
    val viewH = (44 * dp).toInt()

    var state = CursorState.NO_HAND
        private set
    var label = ""
        private set

    private val arrow = Path().apply {
        val s = dp * 1.25f
        val o = tipOffset
        moveTo(o, o)
        lineTo(o, o + 18 * s)
        lineTo(o + 4.5f * s, o + 13.5f * s)
        lineTo(o + 7.5f * s, o + 20 * s)
        lineTo(o + 10.5f * s, o + 18.7f * s)
        lineTo(o + 7.6f * s, o + 12.4f * s)
        lineTo(o + 14 * s, o + 12.4f * s)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.BLACK
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * dp
        color = Color.WHITE
        isFakeBoldText = true
        setShadowLayer(3 * dp, 0f, 0f, Color.BLACK)
    }

    fun update(newState: CursorState, newLabel: String) {
        if (newState != state || newLabel != label) {
            state = newState
            label = newLabel
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (body, dotColor) = when (state) {
            CursorState.NO_HAND -> Pair(Color.argb(110, 255, 255, 255), Color.argb(110, 150, 150, 150))
            CursorState.MOVING -> Pair(Color.WHITE, Color.rgb(124, 58, 237))      // morado
            CursorState.SLOW -> Pair(Color.WHITE, Color.rgb(255, 160, 0))         // naranja: frenando
            CursorState.PINCH -> Pair(Color.rgb(200, 255, 200), Color.rgb(0, 200, 80))
            CursorState.SCROLL -> Pair(Color.WHITE, Color.rgb(0, 170, 255))
            CursorState.PAUSED -> Pair(Color.argb(150, 255, 255, 255), Color.rgb(255, 90, 0))
        }
        fill.color = body
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        dot.color = dotColor
        canvas.drawCircle(tipOffset + 20 * dp, tipOffset + 24 * dp, 5 * dp, dot)
        val shown = if (state == CursorState.PAUSED && label.isEmpty()) "PAUSA" else label
        if (shown.isNotEmpty()) {
            canvas.drawText(shown, tipOffset + 29 * dp, tipOffset + 29 * dp, text)
        }
    }
}
