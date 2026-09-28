package app.interfold.app.telemetry

internal actual fun persistActivityEvents(events: List<ActivityEvent>) = Unit

internal actual fun loadPersistedActivityEvents(): List<ActivityEvent> = emptyList()
