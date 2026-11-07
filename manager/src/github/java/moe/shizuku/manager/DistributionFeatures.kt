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

import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import moe.shizuku.manager.ShizukuSettings.Keys.*
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.app.SnackbarHelper
import moe.shizuku.manager.home.AdbPairDialogFragment
import moe.shizuku.manager.home.StealthViewHolder
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.settings.IntegerSimpleMenuPreference
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UpdateHelper
import rikka.core.content.asActivity

object DistributionFeatures {
    fun onHomeCreated(activity: HomeActivity, root: View) {
        activity.lifecycleScope.launch {
            if (UpdateHelper.isCheckForUpdatesEnabled() && UpdateHelper.isNewUpdateAvailable()) {
                SnackbarHelper.show(
                    activity, root,
                    msg = activity.getString(R.string.snackbar_update_available),
                    duration = Snackbar.LENGTH_INDEFINITE,
                    actionText = activity.getString(R.string.snackbar_action_update),
                    action = { activity.lifecycleScope.launch { UpdateHelper.update() } }
                )
                UpdateHelper.updateLastPromptedVersion()
            }
        }
    }

    fun bindAbout(activity: HomeActivity, binding: AboutDialogBinding) {
        binding.btnUpdate.setOnClickListener {
            activity.lifecycleScope.launch { UpdateHelper.checkAndInstallUpdates() }
        }
    }

    fun addHomeItems(adapter: IdBasedRecyclerViewAdapter) {
        adapter.addItem(StealthViewHolder.CREATOR, null, 9L)
    }

    fun onHomeIntent(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(HomeActivity.EXTRA_SHOW_PAIRING_DIALOG, false)) {
            context.showAccessibilityDialog()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun pair(context: Context) {
        if (EnvironmentUtils.isTelevision()) {
            context.showAccessibilityDialog()
        } else if ((context.display?.displayId ?: -1) > 0 || ShizukuSettings.getLegacyPairing()) {
            AdbPairDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
        } else {
            context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
        }
    }

    fun onPreferenceChanged(fragment: SettingsFragment, key: String?) {
        if (key == KEY_WATCHDOG) {
            fragment.findPreference<TwoStatePreference>(KEY_WATCHDOG)!!.isChecked = ShizukuSettings.isWatchdogRunning()
        }
    }

    fun configureSettings(
        fragment: SettingsFragment,
        toggleBatterySensitiveSetting: (Boolean, (Boolean) -> Unit) -> Unit
    ) {
        val context = fragment.requireContext()
        fragment.findPreference<TwoStatePreference>(KEY_WATCHDOG)!!.apply {
            isChecked = ShizukuSettings.isWatchdogRunning()
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is Boolean) {
                    toggleBatterySensitiveSetting(newValue) { result ->
                        if (result) {
                            ShizukuSettings.setWatchdog(context, newValue)
                            isChecked = newValue
                        }
                    }
                }
                false
            }
        }
        fragment.findPreference<IntegerSimpleMenuPreference>(KEY_UPDATE_MODE)!!.value = ShizukuSettings.getUpdateMode()
        val legacyPairing = fragment.findPreference<TwoStatePreference>(KEY_LEGACY_PAIRING)!!
        legacyPairing.isVisible = !EnvironmentUtils.isTelevision()
        fragment.findPreference<PreferenceCategory>(KEY_CATEGORY_ADVANCED)!!.isVisible = legacyPairing.isVisible
    }

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:" + context.packageName)
        }

    @JvmStatic fun startWatchdog(context: Context) = WatchdogService.start(context)
    @JvmStatic fun stopWatchdog(context: Context) = WatchdogService.stop(context)
    @JvmStatic fun isWatchdogRunning(): Boolean = WatchdogService.isRunning()
}
