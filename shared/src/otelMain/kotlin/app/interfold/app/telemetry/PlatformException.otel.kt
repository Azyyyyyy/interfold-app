package app.interfold.app.telemetry

@Suppress("UNUSED_PARAMETER")
internal actual fun platformExceptionAttributes(throwable: Throwable): Map<String, String> =
  emptyMap()
