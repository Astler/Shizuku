package moe.shizuku.manager.adb

import moe.shizuku.manager.utils.SettingsHelper

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

object AdbStarter {
    private val launches = AdbLaunchCoordinator(
        awaitIdle = { CancellableIo.awaitIdle() },
        awaitReady = {
            try {
                Starter.waitForBinder()
                true
            } catch (_: TimeoutException) {
                false
            }
        },
        isRunning = { Shizuku.pingBinder() }
    )

    suspend fun startAdb(context: Context, port: Int, log: ((String) -> Unit)? = null) {
        var entered = false
        launches.launch(skipIfRunning = true, checkAllowed = {}) { markPossiblyDispatched ->
            entered = true
            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            log?.invoke("Starting with wireless adb...\n")
            val key = loadKey()

            var activePort = port
            val tcpMode = ShizukuSettings.getTcpMode()
            val tcpPort = ShizukuSettings.getTcpPort()
            if (tcpMode && activePort != tcpPort) {
                log?.invoke("Connecting on port $activePort...")
                val client = AdbClient("127.0.0.1", activePort, key)
                CancellableIo.run(client) { checkActive ->
                    client.connect()
                    checkActive()
                    log?.invoke("Successfully connected on port $activePort...")
                    log?.invoke("\nRestarting in TCP mode port: $tcpPort")
                    try {
                        client.command("tcpip:$tcpPort", null, checkActive, {})
                    } catch (e: Exception) {
                        if (e !is EOFException && e !is SocketException) throw e
                    }
                }
                activePort = tcpPort
            }

            log?.invoke("Connecting on port $activePort...")
            connectWithRetry(activePort, key, markPossiblyDispatched) { client, checkActive, mark ->
                log?.invoke("Successfully connected on port $activePort...\n")
                client.command("shell:${Starter.internalCommand}", { log?.invoke(String(it)) }, checkActive, mark)
            }
        }
        if (entered) {
            // Do not disable discovery from a cancelled or failed launch.
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                SettingsHelper.putGlobalIntSafely(context.contentResolver, "adb_wifi_enabled", 0)
            }
        } else {
            ShizukuStateMachine.update()
        }
    }

    suspend fun stopTcp(context: Context, port: Int) {
        try {
            launches.withGate({}) {
                val cr = context.contentResolver
                if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                    SettingsHelper.putGlobalIntSafely(cr, Settings.Global.ADB_ENABLED, 1)
                    SettingsHelper.putGlobalLongSafely(cr, "adb_allowed_connection_time", 0L)
                }
                check(SettingsHelper.isAdbEnabledOrUnknown(cr)) { "ADB is not enabled" }
                ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                val key = loadKey()
                connectWithRetry(port, key, {}) { client, checkActive, mark ->
                    client.command("usb:", null, checkActive, mark)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (EnvironmentUtils.getAdbTcpPort() > 0) {
                ShizukuStateMachine.update()
                withContext(Dispatchers.Main) {
                    val errorMsg = when (e) {
                        is AdbKeyException -> context.getString(R.string.adb_error_key_store)
                        else -> e.message
                    }
                    Toast.makeText(context, context.getString(R.string.adb_error_stop_tcp) + ". ${errorMsg}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private suspend fun loadKey(): AdbKey = withContext(Dispatchers.IO) {
        try {
            AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AdbKeyException(e)
        }
    }

    private suspend fun connectWithRetry(
        port: Int,
        key: AdbKey,
        markPossiblyDispatched: () -> Unit,
        block: (AdbClient, () -> Unit, () -> Unit) -> Unit,
    ) {
        repeat(5) { attempt ->
            currentCoroutineContext().ensureActive()
            var dispatched = false
            try {
                val client = AdbClient("127.0.0.1", port, key)
                CancellableIo.run(client) { checkActive ->
                    client.connect()
                    checkActive()
                    block(client, checkActive) {
                        dispatched = true
                        markPossiblyDispatched()
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Once A_OPEN may have reached adbd, retrying could restart a live server.
                if (dispatched || attempt == 4 || e is SocketTimeoutException) throw e
                delay(1_000L * (attempt + 1))
            }
        }
    }
}
