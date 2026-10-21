package app.interfold.app.integration

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Test-only JWT keypairs generated on the fly when the integration suite
 * first asks for them.
 *
 * Used by [BackendContainer] to feed:
 * - ES256: `OCTOCON_JWT_ES256_PRIVATE_KEY_PEM` / verification keys +
 *   `OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_ES256_PRIVATE_PEM`
 * - RSA-2048: `OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_RSA256_PRIVATE_PEM`
 *   (required by `AuthenticationConfiguration.Rsa256PrivateKey` /
 *   JWKS + recovery-code paths on current API images)
 *
 * Tokens are issued and verified by the backend itself — we never sign
 * anything client-side — so the only requirement is that each public PEM
 * matches its private PEM, which is true by construction.
 *
 * Generated lazily so unit tests that don't touch the container pay no
 * cost. Generated once per test JVM so every container started in a single
 * Gradle worker shares the same keypairs.
 */
internal object TestJwtKeypair {
  private val esKeyPair: KeyPair by lazy {
    KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
    }.generateKeyPair()
  }

  private val rsaKeyPair: KeyPair by lazy {
    KeyPairGenerator.getInstance("RSA").apply {
      initialize(RSA_BITS, SecureRandom())
    }.generateKeyPair()
  }

  /**
   * ES256 private key as a PKCS#8 PEM string (the format OpenSSL's
   * `pkcs8 -topk8 -nocrypt` produces). Matches the encoding the .NET
   * backend's `PemReader` expects.
   */
  val privatePem: String by lazy { encodeAsPem("PRIVATE KEY", esKeyPair.private.encoded) }

  /**
   * ES256 public key as a SubjectPublicKeyInfo PEM string (the format
   * OpenSSL's `pkey -pubout` produces). Matches the
   * `OCTOCON_JWT_ES256_VERIFICATION_KEYS` format the backend reads.
   */
  val publicPem: String by lazy { encodeAsPem("PUBLIC KEY", esKeyPair.public.encoded) }

  /**
   * RSA-2048 private key as PKCS#8 PEM. Seeded into
   * `OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_RSA256_PRIVATE_PEM`.
   */
  val rsaPrivatePem: String by lazy {
    encodeAsPem("PRIVATE KEY", rsaKeyPair.private.encoded)
  }

  // P-256 / prime256v1, the curve ES256 mandates.
  private const val EC_CURVE = "secp256r1"
  private const val RSA_BITS = 2048
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
