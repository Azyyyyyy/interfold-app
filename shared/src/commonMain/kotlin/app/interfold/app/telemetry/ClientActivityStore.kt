package app.interfold.app.telemetry

import app.interfold.app.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
enum class ActivityScope {
  APP,
  PLATFORM_LOG,
}

@Serializable
enum class ActivityStatus {
  OK,
  ERROR,
}

@Serializable
data class ActivityEvent(
  val id: Long,
  val name: String,
  val scope: ActivityScope,
  val status: ActivityStatus,
  val startedAtMillis: Long,
  val durationMillis: Long,
  val attributes: Map<String, String>,
  val message: String?,
)

object ClientActivityStore {
  var capacity: Int = Settings.DEFAULT_ACTIVITY_EVENT_CAPACITY
    private set

  private var nextId = 1L
  private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
  val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()

  fun setCapacity(value: Int) {
    val nextCapacity = value.coerceAtLeast(1)
    capacity = nextCapacity
    var trimmed = false
    _events.update { current ->
      if (current.size > nextCapacity) {
        trimmed = true
        current.drop(current.size - nextCapacity)
      } else {
        current
      }
    }
    if (trimmed) persistSnapshot()
  }

  fun record(
    name: String,
    scope: ActivityScope,
    status: ActivityStatus,
    durationMillis: Long,
    attributes: Map<String, String> = emptyMap(),
    message: String? = null,
    startedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
  ): ActivityEvent {
    val safeAttributes = sanitizeTelemetryAttributes(attributes)
    val safeMessage = sanitizeTelemetryText(message)
    lateinit var recorded: ActivityEvent
    _events.update { current ->
      recorded = ActivityEvent(
        id = nextId++,
        name = name,
        scope = scope,
        status = status,
        startedAtMillis = startedAtMillis,
        durationMillis = durationMillis.coerceAtLeast(0),
        attributes = safeAttributes,
        message = safeMessage,
      )
      val next = current + recorded
      val limit = capacity
      if (next.size > limit) next.drop(next.size - limit) else next
    }
    persistSnapshot()
    return recorded
  }

  fun clear() {
    _events.value = emptyList()
    persistSnapshot()
  }

  fun snapshot(): List<ActivityEvent> = events.value

  internal fun restore(events: List<ActivityEvent>) {
    if (events.isEmpty()) return
    _events.update { current ->
      val merged = if (current.isEmpty()) {
        events
      } else {
        val existingIds = current.mapTo(HashSet(current.size)) { it.id }
        val incoming = events.filter { it.id !in existingIds }
        (incoming + current).sortedBy { it.startedAtMillis }
      }
      if (merged.size > capacity) merged.takeLast(capacity) else merged
    }
    val maxId = _events.value.maxOfOrNull { it.id } ?: 0L
    if (nextId <= maxId) nextId = maxId + 1
  }

  private fun persistSnapshot() {
    persistActivityEvents(_events.value)
  }
}

private val httpMethodNames = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

internal fun ActivityEvent.isNetworkActivity(): Boolean {
  if (scope != ActivityScope.APP) return false
  val n = name
  if (n == "sendAPIRequest" || n.startsWith("phoenix.") || n.startsWith("http.")) return true
  if (n.uppercase() in httpMethodNames) return true
  return attributes.keys.any { key ->
    key.startsWith("http.") || key.startsWith("url.") || key.startsWith("phoenix.")
  }
}

fun List<ActivityEvent>.filtered(filter: ActivityFilter): List<ActivityEvent> =
  when (filter) {
    ActivityFilter.ALL -> this
    ActivityFilter.NETWORK -> filter { it.isNetworkActivity() }
    ActivityFilter.PLATFORM_LOG -> filter { it.scope == ActivityScope.PLATFORM_LOG }
    ActivityFilter.ERRORS -> filter { it.status == ActivityStatus.ERROR }
  }

enum class ActivityFilter {
  ALL,
  NETWORK,
  PLATFORM_LOG,
  ERRORS,
}
