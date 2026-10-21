package app.interfold.app.integration

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Google login through the real client. Lives on desktop because OkHttp can
 * set the redirect cookie and read 302 Location; the wasm browser IT cannot.
 */
class GoogleLoginDesktopTest {

  @Test
  fun googleLogin_exchangesCode_overMockProvider() =
    runBackendIntegrationTest(timeout = 60.seconds, ::assertGoogleLoginExchangesCodeOverMock)
}
