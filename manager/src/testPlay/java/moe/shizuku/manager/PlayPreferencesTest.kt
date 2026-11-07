package moe.shizuku.manager

import moe.shizuku.manager.utils.EmptySharedPreferencesImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayPreferencesTest {
    @Test fun restoredRestrictedFeaturesCannotBeEnabled() {
        val field = ShizukuSettings::class.java.getDeclaredField("sPreferences").apply {
            isAccessible = true
        }
        val previous = field.get(null)
        val restoredPreferences = object : EmptySharedPreferencesImpl() {
            override fun getBoolean(key: String, defaultValue: Boolean) = true
            override fun getInt(key: String, defaultValue: Int) = ShizukuSettings.UpdateMode.BETA
            override fun edit(): android.content.SharedPreferences.Editor =
                error("Play must not modify restricted feature preferences")
        }
        field.set(null, restoredPreferences)
        try {
            assertFalse(ShizukuSettings.getWatchdog())
            assertFalse(ShizukuSettings.getLegacyPairing())
            assertEquals(ShizukuSettings.UpdateMode.OFF, ShizukuSettings.getUpdateMode())
            // Null is intentional: these must return before using any Android Context APIs.
            ShizukuSettings.setWatchdog(null, true)
            ShizukuSettings.setWatchdog(null, false)
            assertFalse(ShizukuSettings.isWatchdogRunning())
        } finally {
            field.set(null, previous)
        }
    }
}
