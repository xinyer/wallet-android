package com.mycelium.sporeui

import android.content.Context
import android.graphics.*
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitArray
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.properties.Delegates

class QRCodeView(context: Context, attrs: AttributeSet): View(context, attrs) {
    val paint = Paint()

    var dimension: Int by Delegates.observable(0) {
        prop, old, new ->
        setupMatrix()
    }

    var data: String by Delegates.observable("") {
        prop, old, new ->
        setupMatrix()
    }

    private var _backgroundColor = android.R.color.white
    override fun setBackgroundColor(color: Int) {
        _backgroundColor = color
    }

    val backgroundColor: Int
        get() = _backgroundColor

    init {
        paint.color = ContextCompat.getColor(context, android.R.color.black)
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.QRCodeView, 0, 0)
        try {
            dimension = a.getInteger(R.styleable.QRCodeView_dimension, 0)
        } finally {
            a.recycle()
        }
    }

    var bitMatrix: BitMatrix? = null
    lateinit var row: BitArray
    var writer = QRCodeWriter()

    fun setupMatrix() {
        if (dimension > 0 && data != "") {
            val hints = mapOf(EncodeHintType.MARGIN to 0)
            bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, dimension, dimension, hints)
            val bitMatrix = bitMatrix
            if (bitMatrix != null) {
                row = BitArray(bitMatrix.width)
            }
        }
    }

    val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(ContextCompat.getColor(context, backgroundColor))
        val bitMatrix = bitMatrix ?: return
        path.reset()
        val dimension = if (canvas.width < canvas.height) canvas.width else canvas.height
        val bitSize = dimension.toFloat() / this.dimension.toFloat()
        for (y in 0..bitMatrix.height-1) {
            val row = bitMatrix.getRow(y, this.row)
            for (x in 0..row.size-1) {
                val bit = row.get(x)
                if (bit) {
                    val left = bitSize.toFloat() * x.toFloat()
                    val top = bitSize.toFloat() * y.toFloat()
                    path.addRect(left, top, left + bitSize, top + bitSize, Path.Direction.CCW)
                }
            }
        }

        canvas.drawPath(path, paint)
    }
}