package app.interfold.app.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

internal fun createNoRedirectHttpClient(): HttpClient = HttpClient(Js) {
  followRedirects = false
  expectSuccess = false
}

internal fun createJsonHttpClient(): HttpClient = HttpClient(Js) {
  install(ContentNegotiation) { json() }
}
