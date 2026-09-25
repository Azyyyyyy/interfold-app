package app.interfold.app.utils

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalWasmJsInterop::class)
class PwaUpdateE2ETest {
  @Test
  fun lastTabActivateWithoutApplyServesPinnedCache() = runTest {
    val body = pwaE2ELastTabWithoutApply().await<JsString>().toString()
    assertEquals("OLD_JS", body)
  }

  @Test
  fun skipWaitingServesNewCacheAndDeletesOldAppCache() = runTest {
    val raw = pwaE2ESkipWaitingApplies().await<JsString>().toString()
    val body = raw.substringBefore('\n')
    val keys = raw.substringAfter('\n')
    assertEquals("NEW_JS", body)
    assertFalse(keys.contains(AppUpdatePolicy.appCacheName("e2e-old")))
    assertTrue(keys.contains(AppUpdatePolicy.appCacheName("e2e-incoming")))
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "() => (async function() {" +
    "await window.__interfoldPwaE2EReset();" +
    "await window.__interfoldPwaE2ESeed();" +
    "await window.__interfoldPwaE2ERegister();" +
    "var resp = await fetch('/interfold-app.js', { cache: 'no-store' });" +
    "return await resp.text();" +
    "})()"
)
private external fun pwaE2ELastTabWithoutApply(): Promise<JsString>

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "() => (async function() {" +
    "await window.__interfoldPwaE2EReset();" +
    "await window.__interfoldPwaE2ESeed();" +
    "await window.__interfoldPwaE2ERegister();" +
    "var reg = await navigator.serviceWorker.ready;" +
    "if (!reg.active) throw new Error('no active worker');" +
    "reg.active.postMessage({ type: 'SKIP_WAITING' });" +
    "await new Promise(function(resolve) { setTimeout(resolve, 250); });" +
    "var resp = await fetch('/interfold-app.js', { cache: 'no-store' });" +
    "var body = await resp.text();" +
    "var keys = await caches.keys();" +
    "return body + '\\n' + keys.join(',');" +
    "})()"
)
private external fun pwaE2ESkipWaitingApplies(): Promise<JsString>
