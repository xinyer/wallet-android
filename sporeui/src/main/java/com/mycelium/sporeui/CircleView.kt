package com.mycelium.sporeui

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import kotlin.properties.Delegates

class CircleView(context: Context, attrs: AttributeSet): View(context, attrs) {

    var color: Int by Delegates.observable(0) {
        prop, old, new ->
        invalidate()
    }

    var radius: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    var xOffset: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    var yOffset: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    init {

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.CircleView, 0, 0)
        try {
            color = a.getColor(R.styleable.CircleView_color,
                    R.color.colorPrimary)
            radius = a.getFloat(R.styleable.CircleView_radius, 0F)
            xOffset = a.getFloat(R.styleable.CircleView_xOffset, 0F)
            yOffset = a.getFloat(R.styleable.CircleView_yOffset, 0F)
        } finally {
            a.recycle()
        }
    }

    val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        path.reset()
        path.addCircle(canvas.width.toFloat() / 2, canvas.height.toFloat() / 2, radius, Path.Direction.CW)
        path.offset(xOffset, yOffset)
        canvas.clipPath(path, Region.Op.XOR)
        canvas.drawColor(color)
    }
}
