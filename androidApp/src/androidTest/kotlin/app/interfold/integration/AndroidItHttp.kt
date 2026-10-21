package app.interfold.integration

import app.interfold.app.api.model.APIResponse
import app.interfold.app.utils.globalSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun androidItHttpClient(): HttpClient = HttpClient(OkHttp) {
  engine {
    config {
      protocols(listOf(okhttp3.Protocol.HTTP_1_1))
      retryOnConnectionFailure(true)
    }
  }
  install(ContentNegotiation) { json(globalSerializer) }
}

/**
 * Emulator `127.0.0.1` needs `adb reverse`; that tunnel often EOFs against
 * Docker Desktop on Windows. `10.0.2.2` is the emulator's host loopback and
 * reaches a published container port without reverse.
 */
internal suspend fun resolveReachableBackendUrl(preferred: String): String {
  val trimmed = preferred.trim().trimEnd('/')
  val candidates = linkedSetOf(trimmed)
  if (trimmed.contains("127.0.0.1")) {
    candidates += trimmed.replace("127.0.0.1", "10.0.2.2")
  }
  if (trimmed.contains("10.0.2.2")) {
    candidates += trimmed.replace("10.0.2.2", "127.0.0.1")
  }
  val errors = mutableListOf<String>()
  for (url in candidates) {
    repeat(3) { attempt ->
      try {
        val client = androidItHttpClient()
        try {
          val response = client.get("$url/health/ready")
          if (response.status.isSuccess()) return url
          errors += "$url attempt ${attempt + 1}: HTTP ${response.status}"
        } finally {
          client.close()
        }
      } catch (e: Throwable) {
        errors += "$url attempt ${attempt + 1}: ${e::class.simpleName}: ${e.message}"
        delay(300.milliseconds)
      }
    }
  }
  error("checkpoint backend reachability: none of $candidates answered /health/ready. $errors")
}

@Serializable
internal data class CreatedAlterDto(val id: Int, val name: String? = null)

@Serializable
private data class UsernameBody(val username: String)

@Serializable
private data class PushTokenBody(val token: String)

@Serializable
private data class AlterNameBody(val name: String)

@Serializable
private data class AlterSecurityBody(
  @SerialName("security_level") val securityLevel: String,
)

@Serializable
private data class FrontStartBody(val id: Int)

internal suspend fun ensureUserExists(baseUrl: String, token: String, username: String) {
  val client = androidItHttpClient()
  try {
    val response = client.post("$baseUrl/api/settings/username") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(UsernameBody(username))
    }
    check(response.status.isSuccess()) {
      "checkpoint ensureUserExists: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun registerPushToken(baseUrl: String, token: String, pushToken: String) {
  val client = androidItHttpClient()
  try {
    val response = client.post("$baseUrl/api/settings/push-token") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(PushTokenBody(pushToken))
    }
    check(response.status.isSuccess()) {
      "checkpoint push-token: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun createAlterHttp(baseUrl: String, token: String, name: String): CreatedAlterDto {
  val client = androidItHttpClient()
  try {
    val response = client.post("$baseUrl/api/systems/me/alters") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(AlterNameBody(name))
    }
    check(response.status.isSuccess()) {
      "checkpoint createAlter: ${response.status} ${response.bodyAsText()}"
    }
    val parsed = globalSerializer.decodeFromString(
      APIResponse.serializer(CreatedAlterDto.serializer()),
      response.bodyAsText(),
    )
    return parsed.data ?: error("checkpoint createAlter: empty data ${response.bodyAsText()}")
  } finally {
    client.close()
  }
}

internal suspend fun setAlterFriendsOnly(baseUrl: String, token: String, alterId: Int) {
  val client = androidItHttpClient()
  try {
    val response = client.patch("$baseUrl/api/systems/me/alters/$alterId") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(AlterSecurityBody("friends_only"))
    }
    check(response.status.isSuccess()) {
      "checkpoint alter visibility: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun startFrontHttp(baseUrl: String, token: String, alterId: Int) {
  val client = androidItHttpClient()
  try {
    val response = client.post("$baseUrl/api/systems/me/front/start") {
      bearerAuth(token)
      contentType(ContentType.Application.Json)
      setBody(FrontStartBody(alterId))
    }
    check(response.status.isSuccess()) {
      "checkpoint startFront: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun sendFriendRequestHttp(baseUrl: String, token: String, friendSystemId: String) {
  val client = androidItHttpClient()
  try {
    val response = client.put("$baseUrl/api/friend-requests/$friendSystemId") {
      bearerAuth(token)
    }
    check(response.status.isSuccess()) {
      "checkpoint sendFriendRequest: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun acceptFriendRequestHttp(baseUrl: String, token: String, friendSystemId: String) {
  val client = androidItHttpClient()
  try {
    val response = client.post("$baseUrl/api/friend-requests/$friendSystemId/accept") {
      bearerAuth(token)
    }
    check(response.status.isSuccess()) {
      "checkpoint acceptFriendRequest: ${response.status} ${response.bodyAsText()}"
    }
  } finally {
    client.close()
  }
}

internal suspend fun fetchAndroidFirebaseConfigOrNull(baseUrl: String): JsonObject? {
  val client = androidItHttpClient()
  try {
    val response = client.get("$baseUrl/api/settings/firebase-config?platform=android")
    if (!response.status.isSuccess()) return null
    val root = globalSerializer.parseToJsonElement(response.bodyAsText())
    val data = (root as? JsonObject)?.get("data")
    return data as? JsonObject
  } finally {
    client.close()
  }
}

@Serializable
internal data class FcmSendAttemptDto(
  @SerialName("system_id") val systemId: String,
  @SerialName("friend_system_id") val friendSystemId: String? = null,
  @SerialName("skip_reason") val skipReason: String? = null,
  val title: String? = null,
  val body: String? = null,
  @SerialName("alter_ids") val alterIds: List<String> = emptyList(),
  @SerialName("token_count") val tokenCount: Int = 0,
  @SerialName("success_count") val successCount: Int = 0,
  @SerialName("failure_count") val failureCount: Int = 0,
  @SerialName("error_codes") val errorCodes: List<String> = emptyList(),
)

internal suspend fun fetchFcmSends(baseUrl: String, token: String): List<FcmSendAttemptDto> {
  val client = androidItHttpClient()
  try {
    val response = client.get("$baseUrl/api/internal/test/fcm-sends") {
      bearerAuth(token)
    }
    check(response.status.isSuccess()) {
      "checkpoint FCM outbox: ${response.status} ${response.bodyAsText()}"
    }
    val parsed = globalSerializer.decodeFromString(
      APIResponse.serializer(kotlinx.serialization.builtins.ListSerializer(FcmSendAttemptDto.serializer())),
      response.bodyAsText(),
    )
    return parsed.data ?: emptyList()
  } finally {
    client.close()
  }
}

internal suspend fun awaitFcmSend(
  baseUrl: String,
  token: String,
  fronterSystemId: String,
): FcmSendAttemptDto {
  val deadline = Clock.System.now() + 20.seconds
  var last: List<FcmSendAttemptDto> = emptyList()
  while (Clock.System.now() < deadline) {
    last = fetchFcmSends(baseUrl, token)
    val match = last.lastOrNull { it.systemId == fronterSystemId || it.systemId.contains(fronterSystemId) }
    if (match != null) return match
    delay(200.milliseconds)
  }
  error(
    "checkpoint FCM flush: no outbox row for system=$fronterSystemId. " +
      "attempts=${last.map { "${it.systemId}/${it.skipReason}/${it.title}" }}",
  )
}

internal fun JsonObject.stringOrNull(key: String): String? =
  this[key]?.jsonPrimitive?.content
