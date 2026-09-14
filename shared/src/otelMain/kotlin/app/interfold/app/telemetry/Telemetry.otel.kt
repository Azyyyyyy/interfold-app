@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import app.interfold.app.BuildInfo
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.export.compositeLogRecordExporter
import io.opentelemetry.kotlin.logging.export.otlpHttpLogRecordExporter
import io.opentelemetry.kotlin.logging.export.persistingLogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.export.compositeSpanExporter
import io.opentelemetry.kotlin.tracing.export.otlpHttpSpanExporter
import io.opentelemetry.kotlin.tracing.export.persistingSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath

private const val SERVICE_NAME = "interfold-client"

internal actual fun applyOtelExport(urls: List<String>) {
  val previous = LiveOpenTelemetry.sdk
  if (urls.isEmpty()) {
    clearPersistenceDirectory()
  }
  val created = createSdk(urls)
  LiveOpenTelemetry.sdk = created
  shutdownPrevious(previous)
}

private fun createSdk(urls: List<String>): OpenTelemetry = createOpenTelemetry {
  serviceName = SERVICE_NAME
  resource(mapOf("service.version" to BuildInfo.VERSION_NAME))
  tracerProvider {
    export {
      if (urls.isEmpty()) {
        KtorActivitySpanProcessor
      } else {
        val exporters = urls.map { otlpHttpSpanExporter(it) }.toTypedArray()
        persistingSpanProcessor(
          processor = KtorActivitySpanProcessor,
          exporter = if (exporters.size == 1) exporters.first() else compositeSpanExporter(*exporters),
          cacheDirectory = "${otelPersistenceDirectory()}/spans".toPath(),
        )
      }
    }
  }
  if (urls.isNotEmpty()) {
    loggerProvider {
      export {
        val exporters = urls.map { otlpHttpLogRecordExporter(it) }.toTypedArray()
        persistingLogRecordProcessor(
          processor = IdleLogRecordProcessor,
          exporter = if (exporters.size == 1) exporters.first() else compositeLogRecordExporter(*exporters),
          cacheDirectory = "${otelPersistenceDirectory()}/logs".toPath(),
        )
      }
    }
  }
}

private object IdleLogRecordProcessor : LogRecordProcessor {
  override fun onEmit(log: ReadWriteLogRecord, context: Context) = Unit
  override fun enabled(
    context: Context,
    instrumentationScopeInfo: InstrumentationScopeInfo,
    severityNumber: SeverityNumber?,
    eventName: String?,
  ): Boolean = false

  override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
  override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}

private fun shutdownPrevious(previous: OpenTelemetry) {
  if (previous is TelemetryCloseable) {
    CoroutineScope(Dispatchers.Default).launch {
      runCatching { previous.shutdown() }
    }
  }
}

private fun clearPersistenceDirectory() {
  runCatching {
    FileSystem.SYSTEM.deleteRecursively(otelPersistenceDirectory().toPath(), mustExist = false)
  }
}

internal actual fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
) {
  val sdk = LiveOpenTelemetry.sdk
  val tracer = sdk.tracerProvider.getTracer(tracerName)
  if (!tracer.enabled()) return
  val span = tracer.startSpan(name) {
    attributes.forEach { (key, value) ->
      setStringAttribute(key, value)
    }
    setLongAttribute("duration_ms", durationMillis)
  }
  when (status) {
    ActivityStatus.OK -> span.setStatus(StatusData.Ok)
    ActivityStatus.ERROR -> span.setStatus(StatusData.Error(attributes["exception.message"] ?: name))
  }
  span.end()
}
