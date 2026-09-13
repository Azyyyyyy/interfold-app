package app.interfold.app.telemetry

internal actual fun applyOtelExport(urls: List<String>) = Unit

internal actual fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
) = Unit

internal actual fun installUncaughtExceptionTelemetry() = Unit
