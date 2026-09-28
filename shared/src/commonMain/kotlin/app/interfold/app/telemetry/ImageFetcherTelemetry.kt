package app.interfold.app.telemetry

import io.ktor.client.HttpClientConfig

internal const val IMAGE_INSTRUMENTATION_SCOPE = "app.interfold.image"

internal expect fun HttpClientConfig<*>.installImageFetcherTelemetry()
