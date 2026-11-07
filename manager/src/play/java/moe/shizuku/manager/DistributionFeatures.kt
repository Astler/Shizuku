package moe.shizuku.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.home.HomeActivity
import moe.shizuku.manager.settings.SettingsFragment
import rikka.recyclerview.IdBasedRecyclerViewAdapter

import android.provider.Settings
import moe.shizuku.manager.adb.AdbPairingTutorialActivity

/** Play has no updater, APK patcher, accessibility pairing, or watchdog implementation. */
object DistributionFeatures {
    fun onHomeCreated(activity: HomeActivity, root: View) = Unit
    fun bindAbout(activity: HomeActivity, binding: AboutDialogBinding) = Unit
    fun addHomeItems(adapter: IdBasedRecyclerViewAdapter) = Unit
    fun onHomeIntent(context: Context, intent: Intent) = Unit
    fun onPreferenceChanged(fragment: SettingsFragment, key: String?) = Unit
    fun configureSettings(
        fragment: SettingsFragment,
        toggleBatterySensitiveSetting: (Boolean, (Boolean) -> Unit) -> Unit
    ) = Unit

    @RequiresApi(Build.VERSION_CODES.R)
    fun pair(context: Context) {
        // Always use notification input, including TV, secondary displays and restored settings.
        context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
    }

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    @JvmStatic fun startWatchdog(context: Context) = Unit
    @JvmStatic fun stopWatchdog(context: Context) = Unit
    @JvmStatic fun isWatchdogRunning(): Boolean = false
}
