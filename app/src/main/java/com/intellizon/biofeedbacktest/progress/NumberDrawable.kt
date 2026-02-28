package com.intellizon.biofeedbacktest.progress

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.SeekBar
import com.intellizon.biofeedbacktest.R

import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

/**
 * Classname: NumberDrawable </p>
 * Created by Lenovo on 2024/12/3.
 */
class NumberDrawable : Drawable(), SeekBar.OnSeekBarChangeListener {

    private fun obtainAttributes(
        res: Resources,
        theme: Resources.Theme?, set: AttributeSet, attrs: IntArray
    ): TypedArray {
        return theme?.obtainStyledAttributes(set, attrs, 0, 0) ?: res.obtainAttributes(set, attrs)
    }

    private var textSize: Float = 60f
    private var color: Int = Color.DKGRAY
    private var bgColor: Int = Color.WHITE
    private var bgColor1: Int = Color.TRANSPARENT
    private var bgColor2: Int = Color.TRANSPARENT

    override fun inflate(
        r: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Resources.Theme?
    ) {
        super.inflate(r, parser, attrs, theme)
        val a: TypedArray = obtainAttributes(r, theme, attrs, R.styleable.number_drawable)

        textSize = a.getDimension(R.styleable.number_drawable_android_textSize, 60f)
        color = a.getColor(R.styleable.number_drawable_android_color, Color.DKGRAY)
        bgColor = a.getColor(R.styleable.number_drawable_bgColor, Color.WHITE)
        bgColor1 = a.getColor(R.styleable.number_drawable_bgColor1, Color.TRANSPARENT)
        bgColor2 = a.getColor(R.styleable.number_drawable_bgColor2, Color.TRANSPARENT)
        width = a.getDimension(R.styleable.number_drawable_width, 60f)
        height = a.getDimension(R.styleable.number_drawable_height, 60f)

        a.recycle()

        paint.color = color
        paint.textSize = textSize
    }

    private var currentValue: String = "0"
    private var currentLeftOffset = 0f
    private var width: Float = 0f
    private var height: Float = 0f

    private val textRect = Rect()

    private val paint = Paint().apply {
        textSize = 60f
        color = Color.CYAN
    }

    override fun getIntrinsicHeight(): Int {
        return height.toInt()
    }

    override fun getIntrinsicWidth(): Int {
        return width.toInt()
    }


    override fun draw(canvas: Canvas) {
        paint.isAntiAlias = true

        if (state == 1) {
            paint.color = bgColor1
            canvas.drawRect(currentLeftOffset + width / 2f, 0f, currentLeftOffset + width, height, paint)

            paint.color = bgColor2
            canvas.drawRect(currentLeftOffset + width, 0f, currentLeftOffset + width * 1.5f, height, paint)
        }

        paint.color = bgColor
        canvas.drawOval(currentLeftOffset + width / 2f, 0f, currentLeftOffset + width * 1.5f, height, paint)

        val number = currentValue

        paint.getTextBounds(number, 0, number.length, textRect)

        val paddingLeft = (width - textRect.width().toFloat()) / 2f
        val paddingTop = (height - textRect.height()) / 2f
        paint.color = color
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(number, paddingLeft + currentLeftOffset + width / 2f, paddingTop + textRect.height(), paint)

    }

    override fun setAlpha(alpha: Int) {
        //ignore
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        //ignore
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        height = height.coerceAtLeast(bounds.height().toFloat())
        width = width.coerceAtLeast(bounds.width().toFloat())
        invalidateSelf()
    }



    private var state = 0

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        try {
            val max = seekBar.max
            val length = seekBar.width - width
            if (length < 0) {
                seekBar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        seekBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        handleInvalidateSelf(seekBar, seekBar.width - width, progress, max)
                    }
                })
            } else {
                handleInvalidateSelf(seekBar, length, progress, max)
            }
        } catch (e: Exception) {
            Timber.v(e)
        }
    }

    private fun handleInvalidateSelf(seekBar: SeekBar, length: Float, progress: Int, max: Int) {
        currentLeftOffset = length * (progress) / (max).toFloat()
        state = when (progress) {
            0 -> 0
            max -> 2
            else -> 1
        }

        currentValue = if (seekBar is RyCompactSeekbar) {
            seekBar.formatter.format((progress + seekBar.minCompact) * seekBar.step)
        } else {
            progress.toString()
        }
        Log.v(
            "NumberDrawable",
            "${hashCode()} currentValue:$currentValue ; currentLeftOffset:$currentLeftOffset, max:$max,length $length"
        )
        invalidateSelf()
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

}