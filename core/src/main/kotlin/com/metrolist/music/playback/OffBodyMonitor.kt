/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

internal class OffBodyMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onBodyStateChanged: (isOffBody: Boolean) -> Unit,
    private val onTimeout: () -> Unit,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    private var timerJob: Job? = null
    private var isRegistered = false

    private val sensorEventListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val isOffBody = event.values.firstOrNull() != 1f
                onBodyStateChanged(isOffBody)
                if (isOffBody) startTimer() else stopTimer()
            }

            override fun onAccuracyChanged(
                sensor: Sensor,
                accuracy: Int,
            ) = Unit
        }

    fun startMonitoring(): Boolean {
        if (isRegistered) return true
        val sensor = offBodySensor ?: return false

        isRegistered =
            runCatching {
                sensorManager?.registerListener(
                    sensorEventListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                ) == true
            }.onFailure { error ->
                Timber.w(error, "Unable to register the off-body sensor")
            }.getOrDefault(false)
        return isRegistered
    }

    fun stopMonitoring() {
        if (isRegistered) sensorManager?.unregisterListener(sensorEventListener)
        isRegistered = false
        stopTimer()
        onBodyStateChanged(false)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob =
            scope.launch {
                delay(5.seconds)
                onTimeout()
            }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
