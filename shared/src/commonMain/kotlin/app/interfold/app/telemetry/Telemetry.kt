package app.interfold.app.telemetry

import app.interfold.app.Settings
import app.interfold.app.api.client
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLATFORM_LOG_TRACER = "app.interfold.platform-log"
const val APP_TRACER = "app.interfold"
const val PLATFORM_LOG_MESSAGE_LIMIT = 512

sealed interface OtlpDiscoveryState {
  data object Idle : OtlpDiscoveryState
  data object Checking : OtlpDiscoveryState
  data object Unavailable : OtlpDiscoveryState
  data class Available(val url: String) : OtlpDiscoveryState
}

@Serializable
internal data class OtlpDiscoveryResponse(
  @SerialName("otlpHttpEndpoint")
  val otlpHttpEndpoint: String? = null,
)

object Telemetry {
  private val mutex = Mutex()
  private val _discovery = MutableStateFlow<OtlpDiscoveryState>(OtlpDiscoveryState.Idle)
  val discovery: StateFlow<OtlpDiscoveryState> = _discovery.asStateFlow()

  private var lastExportKey: String? = null

  fun install() {
    installUncaughtExceptionTelemetry()
  }

  suspend fun syncFromSettings(settings: Settings) {
    mutex.withLock {
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
    val result = fetchOtlpDiscovery(apiEndpoint)
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

  fun recordException(throwable: Throwable) {
    val attrs = mapOf(
      "exception.type" to (throwable::class.simpleName ?: "Throwable"),
      "exception.message" to (throwable.message ?: ""),
    )
    ClientActivityStore.record(
      name = "uncaught.exception",
      scope = ActivityScope.APP,
      status = ActivityStatus.ERROR,
      durationMillis = 0,
      attributes = attrs,
      message = throwable.message,
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
      finishSpan(
        name,
        ActivityStatus.ERROR,
        startedAt,
        attributes + ("exception.message" to (e.message ?: "")),
        e.message,
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
      finishSpan(
        name,
        ActivityStatus.ERROR,
        startedAt,
        attributes + ("exception.message" to (e.message ?: "")),
        e.message,
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

internal suspend fun fetchOtlpDiscovery(apiEndpoint: String): OtlpDiscoveryState {
  return try {
    val base = apiEndpoint.trimEnd('/')
    val response = client.get("$base/api/telemetry/otlp")
    val body = if (response.status.isSuccess()) {
      response.body<OtlpDiscoveryResponse>()
    } else {
      null
    }
    otlpDiscoveryState(response.status.value, body?.otlpHttpEndpoint)
  } catch (_: Exception) {
    OtlpDiscoveryState.Unavailable
  }
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
