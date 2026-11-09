package moe.shizuku.manager.adb

import java.net.Socket
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.CertificateEntry
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SignatureScheme
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

/** API 30 has no public platform TLS exporter. Use the public, pure Java TLS API. */
internal object Android11PairingTls {
    fun connect(socket: Socket, privateKey: PrivateKey, certificate: X509Certificate,
                label: String, length: Int): PairingTlsConnection {
        val crypto = BcTlsCrypto(SecureRandom())
        val client = object : DefaultTlsClient(crypto) {
            lateinit var keyMaterial: ByteArray

            override fun getSupportedVersions(): Array<ProtocolVersion> = arrayOf(ProtocolVersion.TLSv13)

            override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
                override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
                    // ADB authenticates this self-signed peer with the pairing code (SPAKE2).
                }

                override fun getClientCredentials(request: CertificateRequest): TlsCredentials {
                    val algorithm = listOf(SignatureScheme.rsa_pss_rsae_sha256,
                        SignatureScheme.rsa_pss_rsae_sha384, SignatureScheme.rsa_pss_rsae_sha512)
                        .map { SignatureScheme.getSignatureAndHashAlgorithm(it) }
                        .first { request.supportedSignatureAlgorithms.contains(it) }
                    val chain = Certificate(request.certificateRequestContext,
                        arrayOf(CertificateEntry(crypto.createCertificate(certificate.encoded), null)))
                    return BcDefaultTlsCredentialedSigner(TlsCryptoParameters(context), crypto,
                        PrivateKeyFactory.createKey(privateKey.encoded), chain, algorithm)
                }
            }

            override fun notifyHandshakeComplete() {
                super.notifyHandshakeComplete()
                // The TLS library clears exporter secrets after this callback.
                keyMaterial = context.exportKeyingMaterial(label, null, length)
            }
        }
        val protocol = TlsClientProtocol(socket.inputStream, socket.outputStream)
        protocol.connect(client)
        return PairingTlsConnection(protocol.inputStream, protocol.outputStream, client.keyMaterial)
    }
}
