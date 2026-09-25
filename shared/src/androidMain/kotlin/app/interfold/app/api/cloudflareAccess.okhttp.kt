package app.interfold.app.api

import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.http.HttpHeaders

/**
 * OkHttp is the last hop before the wire. Ktor can drop `defaultRequest`
 * headers when it rebuilds a multipart PUT, and OkHttp follows Access 302s
 * as GET of the login HTML. Force the JWT on every call and do not follow.
 */
internal fun OkHttpConfig.installCloudflareAccessOkHttp() {
  addInterceptor { chain ->
    val jwt = CloudflareAccessCredentials.accessJwt
    val request = if (jwt.isNullOrBlank()) {
      chain.request()
    } else {
      chain.request().newBuilder()
        .header(CloudflareAccessCredentials.JWT_ASSERTION_HEADER, jwt)
        .header(HttpHeaders.Cookie, "${CloudflareAccessCredentials.COOKIE_NAME}=$jwt")
        .build()
    }
    chain.proceed(request)
  }
  config {
    followRedirects(false)
    followSslRedirects(false)
  }
}
