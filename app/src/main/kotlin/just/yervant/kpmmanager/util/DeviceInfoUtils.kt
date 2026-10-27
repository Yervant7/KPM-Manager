package just.yervant.kpmmanager.util

import android.util.Log

private fun getSystemProperty(key: String): Boolean {
    try {
        val c = Class.forName("android.os.SystemProperties")
        val get = c.getMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        return get.invoke(c, key, false) as Boolean
    } catch (e: Exception) {
        Log.e("KPM-Manager", "[DeviceUtils] Failed to get system property: ", e)
    }
    return false
}

// Check to see if device supports A/B (seamless) system updates
fun isABDevice(): Boolean {
    return getSystemProperty("ro.build.ab_update")
}