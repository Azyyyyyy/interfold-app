package app.interfold.app.api

import app.interfold.app.api.model.APIResponse
import app.interfold.app.utils.AssumedUTCInstantSerializer
import app.interfold.app.utils.globalSerializer
import app.interfold.app.utils.ioDispatcher
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.time.Instant
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ImportOperationResponse(
  @SerialName("operation_id")
  val operationId: String,
  val status: String,
  @Serializable(with = AssumedUTCInstantSerializer::class)
  @SerialName("started_at")
  val startedAt: Instant? = null,
) {
  val isRunning: Boolean
    get() = status.equals("running", ignoreCase = true)

  val isQueued: Boolean
    get() = status.equals("queued", ignoreCase = true)
}

data class AccountExportResult(
  val bytes: ByteArray,
  val fileName: String,
)

fun exportFileNameForFormat(format: String): String =
  when (format.lowercase()) {
    "pk" -> "interfold_export_pk.json"
    "octocon" -> "interfold_export_octocon.json"
    else -> "interfold_export_${format.lowercase()}.json"
  }

internal fun parseFormatsListBody(body: String): List<String> {
  val response = globalSerializer.decodeFromString(
    APIResponse.serializer(ListSerializer(String.serializer())),
    body,
  )
  return response.data.orEmpty().map { it.lowercase() }
}

internal fun parseImportOperationBody(body: String): ImportOperationResponse {
  val response = globalSerializer.decodeFromString(
    APIResponse.serializer(ImportOperationResponse.serializer()),
    body,
  )
  return response.data ?: error("Missing import operation data")
}

/**
 * Which OAuth link/unlink rows to show in settings, given [login-methods] and
 * whether Cloudflare Access is the only login path.
 */
data class SettingsLoginProviderVisibility(
  val showGoogle: Boolean,
  val showDiscord: Boolean,
  val showApple: Boolean,
)

fun settingsLoginProviderVisibility(methods: LoginMethods): SettingsLoginProviderVisibility {
  if (methods.cloudflare) {
    return SettingsLoginProviderVisibility(
      showGoogle = false,
      showDiscord = false,
      showApple = false,
    )
  }
  return SettingsLoginProviderVisibility(
    showGoogle = methods.google,
    showDiscord = methods.discord,
    showApple = methods.apple,
  )
}

suspend fun fetchImportFormats(endpoint: String, token: String): List<String> =
  withContext(ioDispatcher) {
    val response = get(endpoint, token, "settings/import/formats")
    if (!response.status.isSuccess()) {
      error("import formats HTTP ${response.status.value}")
    }
    parseFormatsListBody(response.bodyAsText())
  }

suspend fun fetchExportFormats(endpoint: String, token: String): List<String> =
  withContext(ioDispatcher) {
    val response = get(endpoint, token, "settings/export/formats")
    if (!response.status.isSuccess()) {
      error("export formats HTTP ${response.status.value}")
    }
    parseFormatsListBody(response.bodyAsText())
  }

suspend fun exportAccount(endpoint: String, token: String, format: String): AccountExportResult =
  withContext(ioDispatcher) {
    val response = get(endpoint, token, "settings/export?format=$format")
    if (!response.status.isSuccess()) {
      val raw = runCatching { response.bodyAsText() }.getOrNull()
      val apiError = raw?.let {
        runCatching {
          globalSerializer.decodeFromString(
            APIResponse.serializer(String.serializer().nullable),
            it,
          )
        }.getOrNull()?.error
      }
      error(apiError ?: "export HTTP ${response.status.value}")
    }
    AccountExportResult(
      bytes = response.body(),
      fileName = exportFileNameForFormat(format),
    )
  }

suspend fun importAccount(
  endpoint: String,
  token: String,
  format: String,
  bodyJson: String,
): ImportOperationResponse =
  withContext(ioDispatcher) {
    val response = post(endpoint, token, "settings/import?format=$format", bodyJson)
    if (response.status.value !in 200..299) {
      val raw = runCatching { response.bodyAsText() }.getOrNull()
      val apiError = raw?.let {
        runCatching {
          globalSerializer.decodeFromString(
            APIResponse.serializer(String.serializer().nullable),
            it,
          )
        }.getOrNull()?.error
      }
      error(apiError ?: "import HTTP ${response.status.value}")
    }
    parseImportOperationBody(response.bodyAsText())
  }
