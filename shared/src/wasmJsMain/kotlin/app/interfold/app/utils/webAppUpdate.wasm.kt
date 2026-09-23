@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import app.interfold.app.BuildInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString

private val pendingVersion = MutableStateFlow<String?>(null)
private val noticeDismissed = MutableStateFlow(false)
private var listenerStarted = false

actual val pendingWebAppVersion: Flow<String?>
  get() {
    startWebAppUpdateListenerIfNeeded()
    return pendingVersion
  }

actual val webAppUpdateNoticeDismissed: Flow<Boolean>
  get() {
    startWebAppUpdateListenerIfNeeded()
    return noticeDismissed
  }

actual fun reloadWebApp() {
  skipWaitingAndReload()
}

actual fun dismissPendingWebAppUpdate() {
  val version = pendingVersion.value ?: return
  writeDismissedWebAppVersion(version)
  noticeDismissed.value = true
}

private fun startWebAppUpdateListenerIfNeeded() {
  if (listenerStarted) return
  listenerStarted = true
  startWebAppUpdateListener { jsVersion ->
    val version = jsVersion.toString()
    val dismissed = readDismissedWebAppVersion().ifEmpty { null }
    if (isPendingWebAppVersion(BuildInfo.BUILD_ID, version)) {
      pendingVersion.value = version
      noticeDismissed.value = !shouldShowWebAppUpdateNotice(
        BuildInfo.BUILD_ID,
        version,
        dismissed,
      )
    } else {
      pendingVersion.value = null
      noticeDismissed.value = false
    }
  }
}

@JsFun("() => { try { return localStorage.getItem('interfold-dismissed-app-version') || ''; } catch (e) { return ''; } }")
private external fun readDismissedWebAppVersion(): String

@JsFun("(version) => { try { localStorage.setItem('interfold-dismissed-app-version', version); } catch (e) {} }")
private external fun writeDismissedWebAppVersion(version: String)

@JsFun(
  "() => {" +
    "if (!('serviceWorker' in navigator)) { location.reload(); return; }" +
    "navigator.serviceWorker.getRegistration().then((reg) => {" +
      "const waiting = reg && reg.waiting;" +
      "if (!waiting) { location.reload(); return; }" +
      "let reloading = false;" +
      "const reloadOnce = () => { if (reloading) return; reloading = true; location.reload(); };" +
      "navigator.serviceWorker.addEventListener('controllerchange', reloadOnce);" +
      "waiting.postMessage({ type: 'SKIP_WAITING' });" +
      "setTimeout(reloadOnce, 1000);" +
    "}).catch(() => location.reload());" +
  "}"
)
private external fun skipWaitingAndReload()

@JsFun(
  "(onVersion) => {" +
    "if (!('serviceWorker' in navigator)) return;" +
    "const deliver = (version) => { if (typeof version === 'string') onVersion(version); };" +
    "navigator.serviceWorker.addEventListener('message', (event) => {" +
      "const data = event.data;" +
      "if (data && data.type === 'interfold-app-version' && typeof data.version === 'string') {" +
        "deliver(data.version);" +
      "}" +
    "});" +
    "const requestFrom = (worker) => { if (worker) worker.postMessage({ type: 'interfold-app-version-request' }); };" +
    "const inspect = (reg) => {" +
      "if (!reg) return;" +
      "requestFrom(reg.waiting);" +
      "requestFrom(reg.active);" +
      "if (reg.installing) {" +
        "reg.installing.addEventListener('statechange', () => { if (reg.waiting) requestFrom(reg.waiting); });" +
      "}" +
      "reg.addEventListener('updatefound', () => {" +
        "const installing = reg.installing;" +
        "if (!installing) return;" +
        "installing.addEventListener('statechange', () => {" +
          "if (installing.state === 'installed') requestFrom(reg.waiting || installing);" +
        "});" +
      "});" +
    "};" +
    "navigator.serviceWorker.getRegistration().then(inspect);" +
    "document.addEventListener('visibilitychange', () => {" +
      "if (document.visibilityState === 'visible') {" +
        "navigator.serviceWorker.getRegistration().then((reg) => {" +
          "if (!reg) return;" +
          "reg.update().then(() => inspect(reg));" +
        "});" +
      "}" +
    "});" +
  "}"
)
private external fun startWebAppUpdateListener(onVersion: (JsString) -> Unit)
