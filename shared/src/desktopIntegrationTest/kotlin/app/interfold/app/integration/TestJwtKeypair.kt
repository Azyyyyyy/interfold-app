package app.interfold.app.integration

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Test-only ES256 (P-256) keypair generated on the fly when the integration
 * suite first asks for it.
 *
 * Used by [BackendContainer] to feed both `OCTOCON_JWT_ES256_PRIVATE_KEY_PEM`
 * (the runtime's signing key) and `OCTOCON_JWT_ES256_VERIFICATION_KEYS`
 * (the verification chain) into the in-memory container, plus the matching
 * `OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_ES256_PRIVATE_PEM` so the
 * `SecretsBootstrapService` patches `AuthenticationConfiguration` at startup.
 *
 * Tokens are issued and verified by the backend itself — we never sign
 * anything client-side — so the only requirement on this keypair is that
 * the public PEM matches the private PEM, which is true by construction.
 *
 * Generated lazily so unit tests that don't touch the container pay no
 * cost. Generated once per test JVM so every container started in a single
 * Gradle worker shares the same keypair; if you start multiple containers
 * in one process they all trust each other's tokens.
 *
 * Replaces a previously committed `test-jwt-keys/private.pem` +
 * `public.pem` pair on disk: committing keypairs (even test-only ones)
 * flags secret-scanning tooling and isn't worth the ~0.5 KB on-disk
 * saving.
 */
internal object TestJwtKeypair {
  private val keyPair: KeyPair by lazy {
    KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
    }.generateKeyPair()
  }

  /**
   * Private key as a PKCS#8 PEM string (the format OpenSSL's
   * `pkcs8 -topk8 -nocrypt` produces). Matches the encoding the .NET
   * backend's `PemReader` expects.
   */
  val privatePem: String by lazy { encodeAsPem("PRIVATE KEY", keyPair.private.encoded) }

  /**
   * Public key as a SubjectPublicKeyInfo PEM string (the format OpenSSL's
   * `pkey -pubout` produces). Matches the `OCTOCON_JWT_ES256_VERIFICATION_KEYS`
   * format the backend reads.
   */
  val publicPem: String by lazy { encodeAsPem("PUBLIC KEY", keyPair.public.encoded) }

  // P-256 / prime256v1, the curve ES256 mandates.
  private const val EC_CURVE = "secp256r1"
}

private val PEM_BASE64: Base64.Encoder =
  Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte()))

private fun encodeAsPem(label: String, derBytes: ByteArray): String {
  val body = PEM_BASE64.encodeToString(derBytes)
  return buildString {
    append("-----BEGIN ").append(label).append("-----\n")
    append(body).append('\n')
    append("-----END ").append(label).append("-----\n")
  }
}
