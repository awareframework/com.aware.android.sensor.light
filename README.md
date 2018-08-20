# AWARE Light

[![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.light.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.light)

The light sensor measures the ambient light. It can be used to detect indoor or outdoor light conditions. The official [SensorManager Light constants][3] are:

+ Cloudy sky: 100.0
+ Full moon: 0.25
+ No moon: 0.001
+ Overcast: 10000.0
+ Shade: 20000.0
+ Sunlight: 110000.0
+ Sunlight maximum: 120000.0
+ Sunrise: 400.0

## Public functions

### LightSensor

+ `start(context: Context, config: LightSensor.Config?)`: Starts the light sensor with the optional configuration.
+ `stop(context: Context)`: Stops the service.
+ `currentInterval`: Data collection rate per second. (e.g. 5 samples per second)

### LightSensor.Config

Class to hold the configuration of the sensor.

#### Fields

+ `sensorObserver: LightSensor.Observer`: Callback for live data updates.
+ `interval: Int`: Data samples to collect per second. (default = 5)
+ `period: Float`: Period to save data in minutes. (default = 1)
+ `threshold: Double`: If set, do not record consecutive points if change in value is less than the set value.
+ `enabled: Boolean` Sensor is enabled or not. (default = false)
+ `debug: Boolean` enable/disable logging to `Logcat`. (default = false)
+ `label: String` Label for the data. (default = "")
+ `deviceId: String` Id of the device that will be associated with the events and the sensor. (default = "")
+ `dbEncryptionKey` Encryption key for the database. (default =String? = null)
+ `dbType: Engine` Which db engine to use for saving data. (default = `Engine.DatabaseType.NONE`)
+ `dbPath: String` Path of the database. (default = "aware_wifi")
+ `dbHost: String` Host for syncing the database. (Defult = `null`)

## Broadcasts

### Fired Broadcasts

+ `LightSensor.ACTION_AWARE_LIGHT` fired when light saved data to db after the period ends.

### Received Broadcasts

+ `LightSensor.ACTION_AWARE_LIGHT_START`: received broadcast to start the sensor.
+ `LightSensor.ACTION_AWARE_LIGHT_STOP`: received broadcast to stop the sensor.
+ `LightSensor.ACTION_AWARE_LIGHT_SYNC`: received broadcast to send sync attempt to the host.
+ `LightSensor.ACTION_AWARE_LIGHT_SET_LABEL`: received broadcast to set the data label. Label is expected in the `LightSensor.EXTRA_LABEL` field of the intent extras.

## Data Representations

### Light Sensor

Contains the hardware sensor capabilities in the mobile device.

| Field      | Type   | Description                                                     |
| ---------- | ------ | --------------------------------------------------------------- |
| maxRange   | Float  | Maximum sensor value possible                                   |
| minDelay   | Float  | Minimum sampling delay in microseconds                          |
| name       | String | Sensor’s name                                                  |
| power      | Float  | Sensor’s power drain in mA                                     |
| resolution | Float  | Sensor’s resolution in sensor’s units                         |
| type       | String | Sensor’s type                                                  |
| vendor     | String | Sensor’s vendor                                                |
| version    | String | Sensor’s version                                               |
| deviceId   | String | AWARE device UUID                                               |
| label      | String | Customizable label. Useful for data calibration or traceability |
| timestamp  | Long   | unixtime milliseconds since 1970                                |
| timezone   | Int    | [Raw timezone offset][1] of the device                          |
| os         | String | Operating system of the device (ex. android)                    |

### Light Data

Contains the raw sensor data.

| Field     | Type   | Description                                                     |
| --------- | ------ | --------------------------------------------------------------- |
| light     | Float  | the ambient luminance in lux units                              |
| accuracy  | Int    | Sensor’s accuracy level (see [SensorManager][2])               |
| label     | String | Customizable label. Useful for data calibration or traceability |
| deviceId  | String | AWARE device UUID                                               |
| label     | String | Customizable label. Useful for data calibration or traceability |
| timestamp | Long   | unixtime milliseconds since 1970                                |
| timezone  | Int    | [Raw timezone offset][1] of the device                          |
| os        | String | Operating system of the device (ex. android)                    |

## Example usage

```kotlin
// To start the service.
LightSensor.start(appContext, LightSensor.Config().apply {
    sensorObserver = object : LightSensor.Observer {
        override fun onDataChanged(data: LightData) {
            // your code here...
        }
    }
    dbType = Engine.DatabaseType.ROOM
    debug = true
    // more configuration...
})

// To stop the service
LightSensor.stop(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()
[2]: http://developer.android.com/reference/android/hardware/SensorManager.html
[3]: http://developer.android.com/reference/android/hardware/SensorManager.html#LIGHT_CLOUDY