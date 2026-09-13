@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import app.interfold.app.BuildInfo
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.logging.export.batchLogRecordProcessor
import io.opentelemetry.kotlin.logging.export.compositeLogRecordExporter
import io.opentelemetry.kotlin.logging.export.otlpHttpLogRecordExporter
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanExporter
import io.opentelemetry.kotlin.tracing.export.otlpHttpSpanExporter

private const val SERVICE_NAME = "interfold-client"

private var otel: OpenTelemetry? = null

internal actual fun applyOtelExport(urls: List<String>) {
  if (urls.isEmpty()) {
    otel = null
    return
  }
  otel = createOpenTelemetry {
    serviceName = SERVICE_NAME
    resource(mapOf("service.version" to BuildInfo.VERSION_NAME))
    tracerProvider {
      export {
        val exporters = urls.map { otlpHttpSpanExporter(it) }.toTypedArray()
        batchSpanProcessor(
          if (exporters.size == 1) exporters.first() else compositeSpanExporter(*exporters)
        )
      }
    }
    loggerProvider {
      export {
        val exporters = urls.map { otlpHttpLogRecordExporter(it) }.toTypedArray()
        batchLogRecordProcessor(
          if (exporters.size == 1) exporters.first() else compositeLogRecordExporter(*exporters)
        )
      }
    }
  }
}

internal actual fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
) {
  val sdk = otel ?: return
  val tracer = sdk.tracerProvider.getTracer(tracerName)
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
