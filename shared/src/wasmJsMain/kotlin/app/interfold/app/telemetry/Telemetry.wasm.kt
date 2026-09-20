package app.interfold.app.telemetry

internal actual fun applyOtelExport(urls: List<String>) = Unit

internal actual fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
) = Unit

internal actual fun startLiveEndpointSpan(
  name: String,
  attributes: Map<String, String>,
): Pair<Any?, Map<String, String>> = null to emptyMap()

internal actual fun endLiveEndpointSpan(
  token: Any?,
  status: ActivityStatus,
  attributes: Map<String, String>,
) = Unit

internal actual fun installUncaughtExceptionTelemetry() = Unit
