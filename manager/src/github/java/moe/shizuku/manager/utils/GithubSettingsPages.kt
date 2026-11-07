package moe.shizuku.manager.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import moe.shizuku.manager.service.WatchdogService

object AccessibilitySettingsPage : SettingsPage(Settings.ACTION_ACCESSIBILITY_SETTINGS)

object WatchdogNotificationSettingsPage : SettingsPage.Notifications() {
    override fun buildIntent(context: Context): Intent = super.buildIntent(context).apply {
        putExtra(Settings.EXTRA_CHANNEL_ID, WatchdogService.CRASH_CHANNEL_ID)
    }
}
