package app.interfold.app.telemetry

import java.io.File

internal actual fun otelPersistenceDirectory(): String {
  val home = System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir")
  return File(home, ".cache/interfold/otel").absolutePath
}
