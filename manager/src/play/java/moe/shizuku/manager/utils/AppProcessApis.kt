package moe.shizuku.manager.utils

/** App-process paths use public APIs, including before the Shizuku server starts. */
internal object AppProcessApis {
    fun initialize() = Unit

    fun isMiui(): Boolean = !getProperty("ro.miui.ui.version.name").isNullOrEmpty()

    fun getAdbTcpPort(): Int {
        val port = getPropertyInt("service.adb.tcp.port")
        return if (port == -1) getPropertyInt("persist.adb.tcp.port") else port
    }

    private fun getPropertyInt(name: String): Int = getProperty(name)?.toIntOrNull() ?: -1

    private fun getProperty(name: String): String? {
        return try {
            // Run as the app UID. No su, hidden framework calls, or server dependency.
            val process = ProcessBuilder("/system/bin/getprop", name).start()
            try {
                val value = process.inputStream.bufferedReader().use { it.readText().trim() }
                if (process.waitFor() == 0) value else null
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            null
        }
    }
}
