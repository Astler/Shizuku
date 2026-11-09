package moe.shizuku.manager.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.RemoteException
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import rikka.shizuku.server.ServerConstants

/** Only the shell/root server resolves hidden system services in Play builds. */
object ShizukuSystemApis {
    private val users = arrayListOf<UserInfoCompat>()

    private fun ownerOnly() = listOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))

    fun getUsers(useCache: Boolean = true): List<UserInfoCompat> {
        synchronized(users) {
            if (!ShizukuStateMachine.isRunning()) {
                users.clear()
                return ownerOnly()
            }
            if (!useCache || users.isEmpty()) {
                val result = try {
                    transact(ServerConstants.BINDER_TRANSACTION_getUsers) { reply ->
                        List(reply.readInt()) { UserInfoCompat(reply.readInt(), reply.readString()) }
                    }
                } catch (_: Throwable) {
                    ownerOnly()
                }
                users.clear()
                users.addAll(result)
            }
            return users.toList()
        }
    }

    fun getUserInfo(userId: Int): UserInfoCompat =
        getUsers().firstOrNull { it.id == userId }
            ?: UserInfoCompat(UserHandleCompat.myUserId(), "Unknown")

    fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        if (!ShizukuStateMachine.isRunning()) return emptyList()
        return transact(ServerConstants.BINDER_TRANSACTION_getInstalledPackages, {
            writeLong(flags)
            writeInt(userId)
        }) { reply ->
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list
                ?: emptyList()
        }
    }

    fun checkPermission(permName: String, pkgName: String, userId: Int): Int {
        if (!ShizukuStateMachine.isRunning()) return PackageManager.PERMISSION_DENIED
        return transact(ServerConstants.BINDER_TRANSACTION_checkRuntimePermission, {
            writeString(pkgName)
            writeString(permName)
            writeInt(userId)
        }) { it.readInt() }
    }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        changeRuntimePermission(ServerConstants.BINDER_TRANSACTION_grantRuntimePermission,
            packageName, permissionName, userId)
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        changeRuntimePermission(ServerConstants.BINDER_TRANSACTION_revokeRuntimePermission,
            packageName, permissionName, userId)
    }

    private fun changeRuntimePermission(code: Int, packageName: String, permissionName: String, userId: Int) {
        if (!ShizukuStateMachine.isRunning()) return
        transact(code, {
            writeString(packageName)
            writeString(permissionName)
            writeInt(userId)
        }) { Unit }
    }

    private inline fun <T> transact(
        code: Int,
        writeArguments: Parcel.() -> Unit = {},
        readResult: (Parcel) -> T
    ): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR)
            data.writeArguments()
            val binder = Shizuku.getBinder() ?: throw RemoteException("Shizuku server unavailable")
            if (!binder.transact(code, data, reply, 0)) {
                throw RemoteException("Server does not support manager transaction $code; restart the server")
            }
            reply.readException()
            return readResult(reply)
        } catch (e: RemoteException) {
            throw RuntimeException(e.message, e)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
