package com.example.sondenit.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.example.sondenit.data.SessionEvent
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Converts raw accelerometer samples into sparse movement bursts.
 *
 * The service persists only these debounced events, not the sample stream.
 */
class AccelerometerMotionMonitor(
    context: Context,
    private val onMovement: (SessionEvent.Motion) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lock = Any()

    private var handlerThread: HandlerThread? = null
    private var running = false
    private var paused = false

    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var lastUnit: Vector3? = null
    private var restUnit: Vector3? = null
    private var quietSince: Long? = null

    private var active = false
    private var startTimestamp = 0L
    private var lastMotionTimestamp = 0L
    private var peakAcceleration = 0f
    private var accelerationSum = 0f
    private var sampleCount = 0
    private var maxOrientationChangeDeg = 0f
    private var wakeCandidate = false

    fun start(): Boolean {
        val sensor = accelerometer ?: return false
        synchronized(lock) {
            if (running) return true
            resetTrackingLocked()
        }

        val thread = HandlerThread("AccelerometerMotionMonitor").apply { start() }
        val ok = sensorManager.registerListener(
            this,
            sensor,
            SAMPLING_PERIOD_US,
            Handler(thread.looper),
        )
        if (!ok) {
            thread.quitSafely()
            return false
        }
        handlerThread = thread
        synchronized(lock) {
            running = true
            paused = false
        }
        return true
    }

    fun setPaused(value: Boolean) {
        val finished = synchronized(lock) {
            if (paused == value) {
                null
            } else {
                paused = value
                if (value) finishActiveLocked(System.currentTimeMillis(), force = true)
                else {
                    resetTrackingLocked()
                    null
                }
            }
        }
        finished?.let(onMovement)
    }

    fun stop() {
        val finished = synchronized(lock) {
            if (!running) {
                null
            } else {
                running = false
                finishActiveLocked(System.currentTimeMillis(), force = true)
            }
        }
        sensorManager.unregisterListener(this)
        handlerThread?.quitSafely()
        handlerThread = null
        finished?.let(onMovement)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        val finished = synchronized(lock) {
            if (!running || paused) null
            else processSampleLocked(event.values[0], event.values[1], event.values[2], now)
        }
        finished?.let(onMovement)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processSampleLocked(x: Float, y: Float, z: Float, now: Long): SessionEvent.Motion? {
        if (!hasGravity) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            normalize(x, y, z)?.let {
                lastUnit = it
                restUnit = it
            }
            hasGravity = true
            quietSince = now
            return null
        }

        gravity[0] = GRAVITY_ALPHA * gravity[0] + (1f - GRAVITY_ALPHA) * x
        gravity[1] = GRAVITY_ALPHA * gravity[1] + (1f - GRAVITY_ALPHA) * y
        gravity[2] = GRAVITY_ALPHA * gravity[2] + (1f - GRAVITY_ALPHA) * z

        val linearX = x - gravity[0]
        val linearY = y - gravity[1]
        val linearZ = z - gravity[2]
        val linearMag = magnitude(linearX, linearY, linearZ)
        val unit = normalize(gravity[0], gravity[1], gravity[2]) ?: lastUnit
        val instantAngle = unit?.let { current ->
            lastUnit?.let { previous -> angleDegrees(previous, current) } ?: 0f
        } ?: 0f
        val restAngle = unit?.let { current ->
            restUnit?.let { rest -> angleDegrees(rest, current) } ?: 0f
        } ?: 0f
        if (unit != null) lastUnit = unit

        val quiet = linearMag < QUIET_ACCELERATION && instantAngle < QUIET_ORIENTATION_DEG
        if (!active && quiet) {
            val since = quietSince ?: now.also { quietSince = it }
            if (now - since >= REST_RESET_MS) {
                restUnit = unit
                quietSince = now
            }
        } else if (!quiet) {
            quietSince = null
        }

        val startsMovement = linearMag >= START_ACCELERATION ||
            instantAngle >= START_ORIENTATION_DEG ||
            restAngle >= START_REST_ORIENTATION_DEG
        val continuesMovement = linearMag >= CONTINUE_ACCELERATION ||
            instantAngle >= CONTINUE_ORIENTATION_DEG

        if (!active && startsMovement) {
            active = true
            startTimestamp = now
            lastMotionTimestamp = now
            peakAcceleration = linearMag
            accelerationSum = linearMag
            sampleCount = 1
            maxOrientationChangeDeg = restAngle
            wakeCandidate = isWakeMovement(linearMag, instantAngle, restAngle)
            return null
        }

        if (!active) return null

        peakAcceleration = max(peakAcceleration, linearMag)
        accelerationSum += linearMag
        sampleCount += 1
        maxOrientationChangeDeg = max(maxOrientationChangeDeg, restAngle)
        wakeCandidate = wakeCandidate || isWakeMovement(linearMag, instantAngle, restAngle)

        if (continuesMovement) {
            lastMotionTimestamp = now
        }

        if (now - startTimestamp >= MAX_EVENT_MS) {
            return finishActiveLocked(now, force = true)
        }
        if (!continuesMovement && now - lastMotionTimestamp >= QUIET_GAP_MS) {
            return finishActiveLocked(lastMotionTimestamp, force = false)
        }
        return null
    }

    private fun finishActiveLocked(endTimestamp: Long, force: Boolean): SessionEvent.Motion? {
        if (!active) return null
        val durationMs = (endTimestamp - startTimestamp).coerceAtLeast(0L)
        val avgAcceleration = if (sampleCount > 0) accelerationSum / sampleCount else 0f
        val event = if (
            force ||
            durationMs >= MIN_EVENT_MS ||
            peakAcceleration >= MIN_PEAK_ACCELERATION ||
            wakeCandidate
        ) {
            SessionEvent.Motion(
                timestamp = startTimestamp,
                durationMs = durationMs,
                peakAcceleration = peakAcceleration,
                avgAcceleration = avgAcceleration,
                orientationChangeDeg = maxOrientationChangeDeg,
                wakeEvent = wakeCandidate,
            )
        } else {
            null
        }

        active = false
        startTimestamp = 0L
        lastMotionTimestamp = 0L
        peakAcceleration = 0f
        accelerationSum = 0f
        sampleCount = 0
        maxOrientationChangeDeg = 0f
        wakeCandidate = false
        restUnit = lastUnit
        quietSince = endTimestamp
        return event
    }

    private fun resetTrackingLocked() {
        hasGravity = false
        lastUnit = null
        restUnit = null
        quietSince = null
        active = false
        startTimestamp = 0L
        lastMotionTimestamp = 0L
        peakAcceleration = 0f
        accelerationSum = 0f
        sampleCount = 0
        maxOrientationChangeDeg = 0f
        wakeCandidate = false
    }

    private fun isWakeMovement(
        linearMag: Float,
        instantAngle: Float,
        restAngle: Float,
    ): Boolean = linearMag >= WAKE_ACCELERATION ||
        instantAngle >= WAKE_INSTANT_ORIENTATION_DEG ||
        restAngle >= WAKE_REST_ORIENTATION_DEG

    private companion object {
        private const val SAMPLING_PERIOD_US = 50_000
        private const val GRAVITY_ALPHA = 0.88f

        private const val START_ACCELERATION = 0.16f
        private const val CONTINUE_ACCELERATION = 0.08f
        private const val QUIET_ACCELERATION = 0.05f
        private const val MIN_PEAK_ACCELERATION = 0.22f

        private const val START_ORIENTATION_DEG = 3.0f
        private const val CONTINUE_ORIENTATION_DEG = 1.2f
        private const val QUIET_ORIENTATION_DEG = 0.45f
        private const val START_REST_ORIENTATION_DEG = 7.0f

        private const val WAKE_ACCELERATION = 1.20f
        private const val WAKE_INSTANT_ORIENTATION_DEG = 8.0f
        private const val WAKE_REST_ORIENTATION_DEG = 18.0f

        private const val MIN_EVENT_MS = 180L
        private const val QUIET_GAP_MS = 1_200L
        private const val REST_RESET_MS = 1_800L
        private const val MAX_EVENT_MS = 30_000L
    }
}

private data class Vector3(val x: Float, val y: Float, val z: Float)

private fun magnitude(x: Float, y: Float, z: Float): Float =
    sqrt(x * x + y * y + z * z)

private fun normalize(x: Float, y: Float, z: Float): Vector3? {
    val mag = magnitude(x, y, z)
    if (mag <= 0.0001f) return null
    return Vector3(x / mag, y / mag, z / mag)
}

private fun angleDegrees(a: Vector3, b: Vector3): Float {
    val dot = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1f, 1f)
    return (acos(dot) * 180.0 / Math.PI).toFloat()
}
