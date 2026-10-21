package app.interfold.app.integration

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Long-lived process that boots [BackendContainer], mints a JWT via the
 * real auth-callback (OkHttp can send the redirect cookie; the browser
 * cannot), and writes the base URL + token next to [args] `[0]`. Gradle's
 * `startWasmIntegrationBackend` waits on the URL file, then the wasm
 * browser tests use both values.
 */
fun main(args: Array<String>) {
  val readyFile = File(args.singleOrNull() ?: error("usage: BackendEntryPoint <ready-file>"))
  val container = BackendContainer()
  Runtime.getRuntime().addShutdownHook(Thread {
    try {
      container.stop()
    } catch (_: Exception) {
    }
  })
  container.start()
  val (browser, fronter) = runBlocking {
    obtainTestToken(container.baseUrl, uid = "wasm-browser") to
      obtainTestToken(container.baseUrl, uid = "wasm-fronter")
  }
  readyFile.parentFile.mkdirs()
  readyFile.writeText(container.baseUrl)
  File(readyFile.parent, "backend-token.txt").writeText(browser.token)
  File(readyFile.parent, "backend-system-id.txt").writeText(browser.scopedSystemId)
  File(readyFile.parent, "backend-token-a.txt").writeText(fronter.token)
  File(readyFile.parent, "backend-system-id-a.txt").writeText(fronter.scopedSystemId)
  val fcmMarker = File(readyFile.parent, "backend-fcm.txt")
  if (!interfoldItFcmServiceAccountJson().isNullOrBlank() &&
    runBlocking { fcmOutboxPresent(container.baseUrl) }
  ) {
    fcmMarker.writeText("1")
  } else {
    fcmMarker.delete()
  }
  val stopFile = File(readyFile.parent, "backend.stop")
  stopFile.delete()
  println("[backend] ready at ${container.baseUrl}")
  while (!stopFile.exists()) {
    Thread.sleep(500)
  }
}
