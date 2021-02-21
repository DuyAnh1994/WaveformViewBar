package com.dev.anhnd.waveformviewbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.core.net.toUri
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

class WaveformViewBar @JvmOverloads constructor(ctx: Context,
                                                attrs: AttributeSet? = null,
                                                defStyle: Int = 0) : View(ctx, attrs, defStyle) {
    companion object {
        private val TAG = WaveformViewBar::class.java.simpleName
    }
    //region properties
    /** ----- parent view ----- */
    private val rectParentView = RectF()
    private val paintParentView = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cornerParentView = 0f

    /** ----- frame view ----- */
    private val rectFrameView = RectF()
    private val paintFrameView = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cornerFrameView = 0f

    /** ----- waveform view ----- */
    private val rectWaveformView = RectF()
    private val paintWaveformView = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cornerWaveformView = 0f
    private var waveformViewCurrentWidth = 0f

    /** ----- amplitudes wave ----- */
    private val paintAmplitudes = Paint(Paint.ANTI_ALIAS_FLAG)

    //    private var amplitudes = arrayListOf<Float>()
    private var amplitudes = SampleRateData.listSampleRate
    private var ratioHeightAmplitudes = 0f
    private var widthAmplitudes = 5f

    /** ----- indicator view ----- */
    private val rectIndicatorView = RectF()
    private val paintIndicatorView = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cornerIndicatorView = 0f

    /** ----- progress ----- */
    private val rectProgressView = RectF()
    private val paintProgressView = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cornerProgressView = 0f
    private var progressWidth = 5f


    /** ----- interact ----- */
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scroller: Scroller
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pointDown = PointF(0f, 0f)
    private var isWaveformMoving = false
    private var touchAreaExtra = 0
    private var waveformAreaIndex = WaveformArea.NONE

    /** ----- media ----- */
    var duration = 100f
        set(value) {
            field = value
            maxProgress = value
            if (minProgress > maxProgress)
                minProgress = 0f
        }
    var progress = 0f
        private set
    var minProgress = 0f
        private set
    var maxProgress = 0f
        private set
    var minRangeCut = 0f
        private set
    var maxRangeCut = 0f
        private set
    //endregion

    //region lifecycle
    init {
        setupAttributes(attrs, defStyle)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        initParentView()
        initFrameView()
        initWaveformView()
        initAmplitudes()
        initIndicatorView()
        initInteract()
        initProgressView()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let { c ->
            c.drawRoundRect(rectParentView, cornerParentView, cornerParentView, paintParentView)
            c.drawRoundRect(rectFrameView, cornerFrameView, cornerFrameView, paintFrameView)
            c.drawRoundRect(rectWaveformView, cornerWaveformView, cornerWaveformView, paintWaveformView)
            drawAmplitudes(c)
            c.drawRoundRect(rectIndicatorView, cornerIndicatorView, cornerIndicatorView, paintIndicatorView)
            c.drawRoundRect(rectProgressView, cornerProgressView, cornerProgressView, paintProgressView)
            c.drawCircle(rectProgressView.centerX(), rectProgressView.top, 10f, paintProgressView)
            c.drawCircle(rectProgressView.centerX(), rectProgressView.bottom, 10f, paintProgressView)

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { ev ->
            val x = ev.x
            val y = ev.y
            if (gestureDetector.onTouchEvent(ev)) {
                return true
            }
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointDown.set(x, y)
                    waveformAreaIndex = getWaveformFocus()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val disMove = x - pointDown.x
                    if (isWaveformMoving) {
                        pointDown.x = x
                        moveWaveform(disMove)
                    } else {
                        return if (abs(disMove) >= touchSlop) {
                            isWaveformMoving = true
                            true
                        } else {
                            false
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    return true
                }
                else -> {
                    return true
                }
            }
        }
        return false
    }
    //endregion

    //region valid value
    private fun moveWaveform(distance: Float) {
        val disMove = distance.toInt()
        if (waveformAreaIndex == WaveformArea.INSIDE) {
            val min = rectProgressView.left - waveformViewCurrentWidth + progressWidth
            val max = rectProgressView.right - progressWidth
            adjustMove(rectWaveformView, disMove, min, max)
            adjustMove(rectIndicatorView, disMove, min, max)
        }
        invalidate()
    }

    private fun adjustMove(waveformRect: RectF, disMove: Int, min: Float, max: Float) {
        when {
            waveformRect.left + disMove < min -> {
                waveformRect.left = min.roundToLong().toFloat()
            }
            waveformRect.left + disMove > max -> {
                waveformRect.left = max.roundToLong().toFloat()
            }
            else -> {
                waveformRect.left += disMove
            }
        }
        waveformRect.right = waveformRect.left + waveformViewCurrentWidth
    }

    private fun getWaveformFocus(): WaveformArea {
        var isFocusInside = false
        if (pointDown.x in rectWaveformView.left - touchAreaExtra..rectWaveformView.right + touchAreaExtra
            || pointDown.y in rectWaveformView.top - touchAreaExtra..rectWaveformView.bottom + touchAreaExtra
        ) {
            isFocusInside = true
        }
        return when {
            isFocusInside -> {
                WaveformArea.INSIDE
            }
            !isFocusInside -> {
                WaveformArea.OUTSIDE
            }
            else -> {
                WaveformArea.NONE
            }
        }
    }

    private fun drawAmplitudes(canvas: Canvas) {
        val middle = rectWaveformView.centerY()
        var max = 0f
        for (idx in 0 until amplitudes.size) {
            var power = amplitudes[idx]
            if (power == 0f) {
                power = 1f
            }
            var scaledHeight = power * ratioHeightAmplitudes
            if (scaledHeight < 1f) {
                scaledHeight = 1f
            }
            if (max < scaledHeight) {
                max = scaledHeight
            }
            canvas.drawLine(
                idx * widthAmplitudes + rectWaveformView.left,
                middle + scaledHeight,
                idx * widthAmplitudes + rectWaveformView.left,
                middle - scaledHeight,
                paintAmplitudes)
        }
    }

    private fun getDuration(path: String): Float {
        return File(path).getMediaDuration(context).toFloat()
    }

    private fun getNumberSample(number: Int, array: ArrayList<Float>): ArrayList<Float> {
        val result = arrayListOf<Float>()
        for (i in 0 until number) {
            result.add(array[array.size / number * i])
        }
        return result
    }

    private fun readAudio(path: String): ArrayList<Float> {
        var waveOut = arrayListOf<Float>()
        val inputStream = context.contentResolver.openInputStream(File(path).toUri())
        try {
            var read: Int
            inputStream?.let { stream ->
                val bytesTemp = ByteArray(44)
                read = stream.read(bytesTemp, 0, bytesTemp.size)
                val bytes = ByteArray(2)
                var longTemp: Long
                while (read != -1) {
                    read = stream.read(bytes, 0, bytes.size)
                    longTemp = ConvertBytesToInt.getLE2(bytes)
                    if (waveOut.size >= 10000) {
                        break
                    }
                    waveOut.add(longTemp.toFloat())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return arrayListOf()
        } finally {
            inputStream?.close()
        }
        waveOut = SampleRateData.listSampleRate
        return waveOut
    }
    //endregion

    //region feature
    fun setAudioPath(path: String?) {
        path?.let { p ->
            duration = getDuration(p)
//            amplitudes = getNumberSample((rectWaveformView.width() / widthAmplitudes).toInt(), readAudio(p))

//            if (!validSampleData(amplitudes)) {
//                amplitudes = getNumberSample((rectWaveFormView.width() / widthAmplitudes).toInt(), SampleRateData.listSampleRate)
//            }
//            amplitudes = getNumberSample((rectWaveformView.width() / widthAmplitudes).toInt(), SampleRateData.listSampleRate)
//            val max = amplitudes.maxOrNull() ?: 0
//            ratioHeightAmplitudes = rectWaveformView.height() / (2 * max.toFloat())

//            amplitudes = SampleRateData.listSampleRate
//            val max = amplitudes.maxOrNull() ?: 0
//            ratioHeightAmplitudes = rectWaveformView.height() / (2 * max.toFloat())
//            Log.d(TAG, "setAudioPath: ${amplitudes.size}")
//            waveformViewCurrentWidth = amplitudes.size
//            Log.d(TAG, "setAudioPath: ${waveformViewCurrentWidth}")
        } ?: run {
            Log.e(TAG, "path null")
        }
        postInvalidate()
    }
    //endregion

    //region init view
    private fun initParentView() {
        paintParentView.apply {
            style = Paint.Style.FILL
        }
        val parentViewLeft = 0f
        val parentViewTop = 0f
        val parentViewRight = width.toFloat()
        val parentViewBottom = parentViewTop + 500f
        rectParentView.set(parentViewLeft, parentViewTop, parentViewRight, parentViewBottom)
    }

    private fun initFrameView() {
        paintFrameView.apply {
            style = Paint.Style.FILL
        }
        val frameLeft = rectParentView.left
        val frameTop = rectParentView.top + 50f
        val frameRight = rectParentView.right
        val frameBottom = rectParentView.bottom - 100f
        rectFrameView.set(frameLeft, frameTop, frameRight, frameBottom)
    }

    private fun initWaveformView() {
        waveformViewCurrentWidth = (widthAmplitudes * amplitudes.size)
        paintWaveformView.apply {
            style = Paint.Style.FILL
        }
        val waveformViewLeft = rectFrameView.width() / 2f
        val waveformViewTop = rectParentView.top + 50f
        val waveformViewRight = waveformViewLeft + waveformViewCurrentWidth
        val waveformViewBottom = rectParentView.bottom - 100f
        rectWaveformView.set(waveformViewLeft, waveformViewTop, waveformViewRight, waveformViewBottom)
    }

    private fun initAmplitudes() {
        paintAmplitudes.apply {
            color = Color.parseColor("#18191F")
            strokeWidth = 1f
        }

        val max = amplitudes.max() ?: 0f
        ratioHeightAmplitudes = rectWaveformView.height() / (2 * max)
    }

    private fun initIndicatorView() {
        paintIndicatorView.apply {
            style = Paint.Style.FILL
        }
        val indicatorViewLeft = rectWaveformView.left
        val indicatorViewTop = rectWaveformView.bottom
        val indicatorViewRight = rectWaveformView.right
        val indicatorViewBottom = indicatorViewTop + 50f
        rectIndicatorView.set(indicatorViewLeft, indicatorViewTop, indicatorViewRight, indicatorViewBottom)
    }

    private fun initProgressView() {
        paintProgressView.apply {
            style = Paint.Style.FILL
        }
        val progressViewLeft = rectFrameView.width() / 2f
        val progressViewTop = rectFrameView.top - 20f
        val progressViewRight = progressViewLeft + progressWidth
        val progressViewBottom = rectFrameView.bottom + 20f
        rectProgressView.set(progressViewLeft, progressViewTop, progressViewRight, progressViewBottom)
    }

    private fun initInteract() {
        scroller = Scroller(context, LinearInterpolator())
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                val minX = 0
                val maxX = waveformViewCurrentWidth.toInt() - width


                scroller.fling(scrollX, scrollY, (-velocityX).toInt(), 0, minX, maxX, scrollY, scrollY)
                return true
            }
        })
    }

    private fun setupAttributes(attrs: AttributeSet?, defStyle: Int) {
        val ta = context.theme.obtainStyledAttributes(attrs, R.styleable.WaveformViewBar, defStyle, 0)
        // parent
        paintParentView.color = ta.getColor(R.styleable.WaveformViewBar_wfv_parent_color, Color.parseColor("#2196F3"))
        cornerParentView = ta.getFloat(R.styleable.WaveformViewBar_wfv_parent_corner, 0f)

        // frame
        paintFrameView.color = ta.getColor(R.styleable.WaveformViewBar_wfv_frame_color, Color.parseColor("#F6F6FB"))
        cornerFrameView = ta.getDimension(R.styleable.WaveformViewBar_wfv_frame_corner, 0f)

        // waveform
        paintWaveformView.color = ta.getColor(R.styleable.WaveformViewBar_wfv_waveform_color, Color.parseColor("#6FD573"))
        cornerWaveformView = ta.getDimension(R.styleable.WaveformViewBar_wfv_waveform_corner, 0f)

        // indicator
        paintIndicatorView.color = ta.getColor(R.styleable.WaveformViewBar_wfv_indicator_color, Color.parseColor("#F6B14B"))
        cornerIndicatorView = ta.getDimension(R.styleable.WaveformViewBar_wfv_indicator_corner, 0f)

        // indicator
        paintProgressView.color = ta.getColor(R.styleable.WaveformViewBar_wfv_progress_color, Color.parseColor("#F95A2B"))
        cornerProgressView = ta.getDimension(R.styleable.WaveformViewBar_wfv_progress_corner, 0f)


        // interact
        touchAreaExtra = ta.getInt(R.styleable.WaveformViewBar_wfv_touch_area, 0)

        ta.recycle()
    }
    //endregion
}
