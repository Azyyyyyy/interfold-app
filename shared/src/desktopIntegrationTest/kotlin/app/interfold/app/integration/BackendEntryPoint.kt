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
  val principal = runBlocking { obtainTestToken(container.baseUrl) }
  readyFile.parentFile.mkdirs()
  readyFile.writeText(container.baseUrl)
  File(readyFile.parent, "backend-token.txt").writeText(principal.token)
  File(readyFile.parent, "backend-system-id.txt").writeText(principal.scopedSystemId)
  println("[backend] ready at ${container.baseUrl}")
  System.`in`.read()
}
