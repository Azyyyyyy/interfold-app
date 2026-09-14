@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import io.ktor.client.HttpClientConfig
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.factory.BaggageFactory
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.factory.SpanContextFactory
import io.opentelemetry.kotlin.factory.SpanFactory
import io.opentelemetry.kotlin.factory.TraceFlagsFactory
import io.opentelemetry.kotlin.factory.TraceStateFactory
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.instrumentation.ktor.client.openTelemetryKtorClientPlugin
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.metrics.MeterProvider
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider

internal const val KTOR_INSTRUMENTATION_SCOPE =
  "io.opentelemetry.kotlin.instrumentation.ktor.client"

/**
 * Stable [OpenTelemetry] for the Ktor plugin. The plugin reads a tracer at
 * install time, so [tracerProvider] always returns a forwarding tracer.
 */
internal object LiveOpenTelemetry : OpenTelemetry {
  @Volatile
  var sdk: OpenTelemetry = createOpenTelemetry {
    serviceName = "interfold-client"
    tracerProvider {
      export { KtorActivitySpanProcessor }
    }
  }

  override val tracerProvider: TracerProvider = object : TracerProvider {
    override fun getTracer(
      name: String,
      version: String?,
      schemaUrl: String?,
      attributes: (AttributesMutator.() -> Unit)?,
    ): Tracer = object : Tracer {
      private fun current(): Tracer =
        sdk.tracerProvider.getTracer(name, version, schemaUrl, attributes)

      override fun enabled(): Boolean = current().enabled()

      override fun startSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: (SpanCreationAction.() -> Unit)?,
      ): Span = current().startSpan(name, parentContext, spanKind, startTimestamp, action)
    }
  }

  override val loggerProvider: LoggerProvider get() = sdk.loggerProvider
  override val meterProvider: MeterProvider get() = sdk.meterProvider
  override val spanContext: SpanContextFactory get() = sdk.spanContext
  override val traceFlags: TraceFlagsFactory get() = sdk.traceFlags
  override val traceState: TraceStateFactory get() = sdk.traceState
  override val context: ContextFactory get() = sdk.context
  override val span: SpanFactory get() = sdk.span
  override val baggage: BaggageFactory get() = sdk.baggage
  override val propagator: TextMapPropagator get() = sdk.propagator
}

internal fun HttpClientConfig<*>.installOpenTelemetryKtorClient() {
  install(openTelemetryKtorClientPlugin(LiveOpenTelemetry))
}
