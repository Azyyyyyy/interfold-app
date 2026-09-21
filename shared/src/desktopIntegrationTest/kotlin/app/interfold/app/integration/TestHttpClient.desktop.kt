package app.interfold.app.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

internal fun createNoRedirectHttpClient(): HttpClient = HttpClient(OkHttp) {
  followRedirects = false
  expectSuccess = false
}

internal fun createJsonHttpClient(): HttpClient = HttpClient(OkHttp) {
  install(ContentNegotiation) { json() }
}
