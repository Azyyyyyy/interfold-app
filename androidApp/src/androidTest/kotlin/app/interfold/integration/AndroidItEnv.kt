package app.interfold.integration

import androidx.test.platform.app.InstrumentationRegistry
import java.util.Properties

internal data class AndroidItEnv(
  val baseUrl: String,
  val bobToken: String,
  val bobSystemId: String,
  val aliceToken: String,
  val aliceSystemId: String,
)

internal fun loadAndroidItEnvOrNull(): AndroidItEnv? {
  val args = InstrumentationRegistry.getArguments()
  fun arg(key: String): String =
    args.getString(key)?.trim().orEmpty().ifEmpty {
      loadItProperties()?.getProperty(key)?.trim().orEmpty()
    }

  val baseUrl = arg("backendUrl").ifEmpty { arg("INTERFOLD_BACKEND_URL") }
  val bobToken = arg("backendToken").ifEmpty { arg("INTERFOLD_BACKEND_TOKEN") }
  val bobSystemId = arg("backendSystemId").ifEmpty { arg("INTERFOLD_BACKEND_SYSTEM_ID") }
  val aliceToken = arg("backendTokenA").ifEmpty { arg("INTERFOLD_BACKEND_TOKEN_A") }
  val aliceSystemId = arg("backendSystemIdA").ifEmpty { arg("INTERFOLD_BACKEND_SYSTEM_ID_A") }
  if (baseUrl.isEmpty() || bobToken.isEmpty() || aliceToken.isEmpty()) return null
  return AndroidItEnv(
    baseUrl = baseUrl.trimEnd('/'),
    bobToken = bobToken,
    bobSystemId = bobSystemId,
    aliceToken = aliceToken,
    aliceSystemId = aliceSystemId,
  )
}

private fun loadItProperties(): Properties? {
  return try {
    val stream = InstrumentationRegistry.getInstrumentation().context.assets
      .open("interfold-it.properties")
    Properties().apply { load(stream) }
  } catch (_: Exception) {
    try {
      val stream = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("interfold-it.properties")
      Properties().apply { load(stream) }
    } catch (_: Exception) {
      null
    }
  }
}
