package app.interfold.app.telemetry

import kotlinx.browser.localStorage

private const val ACTIVITY_EVENTS_KEY = "interfold_activity_events"

internal actual fun persistActivityEvents(events: List<ActivityEvent>) {
  try {
    if (events.isEmpty()) {
      localStorage.removeItem(ACTIVITY_EVENTS_KEY)
    } else {
      localStorage.setItem(ACTIVITY_EVENTS_KEY, serializeActivityEvents(events))
    }
  } catch (t: Throwable) {
    println("Failed to persist activity: ${t.message ?: t.toString()}")
  }
}

internal actual fun loadPersistedActivityEvents(): List<ActivityEvent> {
  val json = try {
    localStorage.getItem(ACTIVITY_EVENTS_KEY)
  } catch (t: Throwable) {
    println("Failed to read activity: ${t.message ?: t.toString()}")
    return emptyList()
  } ?: return emptyList()
  return try {
    deserializeActivityEvents(json)
  } catch (t: Throwable) {
    println("Failed to decode activity: ${t.message ?: t.toString()}")
    emptyList()
  }
}
