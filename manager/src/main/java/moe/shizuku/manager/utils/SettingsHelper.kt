package moe.shizuku.manager.utils

import android.content.ContentResolver
import moe.shizuku.manager.DistributionFeatures
import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.os.PowerManager
import android.provider.Settings

object SettingsHelper {

    private const val TAG = "SettingsHelper"

    /**
     * Writes a debugging-related Settings.Global value without crashing.
     * WRITE_SECURE_SETTINGS is not enough on Android 17: adb_enabled / adb_wifi_enabled also
     * require MANAGE_DEBUGGING, which a regular app cannot hold. Callers fall back to guiding the
     * user to Developer options when this returns false.
     */
    /**
     * Android 17 (API 37) hides debugging settings from apps: adb_enabled and adb_wifi_enabled
     * read as 0 even when enabled. There the value is unknown, so callers must not block on it
     * and should let mDNS discovery / the connection attempt decide.
     */
    fun isAdbEnabledOrUnknown(cr: ContentResolver): Boolean =
        android.os.Build.VERSION.SDK_INT >= 37 || Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) != 0

    fun putGlobalIntSafely(cr: ContentResolver, name: String, value: Int): Boolean = try {
        Settings.Global.putInt(cr, name, value)
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot write $name", e)
        false
    }

    fun putGlobalLongSafely(cr: ContentResolver, name: String, value: Long): Boolean = try {
        Settings.Global.putLong(cr, name, value)
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot write $name", e)
        false
    }

    fun launchOrHighlightWirelessDebugging(context: Context) {
        if (isAdbEnabledOrUnknown(context.contentResolver)) {
            SettingsPage.Developer.WirelessDebugging.launch(context)
        } else SettingsPage.Developer.HighlightWirelessDebugging.launch(context)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        val intent = DistributionFeatures.batteryOptimizationIntent(context)
        if (launcher != null) {
            launcher.launch(intent)
        } else {
            context.startActivity(intent)
        }
    }

}