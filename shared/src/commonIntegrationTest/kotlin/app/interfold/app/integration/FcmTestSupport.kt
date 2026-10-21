package app.interfold.app.integration

import app.interfold.app.api.model.APIResponse
import app.interfold.app.utils.globalSerializer
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
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val FCM_SA_ENV = "INTERFOLD_IT_FCM_SERVICE_ACCOUNT_JSON"
internal const val FCM_WEB_ENV = "INTERFOLD_IT_FIREBASE_WEB_JSON"
internal const val FCM_ANDROID_ENV = "INTERFOLD_IT_FIREBASE_ANDROID_JSON"
internal const val FCM_IOS_ENV = "INTERFOLD_IT_FIREBASE_IOS_JSON"

internal const val FCM_SKIP_NULL = "null_fcm"
internal const val FCM_SKIP_NO_TOKENS = "no_friend_tokens"
internal const val FCM_SKIP_NO_VISIBLE = "no_visible_fronts"
internal const val FCM_SKIP_MESSAGING = "messaging_unavailable"

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

@Serializable
internal data class CreatedAlterDto(
  val id: Int,
  val name: String? = null,
)

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


internal suspend fun registerPushToken(baseUrl: String, token: String, pushToken: String) {
  val client = createJsonHttpClient()
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
  val client = createJsonHttpClient()
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
  val client = createJsonHttpClient()
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
  val client = createJsonHttpClient()
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
  val client = createJsonHttpClient()
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
  val client = createJsonHttpClient()
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

/** False when the API image has no TEST_HOOKS outbox (404). */
internal suspend fun fcmOutboxPresent(baseUrl: String): Boolean {
  val client = createJsonHttpClient()
  return try {
    val response = client.get("$baseUrl/api/internal/test/fcm-sends")
    response.status.value != 404
  } catch (_: Exception) {
    false
  } finally {
    client.close()
  }
}

internal suspend fun fetchFcmSends(baseUrl: String, token: String): List<FcmSendAttemptDto> {
  val client = createJsonHttpClient()
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
  timeout: Duration = 20.seconds,
): FcmSendAttemptDto {
  val deadline = kotlin.time.Clock.System.now() + timeout
  var last: List<FcmSendAttemptDto> = emptyList()
  while (kotlin.time.Clock.System.now() < deadline) {
    last = fetchFcmSends(baseUrl, token)
    val match = last.lastOrNull { it.systemId == fronterSystemId || it.systemId.contains(fronterSystemId) }
    if (match != null) return match
    delay(200.milliseconds)
  }
  error(
    "checkpoint FCM flush: no outbox row for system=$fronterSystemId within $timeout. " +
      "attempts=${last.map { "${it.systemId}/${it.skipReason}/${it.title}" }}",
  )
}

internal suspend fun fetchWebFirebaseConfigOrNull(baseUrl: String): JsonObject? {
  val client = createJsonHttpClient()
  try {
    val response = client.get("$baseUrl/api/settings/firebase-config?platform=web")
    if (!response.status.isSuccess()) return null
    val root = globalSerializer.parseToJsonElement(response.bodyAsText())
    val data = (root as? kotlinx.serialization.json.JsonObject)?.get("data")
    return data as? JsonObject
  } finally {
    client.close()
  }
}

internal fun JsonObject.stringOrNull(key: String): String? =
  this[key]?.jsonPrimitive?.content
