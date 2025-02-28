package com.idunnololz.summit.util.colorPicker.view

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.idunnololz.summit.util.Utils

/**
 * AlphaTileDrawable visualizes ARGB color on the [ ].
 */
class AlphaTileDrawable : Drawable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tileSize: Int = 25
    private var tileOddColor: Int = -0x1
    private var tileEvenColor: Int = -0x343435
    var gradientColor: Int? = null

    constructor() : super() {
        drawTiles()
    }

    constructor(gradientColor: Int?) : super() {
        this.gradientColor = gradientColor
        drawTiles()
    }

    private fun drawTiles() {
        val bitmap = Bitmap.createBitmap(
            tileSize * 2, tileSize * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = Rect(0, 0, tileSize, tileSize)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bitmapPaint.style = Paint.Style.FILL

        bitmapPaint.color = tileOddColor
        drawTile(canvas, rect, bitmapPaint, 0, 0)
        drawTile(canvas, rect, bitmapPaint, tileSize, tileSize)

        bitmapPaint.color = tileEvenColor
        drawTile(canvas, rect, bitmapPaint, -tileSize, 0)
        drawTile(canvas, rect, bitmapPaint, tileSize, -tileSize)

        paint.setShader(
            BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        )
    }

    private fun drawTile(canvas: Canvas, rect: Rect, bitmapPaint: Paint, dx: Int, dy: Int) {
        rect.offset(dx, dy)
        canvas.drawRect(rect, bitmapPaint)
    }

    override fun draw(canvas: Canvas) {
        val height = Utils.convertDpToPixel(16f)
        val width = bounds.width()
        val b = Rect(
            (height).toInt(),
            (bounds.centerY() - height / 2).toInt(),
            (width - height).toInt(),
            (bounds.centerY() + height / 2).toInt()
        )
        canvas.save()
        canvas.clipRect(b)
        canvas.drawRect(b, paint)

        val gradientColor = gradientColor
        if (gradientColor != null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(gradientColor, hsv)
            val startColor = Color.HSVToColor(0, hsv)
            val endColor = Color.HSVToColor(255, hsv)
            val shader: Shader =
                LinearGradient(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    startColor,
                    endColor,
                    Shader.TileMode.CLAMP
                )
            gradientPaint.setShader(shader)
            canvas.drawRect(b, gradientPaint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }
}