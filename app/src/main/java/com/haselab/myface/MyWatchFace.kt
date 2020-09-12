package com.haselab.myface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.palette.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import android.os.BatteryManager
import android.util.Log
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

private const val TAG = "MyFaceX"

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 10f
private const val MINUTE_STROKE_WIDTH = 10f
private const val SECOND_TICK_STROKE_WIDTH = 10f
private const val CENTER_GAP_AND_CIRCLE_RADIUS = 10f

private const val SHADOW_RADIUS = 60f
private val DAY_OF_WEEK = arrayOf(
    "日", "月", "火", "水", "木", "金", "土"
)

private const val FONT_SIZE = 60f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredBatteryReceiver = false
        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandPinColor: Int = 0

        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        private lateinit var mHourPaint: Paint
        private lateinit var mMinutePaint: Paint
        private lateinit var mSecondPaint: Paint
        private lateinit var mTickAndCirclePaint: Paint
        private lateinit var mClockFontPaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mBattryLevel: Int = 0

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }
        private val mBatteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    mBattryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

                }
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)

            /* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.RED)
                    mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                    mWatchHandPinColor = it.getLightVibrantColor(Color.GRAY)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    updateWatchHandStyle()
                }
            }
        }

        private fun initializeWatchFace() {
            Log.v(TAG, "start initializeWatchFace")

            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE
            mWatchHandPinColor = Color.GRAY

            mWatchHandHighlightColor = Color.RED
            mWatchHandShadowColor = Color.BLACK

            mHourPaint = Paint().apply {
                color = mWatchHandPinColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                //                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mMinutePaint = Paint().apply {
                color = mWatchHandPinColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                //                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mSecondPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                //strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mTickAndCirclePaint = Paint().apply {
                color = mWatchHandPinColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
            mClockFontPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                // strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
            mClockFontPaint.setTextSize(FONT_SIZE)
            mClockFontPaint.setAntiAlias(true)
            mClockFontPaint.setTypeface(Typeface.DEFAULT_BOLD)
            mClockFontPaint.setTextAlign(Paint.Align.CENTER)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            Log.v(TAG, "start onTimeTick")

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }


        private fun updateWatchHandStyle() {
            Log.v(TAG, "start updateWatch")

            if (mAmbient) {
                mHourPaint.color = Color.GRAY
                mMinutePaint.color = Color.GRAY
                mSecondPaint.color = Color.GRAY
                mTickAndCirclePaint.color = Color.WHITE

                mHourPaint.isAntiAlias = false
                mMinutePaint.isAntiAlias = false
                mSecondPaint.isAntiAlias = false
                mTickAndCirclePaint.isAntiAlias = false

                mHourPaint.clearShadowLayer()
                mMinutePaint.clearShadowLayer()
                mSecondPaint.clearShadowLayer()
                mTickAndCirclePaint.clearShadowLayer()

            } else {
                mHourPaint.color = mWatchHandPinColor
                mMinutePaint.color = mWatchHandPinColor
                mSecondPaint.color = mWatchHandHighlightColor
                mTickAndCirclePaint.color = mWatchHandPinColor

                mHourPaint.isAntiAlias = true
                mMinutePaint.isAntiAlias = true
                mSecondPaint.isAntiAlias = true
                mTickAndCirclePaint.isAntiAlias = true

                mHourPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mMinutePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mSecondPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mTickAndCirclePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                mClockFontPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.90).toFloat()
            sMinuteHandLength = (mCenterX * 0.85).toFloat()
            sHourHandLength = (mCenterX * 0.65).toFloat()


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(
                        applicationContext,
                        mBattryLevel.toString() + "%",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
            invalidate()
        }

        private fun zeroPad(inp: String): String {
            var str = "0000" + inp
            var len = str.length
            return str.substring(len - 2, len)
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            val innerTickRadius = mCenterX - 10
            val outerTickRadius = mCenterX
            for (tickIndex in 0..11) {
                val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
                val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
                val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
                val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
                canvas.drawLine(
                    mCenterX + innerX, mCenterY + innerY,
                    mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint
                )

                var ti = tickIndex
                if (tickIndex == 0) {
                    ti = 12
                }
                if (ti == 12 || ti == 6) {
                    mClockFontPaint.setTextAlign(Paint.Align.CENTER)
                } else if (1 <= ti && ti <= 5) {
                    mClockFontPaint.setTextAlign(Paint.Align.RIGHT)
                } else if (7 <= ti && ti <= 11) {
                    mClockFontPaint.setTextAlign(Paint.Align.LEFT)
                }

                var ajustY = 0
                if (ti <= 2 || 10 <= ti) {
                    ajustY = (FONT_SIZE * 2f / 3f).toInt()
                } else if (ti == 3 || ti == 9) {
                    ajustY = (FONT_SIZE / 3f).toInt()
                }
                if (ti == 3 || ti == 6 || ti == 9 || ti == 12) {
                    mClockFontPaint.setTextSize(FONT_SIZE)
                } else {
                    mClockFontPaint.setTextSize(FONT_SIZE * 2f / 3f)
                }
                canvas.drawText(
                    ti.toString(),
                    mCenterX + (innerX) * 1.0f,
                    mCenterY + (innerY) * 1.0f + ajustY, mClockFontPaint
                );
            }
            mClockFontPaint.setTextSize(FONT_SIZE)

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds =
                mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */

            // drow hour line and circle
            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY,
                mCenterX,
                mCenterY - sHourHandLength,
                mHourPaint
            )
            canvas.rotate(-hoursRotation, mCenterX, mCenterY)

            // minus line and circle
            canvas.rotate(minutesRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY,
                mCenterX,
                mCenterY - sMinuteHandLength,
                mMinutePaint
            )
            canvas.rotate(-(minutesRotation), mCenterX, mCenterY)

            // seconds line & circle
            if (!mAmbient) {
                canvas.rotate(secondsRotation, mCenterX, mCenterY)
                canvas.drawLine(
                    mCenterX,
                    mCenterY,
                    mCenterX,
                    mCenterY - mSecondHandLength,
                    mSecondPaint
                )
                canvas.rotate(-secondsRotation, mCenterX, mCenterY)
            } else {
                // nothing
            }

            // write date
            mClockFontPaint.setTextAlign(Paint.Align.CENTER)
            mClockFontPaint.setTextSize((FONT_SIZE * 1 / 2f * 1.1f))
            mClockFontPaint.setShadowLayer(
                SHADOW_RADIUS, 0f, 0f, Color.BLACK
            )


            val date_str = mCalendar.get(Calendar.YEAR).toString() +
                    "/" +
                    zeroPad((mCalendar.get(Calendar.MONTH) + 1).toString()) +
                    "/" +
                    zeroPad(mCalendar.get(Calendar.DATE).toString()) +
                    "(" +
                    DAY_OF_WEEK[mCalendar.get(Calendar.DAY_OF_WEEK) - 1] +
                    ")"
            canvas.drawText(
                date_str,
                mCenterX,
                mCenterY - mClockFontPaint.getTextSize(),
                mClockFontPaint
            );

            mClockFontPaint.setTextSize(FONT_SIZE * 1.1f)
            mClockFontPaint.setShadowLayer(FONT_SIZE, 5f, 5f, Color.BLACK)
            var clock_str = ""

            // 24hour 表示にする
            val ampm = mCalendar.get(Calendar.AM_PM)
            val hour = mCalendar.get(Calendar.HOUR) + ampm * 12

            if (!mAmbient) {
                clock_str = zeroPad(hour.toString()) +
                        ":" +
                        zeroPad((mCalendar.get(Calendar.MINUTE).toString())) +
                        ":" +
                        zeroPad((mCalendar.get(Calendar.SECOND).toString()))
            } else {
                clock_str = zeroPad(hour.toString()) +
                        ":" +
                        zeroPad((mCalendar.get(Calendar.MINUTE).toString())) +
                        "     " // dummy str
            }
            canvas.drawText(
                clock_str, mCenterX, mCenterY + FONT_SIZE,
                mClockFontPaint
            );

            // battery
            mClockFontPaint.setTextSize((FONT_SIZE * 1 / 2f))
            mClockFontPaint.setShadowLayer(FONT_SIZE, 5f, 5f, Color.BLACK)

            val battery_percent = mBattryLevel
            val old_color = mClockFontPaint.color
            val battery_str = battery_percent.toString() + "%"
            if (battery_percent <= 40) {
                mClockFontPaint.color = Color.RED
            } else {
                mClockFontPaint.color = Color.WHITE
            }
            canvas.drawText(
                battery_str, mCenterX, mCenterY + FONT_SIZE * 2,
                mClockFontPaint
            )
            //restor
            mClockFontPaint.setTextSize(FONT_SIZE)
            mClockFontPaint.color = old_color

            /* Restore the canvas' original orientation. */
            canvas.restore()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                tzRegisterReceiver()
                bRegisterReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                untzRegisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun tzRegisterReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun bRegisterReceiver() {
            if (mRegisteredBatteryReceiver) {
                return
            }
            mRegisteredBatteryReceiver = true
            var intentFilter: IntentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
            this@MyWatchFace.registerReceiver(mBatteryReceiver, intentFilter)
        }

        private fun untzRegisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        private fun unBatteryRegisterReceiver() {
            Log.v(TAG, "start unBatteryRegisterReceiver")
            if (!mRegisteredBatteryReceiver) {
                return
            }
            mRegisteredBatteryReceiver = false
            this@MyWatchFace.unregisterReceiver(mBatteryReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}


