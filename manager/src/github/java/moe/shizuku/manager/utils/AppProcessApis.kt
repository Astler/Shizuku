package moe.shizuku.manager.utils

import android.os.Build
import android.os.SystemProperties
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.compatibility.DeviceCompatibility

internal object AppProcessApis {
    fun isMiui(): Boolean = DeviceCompatibility.isMiui()

    fun initialize() {
        if (Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.setHiddenApiExemptions("")
        }
    }

    fun getAdbTcpPort(): Int {
        val port = SystemProperties.getInt("service.adb.tcp.port", -1)
        return if (port == -1) SystemProperties.getInt("persist.adb.tcp.port", -1) else port
    }
}
