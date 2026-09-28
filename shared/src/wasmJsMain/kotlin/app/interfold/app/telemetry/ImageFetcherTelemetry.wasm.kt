package app.interfold.app.telemetry

import io.ktor.client.HttpClientConfig

internal actual fun HttpClientConfig<*>.installImageFetcherTelemetry() = Unit
