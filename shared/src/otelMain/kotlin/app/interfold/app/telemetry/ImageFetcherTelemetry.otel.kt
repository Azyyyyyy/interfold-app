@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package app.interfold.app.telemetry

import app.interfold.app.api.CloudflareAccessCredentials
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLBuilder
import io.opentelemetry.kotlin.propagation.TextMapSetter
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData

internal actual fun HttpClientConfig<*>.installImageFetcherTelemetry() {
  install(ImageFetcherTelemetryPlugin)
}

/**
 * Own plugin (not the official Ktor one): 0.7.0 does not set URL/status or
 * inject W3C headers, which we need to match API image-processing spans.
 */
private val ImageFetcherTelemetryPlugin = createClientPlugin("interfold-image-telemetry") {
  on(Send) { request ->
    val tracer = LiveOpenTelemetry.tracerProvider.getTracer(IMAGE_INSTRUMENTATION_SCOPE)
    if (!tracer.enabled()) return@on proceed(request)

    val url = urlWithoutQuery(request.url)
    val method = request.method.value
    val span = tracer.startSpan(method, spanKind = SpanKind.CLIENT) {
      setStringAttribute("http.request.method", method)
      setStringAttribute("url.full", url)
    }
    val context = LiveOpenTelemetry.context.implicit().storeSpan(span)
    LiveOpenTelemetry.propagator.inject(context, request, KtorHeaderSetter)

    try {
      val call = proceed(request)
      val status = call.response.status
      span.setLongAttribute("http.response.status_code", status.value.toLong())
      if (status.value >= 400) {
        span.setStatus(StatusData.Error("HTTP ${status.value}"))
      } else {
        span.setStatus(StatusData.Ok)
      }
      call
    } catch (e: Throwable) {
      span.setStatus(StatusData.Error(e.message ?: e::class.simpleName ?: "error"))
      throw e
    } finally {
      span.end()
    }
  }
}

private object KtorHeaderSetter : TextMapSetter<HttpRequestBuilder> {
  override fun set(carrier: HttpRequestBuilder?, key: String, value: String) {
    if (carrier == null) return
    // Never let W3C inject overwrite or invent auth material.
    if (key.equals("Authorization", ignoreCase = true)) return
    if (key.equals(CloudflareAccessCredentials.JWT_ASSERTION_HEADER, ignoreCase = true)) return
    if (key.equals("Cookie", ignoreCase = true)) return
    carrier.headers[key] = value
  }
}

private fun urlWithoutQuery(url: URLBuilder): String {
  val copy = URLBuilder(url)
  copy.parameters.clear()
  copy.fragment = ""
  copy.user = null
  copy.password = null
  return copy.buildString()
}
