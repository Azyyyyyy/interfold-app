@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

internal object KtorActivitySpanProcessor : SpanProcessor {
  override fun onStart(span: ReadWriteSpan, parentContext: Context) = Unit
  override fun onEnding(span: ReadWriteSpan) = Unit

  override fun onEnd(span: ReadableSpan) {
    if (span.instrumentationScopeInfo.name != KTOR_INSTRUMENTATION_SCOPE) return
    val startMs = span.startTimestamp / 1_000_000
    val endMs = (span.endTimestamp ?: span.startTimestamp) / 1_000_000
    val attrs = span.attributes.mapValues { it.value.toString() }
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
  override fun isOnEndingRequired(): Boolean = false

  override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
  override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
