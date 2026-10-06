package app.interfold.kotlix.ktor

import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Interceptor
import java.io.IOException

fun interface HostGate {
  fun allow(host: String): Boolean
}

/**
 * Process-wide hook so both the API client and the Phoenix websocket client
 * can ask the app before opening a socket to a LAN address.
 */
object AndroidHttpGates {
  @Volatile
  var localNetwork: HostGate? = null
}

fun OkHttpConfig.installAndroidLocalNetworkGate() {
  addInterceptor(Interceptor { chain ->
    val host = chain.request().url.host
    val gate = AndroidHttpGates.localNetwork
    if (gate != null && !gate.allow(host)) {
      throw IOException("Local network access was not granted for $host")
    }
    chain.proceed(chain.request())
  })
}
