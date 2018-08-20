package com.awareframework.android.sensor.light.model

import com.awareframework.android.core.model.AwareObject
import com.google.gson.Gson

/**
 * Contains the raw sensor data.
 *
 * @author  sercant
 * @date 20/08/2018
 */
data class LightData(
        var light: Float = 0f,
        var eventTimestamp: Long = 0L,
        var accuracy: Int = 0
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "lightData"
    }

    override fun toString(): String = Gson().toJson(this)
}