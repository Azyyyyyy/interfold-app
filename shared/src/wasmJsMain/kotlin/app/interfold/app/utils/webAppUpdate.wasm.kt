@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import app.interfold.app.BuildInfo
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString

private val pendingVersion = MutableStateFlow<String?>(null)
private var listenerStarted = false

actual val pendingWebAppVersion: Flow<String?>
  get() {
    startWebAppUpdateListenerIfNeeded()
    return pendingVersion
  }

actual fun reloadWebApp() {
  window.location.reload()
}

actual fun dismissPendingWebAppUpdate() {
  val version = pendingVersion.value ?: return
  writeDismissedWebAppVersion(version)
  pendingVersion.value = null
}

private fun startWebAppUpdateListenerIfNeeded() {
  if (listenerStarted) return
  listenerStarted = true
  startWebAppUpdateListener { jsVersion ->
    val version = jsVersion.toString()
    val dismissed = readDismissedWebAppVersion().ifEmpty { null }
    if (shouldAnnounceWebAppUpdate(BuildInfo.BUILD_ID, version, dismissed)) {
      pendingVersion.value = version
    }
  }
}

@JsFun("() => { try { return sessionStorage.getItem('interfold-dismissed-app-version') || ''; } catch (e) { return ''; } }")
private external fun readDismissedWebAppVersion(): String

@JsFun("(version) => { try { sessionStorage.setItem('interfold-dismissed-app-version', version); } catch (e) {} }")
private external fun writeDismissedWebAppVersion(version: String)

@JsFun(
  "(onVersion) => {" +
    "if (!('serviceWorker' in navigator)) return;" +
    "const deliver = (event) => {" +
      "const data = event.data;" +
      "if (data && data.type === 'interfold-app-version' && typeof data.version === 'string') {" +
        "onVersion(data.version);" +
      "}" +
    "};" +
    "navigator.serviceWorker.addEventListener('message', deliver);" +
    "const request = () => {" +
      "const controller = navigator.serviceWorker.controller;" +
      "if (controller) controller.postMessage({ type: 'interfold-app-version-request' });" +
    "};" +
    "request();" +
    "navigator.serviceWorker.ready.then(function() { request(); });" +
    "document.addEventListener('visibilitychange', () => {" +
      "if (document.visibilityState === 'visible') {" +
        "navigator.serviceWorker.getRegistration().then((reg) => { if (reg) reg.update(); });" +
      "}" +
    "});" +
  "}"
)
private external fun startWebAppUpdateListener(onVersion: (JsString) -> Unit)
