package app.interfold.app.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Firebase JS / MCP dumps camelCase; the API's
 * [FirebaseClientSecretsPostConfigure] deserialises snake_case records.
 */
internal fun parseFirebaseJsonObject(raw: String): kotlinx.serialization.json.JsonObject? {
  val cleaned = raw.trim().removePrefix("\uFEFF").trim()
  val start = cleaned.indexOf('{')
  if (start < 0) return null
  val json = cleaned.substring(start)
  val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
  val nested = root["result"]
  return when (nested) {
    is kotlinx.serialization.json.JsonObject -> nested
    else -> root
  }
}

internal fun toFirebaseWebClientSeed(raw: String): String? {
  val obj = parseFirebaseJsonObject(raw) ?: return null
  fun pick(vararg keys: String): String? {
    for (key in keys) {
      val value = obj[key]?.jsonPrimitive?.contentOrNull?.trim()
      if (!value.isNullOrEmpty()) return value
    }
    return null
  }
  val apiKey = pick("api_key", "apiKey") ?: return null
  val authDomain = pick("auth_domain", "authDomain") ?: return null
  val projectId = pick("project_id", "projectId") ?: return null
  val messagingSenderId = pick("messaging_sender_id", "messagingSenderId") ?: return null
  val appId = pick("app_id", "appId") ?: return null
  val vapidKey = pick("vapid_key", "vapidKey") ?: return null
  val storageBucket = pick("storage_bucket", "storageBucket")
  return buildJsonObject {
    put("api_key", JsonPrimitive(apiKey))
    put("auth_domain", JsonPrimitive(authDomain))
    put("project_id", JsonPrimitive(projectId))
    put("storage_bucket", JsonPrimitive(storageBucket ?: ""))
    put("messaging_sender_id", JsonPrimitive(messagingSenderId))
    put("app_id", JsonPrimitive(appId))
    put("vapid_key", JsonPrimitive(vapidKey))
  }.toString()
}

internal fun toFirebaseAndroidClientSeed(raw: String): String? {
  val obj = parseFirebaseJsonObject(raw) ?: return null
  fun pick(map: kotlinx.serialization.json.JsonObject, vararg keys: String): String? {
    for (key in keys) {
      val value = (map[key] as? JsonPrimitive)?.contentOrNull?.trim()
      if (!value.isNullOrEmpty()) return value
    }
    return null
  }
  val flatApi = pick(obj, "api_key", "apiKey")
  val flatApp = pick(
    obj,
    "application_id",
    "applicationId",
    "google_app_id",
    "mobilesdk_app_id",
    "app_id",
    "appId",
  )
  val flatProject = pick(obj, "project_id", "projectId")
  val flatSender = pick(
    obj,
    "gcm_sender_id",
    "gcmSenderId",
    "messaging_sender_id",
    "messagingSenderId",
    "project_number",
    "projectNumber",
  )
  if (flatApi != null && flatApp != null && flatProject != null && flatSender != null) {
    return buildJsonObject {
      put("api_key", JsonPrimitive(flatApi))
      put("application_id", JsonPrimitive(flatApp))
      put("project_id", JsonPrimitive(flatProject))
      put("gcm_sender_id", JsonPrimitive(flatSender))
      put("storage_bucket", JsonPrimitive(pick(obj, "storage_bucket", "storageBucket") ?: ""))
    }.toString()
  }
  val projectInfo = obj["project_info"]?.jsonObject ?: return null
  val client = obj["client"]?.let { el ->
    (el as? JsonArray)?.firstOrNull()?.jsonObject
      ?: el.jsonObject
  } ?: return null
  val apiKey = client["api_key"]
    ?.let { el -> (el as? JsonArray)?.firstOrNull()?.jsonObject ?: el.jsonObject }
    ?.let { pick(it, "current_key", "api_key", "apiKey") }
    ?: return null
  val appId = client["client_info"]?.jsonObject?.let {
    pick(it, "mobilesdk_app_id", "application_id")
  } ?: return null
  val projectId = pick(projectInfo, "project_id", "projectId") ?: return null
  val sender = pick(projectInfo, "project_number", "gcm_sender_id") ?: return null
  return buildJsonObject {
    put("api_key", JsonPrimitive(apiKey))
    put("application_id", JsonPrimitive(appId))
    put("project_id", JsonPrimitive(projectId))
    put("gcm_sender_id", JsonPrimitive(sender))
    put("storage_bucket", JsonPrimitive(pick(projectInfo, "storage_bucket") ?: ""))
  }.toString()
}

internal fun envOrBlank(name: String): String? =
  System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
