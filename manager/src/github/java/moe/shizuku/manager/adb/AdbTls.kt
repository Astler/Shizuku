package moe.shizuku.manager.adb

import com.android.org.conscrypt.Conscrypt
import java.net.Socket
import javax.net.ssl.SSLSocket

internal object AdbTls {
    fun connect(socket: Socket, host: String, port: Int, key: AdbKey, label: String, length: Int): PairingTlsConnection {
        val tls = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        tls.startHandshake()
        return PairingTlsConnection(tls.inputStream, tls.outputStream,
            Conscrypt.exportKeyingMaterial(tls, label, null, length))
    }
}
