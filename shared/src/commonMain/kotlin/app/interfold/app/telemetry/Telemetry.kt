package app.interfold.app.telemetry

import app.interfold.app.Settings
import app.interfold.app.api.fetchOtlpDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

const val PLATFORM_LOG_TRACER = "app.interfold.platform-log"
const val APP_TRACER = "app.interfold"
const val PLATFORM_LOG_MESSAGE_LIMIT = 512

sealed interface OtlpDiscoveryState {
  data object Idle : OtlpDiscoveryState
  data object Checking : OtlpDiscoveryState
  data object Unavailable : OtlpDiscoveryState
  data class Available(val url: String) : OtlpDiscoveryState
}

object Telemetry {
  private val mutex = Mutex()
  private val _discovery = MutableStateFlow<OtlpDiscoveryState>(OtlpDiscoveryState.Idle)
  val discovery: StateFlow<OtlpDiscoveryState> = _discovery.asStateFlow()

  private var lastExportKey: String? = null
  private var lastUncaughtFingerprint: String? = null
  private var lastUncaughtAtMillis: Long = 0L

  fun install() {
    ClientActivityStore.restore(loadPersistedActivityEvents())
    installUncaughtExceptionTelemetry()
  }

  suspend fun syncFromSettings(settings: Settings) {
    mutex.withLock {
      ClientActivityStore.setCapacity(settings.activityEventCapacity)
      val customUrl = settings.otlpEndpoint.trim().ifEmpty { null }
      val discoveredUrl = if (settings.shareActivityWithServer) {
        refreshDiscoveryLocked(settings.apiEndpoint)
      } else {
        _discovery.value = OtlpDiscoveryState.Idle
        null
      }
      applyExportLocked(discoveredUrl, customUrl)
    }
  }

  suspend fun refreshDiscovery(apiEndpoint: String) {
    mutex.withLock {
      refreshDiscoveryLocked(apiEndpoint)
    }
  }

  private suspend fun refreshDiscoveryLocked(apiEndpoint: String): String? {
    _discovery.value = OtlpDiscoveryState.Checking
    val result = resolveOtlpDiscovery(apiEndpoint)
    _discovery.value = result
    return (result as? OtlpDiscoveryState.Available)?.url
  }

  private fun applyExportLocked(discoveredUrl: String?, customUrl: String?) {
    val urls = listOfNotNull(discoveredUrl, customUrl).distinct()
    val key = urls.joinToString("|")
    if (key == lastExportKey) return
    lastExportKey = key
    applyOtelExport(urls)
  }

  fun recordPlatformLog(tag: String?, message: String) {
    val truncated = message.take(PLATFORM_LOG_MESSAGE_LIMIT)
    val name = tag?.takeIf { it.isNotBlank() } ?: "platform.log"
    val attrs = buildMap {
      put("log.tag", tag ?: "INTERFOLD")
      put("log.message", truncated)
    }
    ClientActivityStore.record(
      name = name,
      scope = ActivityScope.PLATFORM_LOG,
      status = ActivityStatus.OK,
      durationMillis = 0,
      attributes = attrs,
      message = truncated,
    )
    emitOtelSpan(
      tracerName = PLATFORM_LOG_TRACER,
      name = name,
      status = ActivityStatus.OK,
      durationMillis = 0,
      attributes = attrs,
    )
  }

  fun recordException(
    throwable: Throwable,
    extraAttributes: Map<String, String> = emptyMap(),
  ) {
    val details = exceptionActivityAttributes(throwable)
    val message = extraAttributes["exception.message"]?.takeIf { it.isNotBlank() }
      ?: details["exception.message"].orEmpty()
    val now = Clock.System.now().toEpochMilliseconds()
    val fingerprint = listOf(
      message,
      extraAttributes["exception.target"] ?: details["exception.target"].orEmpty(),
      extraAttributes["exception.source"] ?: details["exception.source"].orEmpty(),
    ).joinToString("\u0000")
    if (fingerprint == lastUncaughtFingerprint && now - lastUncaughtAtMillis < 1_000L) return
    lastUncaughtFingerprint = fingerprint
    lastUncaughtAtMillis = now
    val attrs = buildMap {
      put(
        "exception.type",
        sequenceOf(
          extraAttributes["exception.type"],
          details["exception.type"],
          readableExceptionType(throwable),
        ).firstOrNull { !it.isNullOrBlank() } ?: "Throwable",
      )
      put("exception.message", message)
      for ((key, value) in details) {
        if (value.isBlank() || key == "exception.type" || key == "exception.message") continue
        put(key, value)
      }
      for ((key, value) in extraAttributes) {
        if (value.isBlank() || key == "exception.type" || key == "exception.message") continue
        put(key, value)
      }
    }
    ClientActivityStore.record(
      name = "uncaught.exception",
      scope = ActivityScope.APP,
      status = ActivityStatus.ERROR,
      durationMillis = 0,
      attributes = attrs,
      message = message,
    )
    emitOtelSpan(
      tracerName = APP_TRACER,
      name = "uncaught.exception",
      status = ActivityStatus.ERROR,
      durationMillis = 0,
      attributes = attrs,
    )
  }

  inline fun <T> span(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: () -> T,
  ): T {
    val startedAt = Clock.System.now().toEpochMilliseconds()
    return try {
      val result = block()
      finishSpan(name, ActivityStatus.OK, startedAt, attributes, null)
      result
    } catch (e: Exception) {
      val details = exceptionActivityAttributes(e)
      finishSpan(
        name,
        ActivityStatus.ERROR,
        startedAt,
        attributes + details,
        details["exception.message"],
      )
      throw e
    }
  }

  suspend inline fun <T> spanSuspend(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend () -> T,
  ): T {
    val startedAt = Clock.System.now().toEpochMilliseconds()
    return try {
      val result = block()
      finishSpan(name, ActivityStatus.OK, startedAt, attributes, null)
      result
    } catch (e: Exception) {
      val details = exceptionActivityAttributes(e)
      finishSpan(
        name,
        ActivityStatus.ERROR,
        startedAt,
        attributes + details,
        details["exception.message"],
      )
      throw e
    }
  }

  fun beginEndpointSpan(
    method: String,
    route: String,
  ): EndpointSpanHandle {
    val attributes = mapOf(
      "http.method" to method,
      "http.route" to route,
    )
    val startedAt = Clock.System.now().toEpochMilliseconds()
    val (token, fields) = startLiveEndpointSpan("sendAPIRequest", attributes)
    return EndpointSpanHandle(
      name = "sendAPIRequest",
      startedAtMillis = startedAt,
      attributes = attributes,
      propagationFields = fields,
      token = token,
    )
  }

  fun finishSpan(
    name: String,
    status: ActivityStatus,
    startedAtMillis: Long,
    attributes: Map<String, String>,
    message: String?,
  ) {
    val duration = Clock.System.now().toEpochMilliseconds() - startedAtMillis
    ClientActivityStore.record(
      name = name,
      scope = ActivityScope.APP,
      status = status,
      durationMillis = duration,
      attributes = attributes,
      message = message,
      startedAtMillis = startedAtMillis,
    )
    emitOtelSpan(
      tracerName = APP_TRACER,
      name = name,
      status = status,
      durationMillis = duration,
      attributes = attributes,
    )
  }
}

internal fun otlpDiscoveryState(statusCode: Int, endpoint: String?): OtlpDiscoveryState {
  if (statusCode == 403 || statusCode == 404 || statusCode !in 200..299) {
    return OtlpDiscoveryState.Unavailable
  }
  val url = endpoint?.trim().orEmpty()
  return if (url.isEmpty()) OtlpDiscoveryState.Unavailable else OtlpDiscoveryState.Available(url)
}

internal suspend fun resolveOtlpDiscovery(apiEndpoint: String): OtlpDiscoveryState {
  val (statusCode, advertised) = fetchOtlpDiscovery(apiEndpoint)
  return otlpDiscoveryState(statusCode, advertised)
}

class EndpointSpanHandle internal constructor(
  val name: String,
  val startedAtMillis: Long,
  val attributes: Map<String, String>,
  val propagationFields: Map<String, String>,
  private val token: Any?,
) {
  private var finished = false

  fun finish(
    status: ActivityStatus,
    extraAttributes: Map<String, String> = emptyMap(),
    message: String? = null,
  ) {
    if (finished) return
    finished = true
    val attrs = attributes + extraAttributes
    val duration = Clock.System.now().toEpochMilliseconds() - startedAtMillis
    ClientActivityStore.record(
      name = name,
      scope = ActivityScope.APP,
      status = status,
      durationMillis = duration,
      attributes = attrs,
      message = message,
      startedAtMillis = startedAtMillis,
    )
    endLiveEndpointSpan(token, status, attrs)
  }
}

internal expect fun startLiveEndpointSpan(
  name: String,
  attributes: Map<String, String>,
): Pair<Any?, Map<String, String>>

internal expect fun endLiveEndpointSpan(
  token: Any?,
  status: ActivityStatus,
  attributes: Map<String, String>,
)

internal expect fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
)

internal expect fun applyOtelExport(urls: List<String>)

internal expect fun installUncaughtExceptionTelemetry()
