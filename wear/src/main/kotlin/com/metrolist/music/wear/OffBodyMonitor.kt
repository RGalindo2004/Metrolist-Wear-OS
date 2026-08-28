/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

class OffBodyMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onTimeout: () -> Unit,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    private val measureClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            HealthServices.getClient(context).measureClient
        } catch (e: Exception) {
            null
        }
    } else null

    private var timerJob: Job? = null
    private var isRegistered = false

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val isOnBody = event.values[0] == 1.0f
            Timber.d("OffBodyMonitor: Sensor changed, isOnBody=$isOnBody")
            if (isOnBody) stopTimer() else startTimer()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val healthCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                if (availability is DataTypeAvailability) {
                    Timber.d("OffBodyMonitor: Availability changed to $availability")
                    when (availability) {
                        DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY,
                        DataTypeAvailability.UNAVAILABLE -> startTimer()
                        DataTypeAvailability.AVAILABLE -> stopTimer()
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {}
        }
    } else null

    fun startMonitoring() {
        if (isRegistered) return
        Timber.d("OffBodyMonitor: Starting monitoring")
        
        // Try Health Services first on supported devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && measureClient != null && healthCallback != null) {
            try {
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, healthCallback)
                isRegistered = true
                Timber.d("OffBodyMonitor: Using Health Services")
                return
            } catch (e: Exception) {
                Timber.e(e, "OffBodyMonitor: Health Services failed, falling back to SensorManager")
            }
        }

        // Fallback to SensorManager
        if (offBodySensor != null) {
            sensorManager?.registerListener(sensorEventListener, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL)
            isRegistered = true
            Timber.d("OffBodyMonitor: Using SensorManager")
        } else {
            Timber.w("OffBodyMonitor: No off-body sensor available")
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        Timber.d("OffBodyMonitor: Stopping monitoring")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && measureClient != null && healthCallback != null) {
            try {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, healthCallback)
            } catch (e: Exception) {
                Timber.e(e, "OffBodyMonitor: Failed to unregister Health Services callback")
            }
        }
        
        sensorManager?.unregisterListener(sensorEventListener)
        stopTimer()
        isRegistered = false
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        Timber.d("OffBodyMonitor: Starting 1-minute timer")
        timerJob = scope.launch {
            delay(1.minutes)
            Timber.d("OffBodyMonitor: Timer expired, triggering timeout")
            onTimeout()
        }
    }

    private fun stopTimer() {
        Timber.d("OffBodyMonitor: Stopping timer")
        timerJob?.cancel()
        timerJob = null
    }
}
