package app.interfold.app.telemetry

import app.interfold.app.utils.globalSerializer
import kotlinx.serialization.builtins.ListSerializer

private val activityEventListSerializer = ListSerializer(ActivityEvent.serializer())

internal fun serializeActivityEvents(events: List<ActivityEvent>): String =
  globalSerializer.encodeToString(activityEventListSerializer, events)

internal fun deserializeActivityEvents(json: String): List<ActivityEvent> =
  globalSerializer.decodeFromString(activityEventListSerializer, json)

internal expect fun persistActivityEvents(events: List<ActivityEvent>)

internal expect fun loadPersistedActivityEvents(): List<ActivityEvent>
