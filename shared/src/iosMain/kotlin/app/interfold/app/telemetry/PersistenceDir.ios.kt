package app.interfold.app.telemetry

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun otelPersistenceDirectory(): String {
  val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
  val base = paths.firstOrNull() as? String ?: return "/tmp/interfold-otel"
  return "$base/interfold-otel"
}
