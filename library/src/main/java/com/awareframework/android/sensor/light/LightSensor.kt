package com.awareframework.android.sensor.light

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_LIGHT
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.db.model.DbSyncConfig
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.light.model.LightData
import com.awareframework.android.sensor.light.model.LightDevice

/**
 * AWARE Light module
 * The light sensor measures the ambient light. It can be used to detect indoor or outdoor light conditions. The official SensorManager Light constants are:
 * Cloudy sky: 100.0
 * Full moon: 0.25
 * No moon: 0.001
 * Overcast: 10000.0
 * Shade: 20000.0
 * Sunlight: 110000.0
 * Sunlight maximum: 120000.0
 * Sunrise: 400.0

 * @author  sercant
 * @date 20/08/2018
 */
class LightSensor : AwareSensor(), SensorEventListener {

    companion object {
        const val TAG = "AWARE::Light"

        const val ACTION_AWARE_LIGHT = "ACTION_AWARE_LIGHT"

        const val ACTION_AWARE_LIGHT_START = "com.awareframework.android.sensor.light.SENSOR_START"
        const val ACTION_AWARE_LIGHT_STOP = "com.awareframework.android.sensor.light.SENSOR_STOP"

        const val ACTION_AWARE_LIGHT_SET_LABEL = "com.awareframework.android.sensor.light.ACTION_AWARE_LIGHT_SET_LABEL"
        const val EXTRA_LABEL = "label"

        const val ACTION_AWARE_LIGHT_SYNC = "com.awareframework.android.sensor.light.SENSOR_SYNC"

        val CONFIG = Config()

        var currentInterval: Int = 0
            private set

        fun start(context: Context, config: Config? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, LightSensor::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LightSensor::class.java))
        }
    }

    private lateinit var mSensorManager: SensorManager
    private var mLight: Sensor? = null
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    private var lastSave = 0L

    private var lastValue: Float = 0f
    private var lastTimestamp: Long = 0
    private var lastSavedAt: Long = 0

    private val dataBuffer = ArrayList<LightData>()

    private var dataCount: Int = 0
    private var lastDataCountTimestamp: Long = 0

    private val lightReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_AWARE_LIGHT_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_LIGHT_SYNC -> onSync(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)

        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mLight = mSensorManager.getDefaultSensor(TYPE_LIGHT)

        sensorThread = HandlerThread(TAG)
        sensorThread.start()

        sensorHandler = Handler(sensorThread.looper)

        registerReceiver(lightReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_LIGHT_SET_LABEL)
            addAction(ACTION_AWARE_LIGHT_SYNC)
        })

        logd("Light service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return if (mLight != null) {
            saveSensorDevice(mLight)

            val samplingFreqUs = if (CONFIG.interval > 0) 1000000 / CONFIG.interval else 0
            mSensorManager.registerListener(
                    this,
                    mLight,
                    samplingFreqUs,
                    sensorHandler)

            lastSave = System.currentTimeMillis()

            logd("Light service active: ${CONFIG.interval} samples per second.")

            START_STICKY
        } else {
            logw("This device doesn't have a light sensor!")

            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        sensorHandler.removeCallbacksAndMessages(null)
        mSensorManager.unregisterListener(this, mLight)
        sensorThread.quit()

        dbEngine?.close()

        unregisterReceiver(lightReceiver)

        logd("Light service terminated...")
    }

    private fun saveSensorDevice(sensor: Sensor?) {
        sensor ?: return

        val device = LightDevice().apply {
            deviceId = CONFIG.deviceId
            label = CONFIG.label
            timestamp = System.currentTimeMillis()

            maxRange = sensor.maximumRange
            minDelay = sensor.minDelay.toFloat()
            name = sensor.name
            power = sensor.power
            resolution = sensor.resolution
            type = sensor.type.toString()
            vendor = sensor.vendor
            version = sensor.version.toString()
        }

        dbEngine?.save(device, LightDevice.TABLE_NAME, 0)

        logd("Light sensor info: $device")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //We log current accuracy on the sensor changed event
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastDataCountTimestamp >= 1000) {
            currentInterval = dataCount
            dataCount = 0
            lastDataCountTimestamp = currentTime
        }

        if (currentTime - lastTimestamp < (900.0 / CONFIG.interval)) {
            // skip this event
            return
        }
        lastTimestamp = currentTime

        if (CONFIG.threshold > 0
                && Math.abs(event.values[0] - lastValue) < CONFIG.threshold) {
            return
        }
        lastValue = event.values[0]

        val data = LightData().apply {
            deviceId = CONFIG.deviceId
            label = CONFIG.label
            timestamp = currentTime

            eventTimestamp = event.timestamp
            light = event.values[0]
            accuracy = event.accuracy
        }

        CONFIG.sensorObserver?.onDataChanged(data)

        dataBuffer.add(data)
        dataCount++

        if (currentTime - lastSavedAt < CONFIG.period * 60000) { // convert minute to ms
            // not ready to save yet
            return
        }
        lastSavedAt = currentTime

        val dataBuffer = this.dataBuffer.toTypedArray()
        this.dataBuffer.clear()

        try {
            logd("Saving buffer to database.")
            dbEngine?.save(dataBuffer, LightData.TABLE_NAME)

            sendBroadcast(Intent(ACTION_AWARE_LIGHT))
        } catch (e: Exception) {
            e.message ?: logw(e.message!!)
            e.printStackTrace()
        }
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(LightData.TABLE_NAME)
        dbEngine?.startSync(LightDevice.TABLE_NAME, DbSyncConfig(removeAfterSync = false))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    interface Observer {
        fun onDataChanged(data: LightData)
    }

    data class Config(
            /**
             * For real-time observation of the sensor data collection.
             */
            var sensorObserver: Observer? = null,

            /**
             * Light interval in hertz per second: e.g.
             *
             * 0 - fastest
             * 1 - sample per second
             * 5 - sample per second
             * 20 - sample per second
             */
            var interval: Int = 5,

            /**
             * Period to save data in minutes. (optional)
             */
            var period: Float = 1f,

            /**
             * Light threshold (float).  Do not record consecutive points if
             * change in value is less than the set value.
             */
            var threshold: Double = 0.0

            // TODO wakelock?

    ) : SensorConfig(dbPath = "aware_light") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is Config) {
                sensorObserver = config.sensorObserver
                interval = config.interval
                period = config.period
                threshold = config.threshold
            }
        }
    }

    class LightSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        start(context)
                    }
                }

                ACTION_AWARE_LIGHT_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stop(context)
                }

                ACTION_AWARE_LIGHT_START -> {
                    start(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (LightSensor.CONFIG.debug) Log.d(LightSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(LightSensor.TAG, text)
}