package moe.shizuku.manager.adb

import android.net.ssl.SSLSockets
import android.os.Build
import java.net.Socket
import javax.net.ssl.SSLSocket

internal object AdbTls {
    fun connect(socket: Socket, host: String, port: Int, key: AdbKey, label: String, length: Int): PairingTlsConnection {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            return Android11PairingTls.connect(socket, key.pairingPrivateKey, key.pairingCertificate, label, length)
        }
        val tls = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        tls.startHandshake()
        val material = checkNotNull(SSLSockets.exportKeyingMaterial(tls, label, null, length)) {
            "TLS handshake did not produce keying material"
        }
        return PairingTlsConnection(tls.inputStream, tls.outputStream, material)
    }
}
