package moe.shizuku.manager.adb

import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Test

class Android11PairingTlsTest {
    @Test(timeout = 20000)
    fun publicTlsClientSupportsMutualTls13AndAdbExporterLabel() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=adb-pairing-test")
        val cert = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date(System.currentTimeMillis() - 60000), Date(System.currentTimeMillis() + 60000),
                name, pair.public).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)))
        val password = "test".toCharArray()
        val keys = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, password)
            setKeyEntry("adb", pair.private, password, arrayOf(cert))
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keys, password)
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
        // Interoperate with the JDK implementation, not a second copy of the client TLS stack.
        val context = SSLContext.getInstance("TLSv1.3", "SunJSSE").apply {
            init(managers.keyManagers, arrayOf(trust), SecureRandom())
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            (context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket).use { server ->
                server.enabledProtocols = arrayOf("TLSv1.3")
                server.needClientAuth = true
                val peer = executor.submit(Callable {
                    (server.accept() as SSLSocket).use { tls ->
                        tls.soTimeout = 5000
                        tls.startHandshake()
                        assertEquals("TLSv1.3", tls.session.protocol)
                        assertArrayEquals(cert.encoded, tls.session.peerCertificates.single().encoded)
                        assertEquals(42, tls.inputStream.read())
                        tls.outputStream.write(43)
                        tls.outputStream.flush()
                    }
                })
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
                    socket.soTimeout = 5000
                    val connection = Android11PairingTls.connect(socket, pair.private, cert, "adb-label\u0000", 64)
                    assertEquals(64, connection.keyMaterial.size)
                    assertFalse(connection.keyMaterial.all { it == 0.toByte() })
                    connection.output.write(42)
                    connection.output.flush()
                    assertEquals(43, connection.input.read())
                }
                peer.get(10, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
