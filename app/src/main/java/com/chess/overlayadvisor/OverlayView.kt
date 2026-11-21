package com.chess.overlayadvisor

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class MoveArrow(
    val fromCol: Int,
    val fromRow: Int,
    val toCol: Int,
    val toRow: Int,
    val score: Int,
    val isBest: Boolean
)

class OverlayView(context: Context) : View(context) {

    var boardRect = RectF(40f, 520f, 1040f, 1520f)
    var showCalibrationGrid = true
    var arrows = listOf<MoveArrow>()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8800E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DD4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
    }

    private val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA9E9E9E")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = boardRect.width() / 8f

        if (showCalibrationGrid) {
            canvas.drawRect(boardRect, gridPaint)
            for (i in 1..7) {
                canvas.drawLine(
                    boardRect.left + i * cell, boardRect.top,
                    boardRect.left + i * cell, boardRect.bottom, gridPaint
                )
                canvas.drawLine(
                    boardRect.left, boardRect.top + i * cell,
                    boardRect.right, boardRect.top + i * cell, gridPaint
                )
            }
        }

        // Gambar panah alternatif dulu, lalu panah terbaik di lapisan atas
        for (arrow in arrows.reversed()) {
            val sx = boardRect.left + (arrow.fromCol + 0.5f) * cell
            val sy = boardRect.top + (arrow.fromRow + 0.5f) * cell
            val ex = boardRect.left + (arrow.toCol + 0.5f) * cell
            val ey = boardRect.top + (arrow.toRow + 0.5f) * cell

            val paint = if (arrow.isBest) bestPaint else altPaint
            drawArrow(canvas, sx, sy, ex, ey, paint)

            badgePaint.color = if (arrow.isBest) Color.parseColor("#2E7D32") else Color.parseColor("#424242")
            val bx = boardRect.left + (arrow.fromCol + 0.76f) * cell
            val by = boardRect.top + (arrow.fromRow + 0.20f) * cell
            canvas.drawRoundRect(bx - 30f, by - 20f, bx + 30f, by + 14f, 8f, 8f, badgePaint)
            canvas.drawText(arrow.score.toString(), bx, by + 5f, textPaint)
        }
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLen = 34f
        val headAngle = Math.toRadians(28.0)

        val x3 = x2 - headLen * cos(angle - headAngle).toFloat()
        val y3 = y2 - headLen * sin(angle - headAngle).toFloat()
        val x4 = x2 - headLen * cos(angle + headAngle).toFloat()
        val y4 = y2 - headLen * sin(angle + headAngle).toFloat()

        canvas.drawLine(x2, y2, x3, y3, paint)
        canvas.drawLine(x2, y2, x4, y4, paint)
    }
}
