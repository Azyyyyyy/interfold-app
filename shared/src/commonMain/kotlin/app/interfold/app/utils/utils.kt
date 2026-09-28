package app.interfold.app.utils

import androidx.compose.runtime.Composable
import app.interfold.app.api.installCloudflareAccessHeadersForApiOrigin
import app.interfold.app.telemetry.installImageFetcherTelemetry
import io.kamel.core.config.Core
import io.kamel.core.config.DefaultCacheSize
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.takeFrom
import io.kamel.image.config.imageBitmapDecoder
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val colorRegex = Regex("^#[0-9A-Fa-f]{6}$")
val idRegex = Regex("^[a-z]{7}$")
val usernameRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9_\\-.]{3,14})[a-zA-Z0-9]")

@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)

expect fun LocalDateTime.dateFormat(): String
expect fun LocalDateTime.timeFormat(): String

expect fun LocalDateTime.dateTimeFormat(): String

expect fun LocalDate.monthYearFormat(): String

expect fun localeFormatNumber(number: Number): String

expect fun <T> List<T>.sortedLocaleAware(selector: (T) -> String): List<T>

@Suppress("NOTHING_TO_INLINE")
inline fun isGrayscale(hex: String): Boolean {
  return try {
    val rgb = hex.substring(1).chunked(2).map { it.toInt(16) }
    val (r, g, b) = rgb
    (r == g && g == b)
  } catch(e: Exception) {
    e.printStackTrace()
    false
  }
}

val kamelConfig = KamelConfig {
  takeFrom(KamelConfig.Core)
  // takeFrom(KamelConfig.Default)

  imageBitmapCacheSize = DefaultCacheSize

  imageBitmapDecoder()

  httpUrlFetcher {
    installApiOriginCredentials()
    installCloudflareAccessHeadersForApiOrigin()
    httpCache(100 * 1024 * 1024 /* 100 MiB */)

    install(HttpRequestRetry) {
      maxRetries = 3
      retryIf { _, httpResponse ->
        !httpResponse.status.isSuccess()
      }
    }
    installImageFetcherTelemetry()
  }
}

val noCacheKamelConfig = KamelConfig {
  takeFrom(KamelConfig.Core)

  imageBitmapCacheSize = 0

  imageBitmapDecoder()

  httpUrlFetcher {
    installApiOriginCredentials()
    installCloudflareAccessHeadersForApiOrigin()
    httpCache(0)

    install(HttpRequestRetry) {
      maxRetries = 3
      retryIf { _, httpResponse ->
        !httpResponse.status.isSuccess()
      }
    }
    installImageFetcherTelemetry()
  }
}

fun platformLog(message: String) = platformLog(null, message)

fun platformLog(tag: String? = null, message: String) {
  writePlatformLog(tag, message)
  app.interfold.app.telemetry.Telemetry.recordPlatformLog(tag, message)
}

expect fun writePlatformLog(tag: String? = null, message: String)

/** Browser image fetches include cookies only for the configured API host. */
internal expect fun HttpClientConfig<*>.installApiOriginCredentials()

val globalSerializersModule = SerializersModule {
  polymorphic(app.interfold.app.api.model.Poll::class) {
    subclass(
      app.interfold.app.api.model.VotePoll::class,
      app.interfold.app.api.model.VotePoll.serializer()
    )
    subclass(
      app.interfold.app.api.model.ChoicePoll::class,
      app.interfold.app.api.model.ChoicePoll.serializer()
    )
  }
}

val globalSerializer = Json {
  ignoreUnknownKeys = true
  allowStructuredMapKeys = true
  serializersModule = globalSerializersModule
}