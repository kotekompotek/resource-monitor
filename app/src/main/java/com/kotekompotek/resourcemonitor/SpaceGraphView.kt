package com.kotekompotek.resourcemonitor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SpaceGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val dataPoints = mutableListOf<Double>()
    private val maxPoints = 600
    
    var isReversed: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Фиксированные границы. Если null — вычисляются автоматически (старое поведение).
    var minY: Double? = null
        set(value) {
            field = value
            invalidate()
        }
    var maxY: Double? = null
        set(value) {
            field = value
            invalidate()
        }

    fun addDataPoint(value: Double) {
        dataPoints.add(value)
        if (dataPoints.size > maxPoints) {
            dataPoints.removeAt(0)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) return

        val width = width.toFloat()
        val height = height.toFloat()
        
        val actualMin = dataPoints.minOrNull() ?: 0.0
        val actualMax = dataPoints.maxOrNull() ?: 1.0
        
        // Используем заданный минимум или вычисляем автоматически (по умолчанию)
        val min = minY ?: actualMin
        // Используем заданный максимум, но расширяем его, если данные вышли за предел.
        // Если maxY == null, берем фактический максимум (старое поведение).
        val max = Math.max(maxY ?: actualMax, actualMax).coerceAtLeast(min + 0.00001)
        
        val range = (max - min).coerceAtLeast(0.00001).toFloat()

        val path = Path()
        var prevX = 0f
        var prevY = 0f

        for (i in dataPoints.indices) {
            val x = i * width / (maxPoints - 1)
            val normalized = ((dataPoints[i] - min) / range).coerceIn(0.0, 1.0).toFloat()
            
            // Оригинальная формула: false - малые значения вверху (y=0)
            val y = if (isReversed) height - (normalized * height) else normalized * height
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                val midX = (prevX + x) / 2
                path.quadTo(prevX, prevY, midX, (prevY + y) / 2)
            }
            prevX = x
            prevY = y
        }
        path.lineTo(prevX, prevY)
        canvas.drawPath(path, paint)
    }
}
