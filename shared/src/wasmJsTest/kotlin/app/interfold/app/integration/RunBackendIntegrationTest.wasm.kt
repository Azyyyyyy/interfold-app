package app.interfold.app.integration

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.coroutines.withTimeout
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.time.Duration

@OptIn(DelicateCoroutinesApi::class)
internal fun runBackendIntegrationTest(
  timeout: Duration,
  block: suspend (baseUrl: String) -> Unit,
): Promise<JsAny?> {
  // Mocha waits on a returned Promise. runTest's TestResult does that too,
  // but its virtual scheduler turns withTimeout/delay into instant time and
  // Dispatchers.Default is not the browser thread Ktor fetch needs.
  val url = wasmBackendUrlOrSkip()
  return GlobalScope.promise {
    if (url == null) return@promise null
    withTimeout(timeout) { block(url) }
    null
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_BACKEND_URL__']; return (typeof v === 'string' ? v : ''); }")
private external fun injectedBackendUrl(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_BACKEND_TOKEN__']; return (typeof v === 'string' ? v : ''); }")
private external fun injectedBackendToken(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_BACKEND_SYSTEM_ID__']; return (typeof v === 'string' ? v : ''); }")
private external fun injectedBackendSystemId(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_BACKEND_TOKEN_A__']; return (typeof v === 'string' ? v : ''); }")
private external fun injectedBackendTokenA(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_BACKEND_SYSTEM_ID_A__']; return (typeof v === 'string' ? v : ''); }")
private external fun injectedBackendSystemIdA(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var v = globalThis['__INTERFOLD_REQUIRE_BACKEND__']; return v === true || v === '1' || v === 'true'; }")
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

internal fun injectedFronterPrincipal(): TestPrincipal? {
  val token = injectedBackendTokenA().trim()
  val systemId = injectedBackendSystemIdA().trim()
  if (token.isEmpty()) return null
  return TestPrincipal(token = token, scopedSystemId = systemId)
}
