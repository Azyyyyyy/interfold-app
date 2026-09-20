package app.interfold.app.telemetry

import app.interfold.app.utils.BuildConfig
import java.io.File

internal actual fun otelPersistenceDirectory(): String {
  val base = BuildConfig.applicationContext?.cacheDir
    ?: File(System.getProperty("java.io.tmpdir"))
  return File(base, "interfold-otel").absolutePath
}
