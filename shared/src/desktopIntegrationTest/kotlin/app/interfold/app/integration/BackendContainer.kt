package app.interfold.app.integration

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
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
 * - The `OCTOCON_INMEMORY_SECRETS_SEED__*` env vars (added upstream and
 *   bug-fixed in the image pinned below) seed the runtime's `ISecretsStore`
 *   with `encryption:pepper`, the ES256 signing PEM, and the deep-link
 *   HMAC secret — without them `SecretsBootstrapService` fails fast.
 * - `waitingFor` polls `/health/ready` (mounted by Aspire's ServiceDefaults
 *   at the root, not under `/api`).
 *
 * ## Image pinning
 *
 * [DEFAULT_IMAGE] is pinned to a sha256 digest so the slice stays
 * reproducible across machines and across upstream `:latest` retags. The
 * pin can be overridden via `-Dinterfold.backend.image=<image>:<tag>`
 * (forwarded by the Gradle Test task), which is useful when iterating
 * against a fork or testing an unreleased upstream change.
 */
class BackendContainer : GenericContainer<BackendContainer>(resolveImage()) {
  /**
   * When [FIXED_PORT_PROPERTY] is set, host and container listen on the same
   * port and [baseUrl] is `http://127.0.0.1:<port>`. Kotlin/Wasm Chrome tests
   * need that: Phoenix's endpoint proxy loops back using the inbound `Host`
   * header, which only resolves inside the container if the published port
   * matches the listen port.
   */
  private val fixedHostPort: Int? =
    System.getProperty(FIXED_PORT_PROPERTY)?.toIntOrNull()

  private val listenPort: Int = fixedHostPort ?: 8080

  init {
    withEnv("OCTOCON_PERSISTENCE", "inmemory")
    withEnv("ASPNETCORE_URLS", "http://+:$listenPort")
    withEnv("OCTOCON_JWT_AUTHORITY", "interfold-test")
    // Empty / unset means the in-memory image allows any Origin, which wasm
    // Chrome tests need (Karma serves from a different port than the backend).
    withEnv("OCTOCON_CORS_ALLOWED_ORIGINS", "")
    withEnv("OCTOCON_AUTH_CALLBACK_BASE_URL", "http://127.0.0.1:$listenPort")
    withEnv("OCTOCON_JWT_ES256_PRIVATE_KEY_PEM", TestJwtKeypair.privatePem)
    withEnv("OCTOCON_JWT_ES256_VERIFICATION_KEYS", TestJwtKeypair.publicPem)
    withEnv(BackendSeedEnvVars.ENCRYPTION_PEPPER, "TEST")
    withEnv(BackendSeedEnvVars.JWT_ES256_PRIVATE_PEM, TestJwtKeypair.privatePem)
    withEnv(BackendSeedEnvVars.DEEP_LINK_SECRET, "TEST_DEEP_LINK_SECRET")
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
    // Pinned digest of ghcr.io/azyyyyyy/interfold-api at upstream revision
    // 7ba967c6 — the first published build with the bug-fixed
    // OCTOCON_INMEMORY_SECRETS_SEED__* env-var lookup. Bumping this pin
    // means: (a) verifying bootstrap with a docker-run smoke test, and
    // (b) re-running :shared:desktopIntegrationTest locally.
    private const val DEFAULT_IMAGE =
      "ghcr.io/azyyyyyy/interfold-api@sha256:1727e25a969a052ad9d2bfe5392c0375068eea5fa026e082b97006901e010816"

    private fun resolveImage(): DockerImageName {
      val override = System.getProperty("interfold.backend.image").orEmpty()
      val image = if (override.isBlank()) DEFAULT_IMAGE else override
      println("[backend] Using image $image (override via -Dinterfold.backend.image=<tag>)")
      return DockerImageName.parse(image)
    }
  }
}

/** Env-var names the upstream image honours for in-memory secrets seeding. */
private object BackendSeedEnvVars {
  const val ENCRYPTION_PEPPER = "OCTOCON_INMEMORY_SECRETS_SEED__ENCRYPTION_PEPPER"
  const val JWT_ES256_PRIVATE_PEM = "OCTOCON_INMEMORY_SECRETS_SEED__AUTH_JWT_ES256_PRIVATE_PEM"
  const val DEEP_LINK_SECRET = "OCTOCON_INMEMORY_SECRETS_SEED__AUTH_DEEP_LINK_SECRET"
}
