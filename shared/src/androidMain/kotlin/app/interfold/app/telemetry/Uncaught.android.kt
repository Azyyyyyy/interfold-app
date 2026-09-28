package app.interfold.app.telemetry

internal actual fun installUncaughtExceptionTelemetry() {
  val previous = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    runCatching { Telemetry.recordException(throwable) }
    previous?.uncaughtException(thread, throwable)
  }
}
