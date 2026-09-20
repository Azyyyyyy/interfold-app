package app.interfold.app.telemetry

import app.interfold.app.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

enum class ActivityScope {
  APP,
  PLATFORM_LOG,
}

enum class ActivityStatus {
  OK,
  ERROR,
}

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
    _events.update { current ->
      if (current.size > nextCapacity) current.drop(current.size - nextCapacity) else current
    }
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
    return recorded
  }

  fun clear() {
    _events.value = emptyList()
  }

  fun snapshot(): List<ActivityEvent> = events.value
}

fun List<ActivityEvent>.filtered(filter: ActivityFilter): List<ActivityEvent> =
  when (filter) {
    ActivityFilter.ALL -> this
    ActivityFilter.NETWORK -> filter { it.scope == ActivityScope.APP && !it.name.startsWith("platform.log") }
    ActivityFilter.PLATFORM_LOG -> filter { it.scope == ActivityScope.PLATFORM_LOG }
    ActivityFilter.ERRORS -> filter { it.status == ActivityStatus.ERROR }
  }

enum class ActivityFilter {
  ALL,
  NETWORK,
  PLATFORM_LOG,
  ERRORS,
}
