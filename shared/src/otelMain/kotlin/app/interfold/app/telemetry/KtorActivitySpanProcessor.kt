@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

internal object KtorActivitySpanProcessor : SpanProcessor {
  override fun onStart(span: ReadWriteSpan, parentContext: Context) = Unit

  override fun onEnding(span: ReadWriteSpan) {
    // Mutate before persistence / OTLP export so Access JWTs never leave the device.
    for ((key, value) in span.attributes) {
      val raw = value.toString()
      val redacted = sanitizeTelemetryAttribute(key, raw)
      if (redacted != raw) {
        span.setStringAttribute(key, redacted)
      }
    }
    val description = span.status.description
    val safeDescription = sanitizeTelemetryText(description)
    if (safeDescription != null && safeDescription != description) {
      span.setStatus(
        when (span.status.statusCode) {
          StatusCode.ERROR -> StatusData.Error(safeDescription)
          StatusCode.OK -> StatusData.Ok
          else -> span.status
        },
      )
    }
  }

  override fun onEnd(span: ReadableSpan) {
    val scope = span.instrumentationScopeInfo.name
    if (scope != KTOR_INSTRUMENTATION_SCOPE && scope != IMAGE_INSTRUMENTATION_SCOPE) return
    val startMs = span.startTimestamp / 1_000_000
    val endMs = (span.endTimestamp ?: span.startTimestamp) / 1_000_000
    val attrs = span.attributes.mapValues { (_, value) -> value.toString() }
    ClientActivityStore.record(
      name = span.name,
      scope = ActivityScope.APP,
      status = if (span.status.statusCode == StatusCode.ERROR) {
        ActivityStatus.ERROR
      } else {
        ActivityStatus.OK
      },
      durationMillis = endMs - startMs,
      attributes = attrs,
      message = span.status.description?.takeIf { it.isNotBlank() },
      startedAtMillis = startMs,
    )
  }

  override fun isStartRequired(): Boolean = false
  override fun isEndRequired(): Boolean = true
  override fun isOnEndingRequired(): Boolean = true

  override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
  override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
