package app.interfold.app.integration

import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.Duration

internal fun runBackendIntegrationTest(
  timeout: Duration,
  block: suspend (baseUrl: String) -> Unit,
) {
  // Returning without running the body would count as a pass. karma.config.d
  // wraps Mocha's it() and calls this.skip() when no URL is injected, so
  // Gradle records these as skipped; this early return only avoids hitting
  // the network if that wrap races the test body.
  val url = wasmBackendUrlOrSkip() ?: return
  runTest(timeout = timeout) { block(url) }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof globalThis.__INTERFOLD_BACKEND_URL__ === 'string' ? globalThis.__INTERFOLD_BACKEND_URL__ : '')")
private external fun injectedBackendUrl(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof globalThis.__INTERFOLD_BACKEND_TOKEN__ === 'string' ? globalThis.__INTERFOLD_BACKEND_TOKEN__ : '')")
private external fun injectedBackendToken(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof globalThis.__INTERFOLD_BACKEND_SYSTEM_ID__ === 'string' ? globalThis.__INTERFOLD_BACKEND_SYSTEM_ID__ : '')")
private external fun injectedBackendSystemId(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (globalThis.__INTERFOLD_REQUIRE_BACKEND__ === true || globalThis.__INTERFOLD_REQUIRE_BACKEND__ === '1' || globalThis.__INTERFOLD_REQUIRE_BACKEND__ === 'true')")
private external fun requireBackend(): Boolean

private fun wasmBackendUrlOrSkip(): String? {
  val url = injectedBackendUrl().trim()
  val token = injectedBackendToken().trim()
  val systemId = injectedBackendSystemId().trim()
  if (url.isNotEmpty()) {
    if (token.isNotEmpty()) {
      preMintedTestPrincipal = TestPrincipal(token = token, scopedSystemId = systemId)
    }
    return url
  }
  check(!requireBackend()) {
    "Wasm integration tests required a backend URL but none was injected. " +
      "Run :shared:wasmJsBrowserIntegrationTest (not :shared:wasmJsBrowserTest)."
  }
  return null
}
