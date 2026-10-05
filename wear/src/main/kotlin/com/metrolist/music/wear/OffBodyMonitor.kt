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
import kotlin.time.Duration.Companion.seconds

class OffBodyMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onBodyStateChanged: (Boolean) -> Unit,
    private val onTimeout: () -> Unit,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    // El Galaxy Watch 7 con Wear OS 5 responde de forma mucho más confiable a los sensores de tipo hardware alternativo como el de proximidad o el acelerómetro
    // si la app está en segundo plano y el sistema operativo enmascara el sensor primario TYPE_LOW_LATENCY_OFFBODY_DETECT por políticas de Samsung.
    // Probamos primero obtener el sensor offbody, pero si One UI lo bloquea en segundo plano, usaremos un fallback.
    private val offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

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
            // En sensores TYPE_PROXIMITY: 0 significa "Cerca" (en la muñeca), y valores altos significan "Lejos" (fuera del cuerpo)
            // En sensores TYPE_LOW_LATENCY_OFFBODY_DETECT: > 0.5f significa "En la muñeca"
            val isOnBody = if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                event.values[0] < 1.0f // Cerca = en la muñeca
            } else {
                event.values[0] > 0.5f // 1.0 = en la muñeca
            }
            Timber.d("OffBodyMonitor: Sensor changed type=${event.sensor.type}, values=${event.values.joinToString()}, isOnBody=$isOnBody")
            onBodyStateChanged(isOnBody)
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
                        DataTypeAvailability.UNAVAILABLE -> {
                            onBodyStateChanged(false)
                            startTimer()
                        }
                        DataTypeAvailability.AVAILABLE -> {
                            onBodyStateChanged(true)
                            stopTimer()
                        }
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {}
        }
    } else null

    fun startMonitoring() {
        if (isRegistered) return
        Timber.d("OffBodyMonitor: Starting monitoring")
        
        // En Galaxy Watch 7 y Wear OS 4+, priorizamos SensorManager directo para TYPE_LOW_LATENCY_OFFBODY_DETECT,
        // ya que Health Services está diseñado para mediciones activas de ejercicio/salud (BPM) y duerme las lecturas pasivas.
        if (offBodySensor != null) {
            sensorManager?.registerListener(sensorEventListener, offBodySensor, SensorManager.SENSOR_DELAY_FASTEST)
            isRegistered = true
            Timber.d("OffBodyMonitor: Using SensorManager hardware directly")
            // Asumimos inicialmente el estado para forzar la sincronización rápida
            onBodyStateChanged(false)
            startTimer()
            return
        } else {
            Timber.w("OffBodyMonitor: No off-body sensor available")
            onBodyStateChanged(false)
            startTimer()
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
        Timber.d("OffBodyMonitor: Starting 10-second timer")
        timerJob = scope.launch {
            delay(10.seconds)
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
