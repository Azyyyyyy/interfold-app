package app.interfold.app.integration

import java.io.File

internal fun interfoldItFcmServiceAccountJson(): String? =
  envOrDotInterfoldFile(FCM_SA_ENV, "it-fcm-sa.json")

internal fun interfoldItFirebaseWebJson(): String? =
  envOrDotInterfoldFile(FCM_WEB_ENV, "it-fcm-web.json")

internal fun interfoldItFirebaseAndroidJson(): String? =
  envOrDotInterfoldFile(FCM_ANDROID_ENV, "it-fcm-android.json")

private fun envOrDotInterfoldFile(envName: String, fileName: String): String? {
  envOrBlank(envName)?.let { return it }
  val home = System.getProperty("user.home") ?: return null
  val file = File(home, ".interfold${File.separator}$fileName")
  if (!file.isFile) return null
  return file.readText().trim().takeIf { it.isNotEmpty() }
}
