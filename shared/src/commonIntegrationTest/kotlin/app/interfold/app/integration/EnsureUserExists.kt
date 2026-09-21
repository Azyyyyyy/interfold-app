package app.interfold.app.integration

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Bootstraps a principal in the in-memory backend so subsequent calls have a
 * profile to attach to. Mirrors `BaseEndpointTest.EnsureUserExistsAsync`.
 */
internal suspend fun ensureUserExists(
  baseUrl: String,
  token: String,
  username: String = "test-${Clock.System.now().toEpochMilliseconds().toString(36)}",
) {
  val client = createJsonHttpClient()
  try {
    val response = client.post("$baseUrl/api/settings/username") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(UsernameBody(username))
    }
    check(response.status.isSuccess()) {
      "ensureUserExists failed: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

@Serializable
private data class UsernameBody(val username: String)
