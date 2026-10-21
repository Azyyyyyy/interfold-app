package app.interfold.app.integration

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.time.Duration

/**
 * Testcontainers wrapper around the published Interfold backend image in its
 * in-memory persistence mode.
 *
 * - REST + WebSocket share one Kestrel listener on container port 8080.
 * - An ES256 keypair generated on the fly by [TestJwtKeypair] is what the
 *   backend uses to *issue* JWTs (via `IssueDeepLinkTokenAsync`) and to
 *   verify them on subsequent requests. The matching public PEM is the
 *   single entry in `OCTOCON_JWT_ES256_VERIFICATION_KEYS`. We never sign
 *   tokens client-side — `obtainTestToken` drives the auth-callback flow
 *   so the backend mints + records each JTI itself. The keypair is fresh
 *   per test JVM; nothing about it is committed to the repo.
 * - The `OCTOCON_INMEMORY_SECRETS_SEED__*` env vars seed the runtime's
 *   `ISecretsStore` with `encryption:pepper`, the ES256 + RSA-2048 signing
 *   PEMs, and the deep-link HMAC secret — without them
 *   `SecretsBootstrapService` / `AuthenticationConfiguration` fails fast.
 * - `waitingFor` polls `/health/ready` (mounted by Aspire's ServiceDefaults
 *   at the root, not under `/api`).
 *
 * ## Image pinning (branch-synced channels)
 *
 * Digests are pinned per upstream publish channel so CI stays reproducible
 * across floating-tag retags:
 *
 * | App branch (and PRs into it) | API channel | Constant |
 * | --- | --- | --- |
 * | `develop` | `bleeding-edge` | [BLEEDING_EDGE_IMAGE] |
 * | `main` | `latest` | [LATEST_IMAGE] |
 *
 * Selection order for [resolveImage]:
 * 1. `-Dinterfold.backend.image=<ref>` override (floating tag or digest)
 * 2. `-Dinterfold.backend.channel=bleeding-edge|latest`
 * 3. CI branch hints: `GITHUB_BASE_REF` (PR target), then `GITHUB_REF`
 * 4. Local `git rev-parse --abbrev-ref HEAD`
 * 5. Default `bleeding-edge` (dev-oriented)
 *
 * Bump a channel's digest only after Interfold CI has published that tag for
 * the intended SHA (`docker buildx imagetools inspect …:bleeding-edge|:latest`).
 * When merging app `develop` → `main`, ensure the `latest` digest is current
 * (API main must have published first).
 */
class BackendContainer : GenericContainer<BackendContainer>(resolveImage()) {
  /**
   * When [FIXED_PORT_PROPERTY] is set (or auto-selected on Windows), host and
   * container listen on the same port and [baseUrl] is `http://127.0.0.1:<port>`.
   * Kotlin/Wasm Chrome tests need that: Phoenix's endpoint proxy loops back
   * using the inbound `Host` header, which only resolves inside the container
   * if the published port matches the listen port.
   *
   * Docker Desktop on Windows cannot reach the container's bridge IP from the
   * host JVM, so we pick a free port and publish it 1:1 unless the property
   * already set one. Linux CI keeps the default bridge-IP path.
   */
  private val fixedHostPort: Int? =
    System.getProperty(FIXED_PORT_PROPERTY)?.toIntOrNull()
      ?: if (needsHostMappedAccess()) allocateEphemeralPort() else null

  private val listenPort: Int = fixedHostPort ?: 8080

  init {
    withEnv("OCTOCON_PERSISTENCE", "inmemory")
    withEnv("ASPNETCORE_URLS", "http://+:$listenPort")
    withEnv("OCTOCON_JWT_AUTHORITY", "interfold-test")
    // Empty / unset means the in-memory image allows any Origin, which wasm
    // Chrome tests need (Karma serves from a different port than the backend).
    withEnv("OCTOCON_CORS_ALLOWED_ORIGINS", "")
    withEnv("OCTOCON_AUTH_CALLBACK_BASE_URL", "http://127.0.0.1:$listenPort")
    // Client ID registers the Google challenge scheme and flips login-methods.
    // No client secret: callback uses the `email` fallback so the API never
    // POSTs oauth2.googleapis.com.
    withEnv("OCTOCON_GOOGLE_OAUTH_CLIENT_ID", TEST_GOOGLE_OAUTH_CLIENT_ID)
    withEnv("OCTOCON_JWT_ES256_PRIVATE_KEY_PEM", TestJwtKeypair.privatePem)
    withEnv("OCTOCON_JWT_ES256_VERIFICATION_KEYS", TestJwtKeypair.publicPem)
    withEnv(BackendSeedEnvVars.ENCRYPTION_PEPPER, "TEST")
    withEnv(BackendSeedEnvVars.JWT_ES256_PRIVATE_PEM, TestJwtKeypair.privatePem)
    withEnv(BackendSeedEnvVars.JWT_RSA256_PRIVATE_PEM, TestJwtKeypair.rsaPrivatePem)
    withEnv(BackendSeedEnvVars.DEEP_LINK_SECRET, "TEST_DEEP_LINK_SECRET")
    withEnv("OCTOCON_TEST_HOOKS", "1")
    withEnv("OCTOCON_FRONT_NOTIFIER_QUIET_MS", "0")
    withEnv("OCTOCON_FRONT_NOTIFIER_FLUSH_MS", "200")
    seedFcmIfPresent()
    withExposedPorts(listenPort)
    if (fixedHostPort != null) {
      addFixedExposedPort(fixedHostPort, listenPort)
    }
    waitingFor(
      Wait.forHttp("/health/ready")
        .forStatusCode(200)
        .withStartupTimeout(Duration.ofMinutes(2))
    )
    withLogConsumer { frame -> print("[backend] " + frame.utf8String) }
  }

  private fun seedFcmIfPresent() {
    val serviceAccount = interfoldItFcmServiceAccountJson()
    if (serviceAccount.isNullOrBlank()) {
      println("[backend] $FCM_SA_ENV unset — FCM stays NullFCMService; friend-front ITs skip.")
      return
    }
    withEnv(BackendSeedEnvVars.FCM_SERVICE_ACCOUNT_JSON, serviceAccount)

    val webRaw = interfoldItFirebaseWebJson()
    val webSeed = webRaw?.let { toFirebaseWebClientSeed(it) }
    if (webSeed != null) {
      withEnv(BackendSeedEnvVars.FIREBASE_CLIENT_WEB, webSeed)
    } else if (!webRaw.isNullOrBlank()) {
      println("[backend] $FCM_WEB_ENV present but missing vapid_key / required web fields — not seeded.")
    }

    val androidRaw = interfoldItFirebaseAndroidJson()
    val androidSeed = androidRaw?.let { toFirebaseAndroidClientSeed(it) }
    if (androidSeed != null) {
      withEnv(BackendSeedEnvVars.FIREBASE_CLIENT_ANDROID, androidSeed)
    } else if (!androidRaw.isNullOrBlank()) {
      val keys = parseFirebaseJsonObject(androidRaw)?.keys
      println(
        "[backend] $FCM_ANDROID_ENV present but missing Android client fields — keys=$keys len=${androidRaw.length}",
      )
    }
    envOrBlank(FCM_IOS_ENV)?.let { withEnv(BackendSeedEnvVars.FIREBASE_CLIENT_IOS, it) }
    println("[backend] Seeded FCM service account + optional Firebase client configs.")
  }

  /**
   * Base URL for HTTP and WebSocket access. No trailing slash; no `/api`
   * suffix.
   *
   * Default: the container's internal IP + [listenPort], so Phoenix
   * `endpoint` proxy loopback (`{Request.Scheme}://{Request.Host}`) can
   * re-resolve inside the container. Fixed-port mode (wasm Chrome) uses
   * `127.0.0.1` on that same port instead — see [fixedHostPort].
   */
  val baseUrl: String
    get() {
      val port = fixedHostPort
      if (port != null) return "http://127.0.0.1:$port"
      val networks = containerInfo.networkSettings.networks
      val ip = networks["bridge"]?.ipAddress
        ?: networks.values.firstOrNull()?.ipAddress
        ?: error(
          "Container has no resolvable network IP. " +
            "Networks: ${networks.keys}",
        )
      return "http://$ip:$listenPort"
    }

  companion object {
    const val FIXED_PORT_PROPERTY = "interfold.backend.port"
    const val IMAGE_PROPERTY = "interfold.backend.image"
    const val CHANNEL_PROPERTY = "interfold.backend.channel"

    const val CHANNEL_BLEEDING_EDGE = "bleeding-edge"
    const val CHANNEL_LATEST = "latest"

    // Digest of ghcr.io/azyyyyyy/interfold-api:bleeding-edge @ upstream
    // 675863a4 (2026-10-20, develop tip when pinned).
    private const val BLEEDING_EDGE_IMAGE =
      "ghcr.io/azyyyyyy/interfold-api@sha256:d68764d9026395fde35e80c63785ad7f39d4d5cc1c30b767c8873c478d1512eb"

    // Digest of ghcr.io/azyyyyyy/interfold-api:latest @ upstream
    // 4e1981a4 (2026-10-03, main tip when pinned).
    private const val LATEST_IMAGE =
      "ghcr.io/azyyyyyy/interfold-api@sha256:4dfe865895d7dd1c6732e63f92e6362581425a3666c0835a49abfb48124efc9d"

    internal fun resolveImage(): DockerImageName {
      val override = System.getProperty(IMAGE_PROPERTY).orEmpty().trim()
      if (override.isNotEmpty()) {
        println(
          "[backend] Using image override $override " +
            "(via -D$IMAGE_PROPERTY=…)",
        )
        return DockerImageName.parse(override)
      }
      val channel = resolveChannel()
      val image = imageForChannel(channel)
      println(
        "[backend] Using image $image (channel=$channel; " +
          "override via -D$IMAGE_PROPERTY=… or -D$CHANNEL_PROPERTY=…)",
      )
      return DockerImageName.parse(image)
    }

    internal fun resolveChannel(): String {
      val explicit = System.getProperty(CHANNEL_PROPERTY).orEmpty().trim()
      if (explicit == CHANNEL_BLEEDING_EDGE || explicit == CHANNEL_LATEST) {
        return explicit
      }
      val branch = detectAppBranch()
      return channelForBranch(branch)
    }

    internal fun channelForBranch(branch: String): String {
      val normalized = branch.trim().lowercase()
        .removePrefix("refs/heads/")
        .removePrefix("origin/")
      return when (normalized) {
        "main", "master" -> CHANNEL_LATEST
        else -> CHANNEL_BLEEDING_EDGE
      }
    }

    private fun imageForChannel(channel: String): String =
      when (channel) {
        CHANNEL_LATEST -> LATEST_IMAGE
        else -> BLEEDING_EDGE_IMAGE
      }

    private fun needsHostMappedAccess(): Boolean {
      val os = System.getProperty("os.name").orEmpty().lowercase()
      return os.contains("windows") || os.contains("mac")
    }

    private fun allocateEphemeralPort(): Int =
      ServerSocket(0).use { it.localPort }

    private fun detectAppBranch(): String {
      val baseRef = System.getenv("GITHUB_BASE_REF").orEmpty().trim()
      if (baseRef.isNotEmpty()) return baseRef

      val githubRef = System.getenv("GITHUB_REF").orEmpty().trim()
      if (githubRef.startsWith("refs/heads/")) {
        return githubRef.removePrefix("refs/heads/")
      }

      return try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
          .redirectErrorStream(true)
          .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exited = process.waitFor()
        if (exited == 0 && output.isNotEmpty() && output != "HEAD") output
        else CHANNEL_BLEEDING_EDGE
      } catch (_: Exception) {
        CHANNEL_BLEEDING_EDGE
      }
    }
  }
}

/** Env-var names the upstream image honours for in-memory secrets seeding. */
private object BackendSeedEnvVars {
  const val ENCRYPTION_PEPPER = "OCTOCON_INMEMORY_SECRETS_SEED__ENCRYPTION_PEPPER"
  const val JWT_ES256_PRIVATE_PEM = "OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_ES256_PRIVATE_PEM"
  const val JWT_RSA256_PRIVATE_PEM = "OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_RSA256_PRIVATE_PEM"
  const val DEEP_LINK_SECRET = "OCTOCON_INMEMORY_SECRETS_SEED__AUTH_DEEP_LINK_SECRET"
  const val FCM_SERVICE_ACCOUNT_JSON = "OCTOCON_INMEMORY_SECRETS_SEED__FCM_SERVICE_ACCOUNT_JSON"
  const val FIREBASE_CLIENT_WEB = "OCTOCON_INMEMORY_SECRETS_SEED__FIREBASE_CLIENT_WEB"
  const val FIREBASE_CLIENT_ANDROID = "OCTOCON_INMEMORY_SECRETS_SEED__FIREBASE_CLIENT_ANDROID"
  const val FIREBASE_CLIENT_IOS = "OCTOCON_INMEMORY_SECRETS_SEED__FIREBASE_CLIENT_IOS"
}
