package app.interfold.app

import app.interfold.app.utils.globalSerializer
import app.interfold.app.utils.encryptionVault
import com.liftric.kvault.KVault

private val vault = KVault("interfold_settings", accessGroup = "AVJM9TZ9VF.app.interfold.InterfoldApp.Keychain", accessibility = KVault.Accessible.WhenUnlockedThisDeviceOnly)

fun clearKeychain() {
  vault.deleteObject("settings")
  encryptionVault.deleteObject("encryption_key")
}

fun getSettingsFromKeychain(): Settings {
  vault.string("settings")?.let { return Settings.deserialize(it) }

  return Settings()
}

fun saveSettingsToKeychain(settings: Settings) {
  vault.set("settings", globalSerializer.encodeToString(settings))
}

fun getStoredPublicKey(): Pair<String, Long>? {
  val key = vault.string("public_key") ?: return null
  val at = vault.string("public_key_at")?.toLongOrNull() ?: return null
  return key to at
}

fun saveStoredPublicKey(key: String, atMillis: Long) {
  vault.set("public_key", key)
  vault.set("public_key_at", atMillis.toString())
}

fun getStoredFirebaseConfig(): Pair<String, Long>? {
  val json = vault.string("firebase_config") ?: return null
  val at = vault.string("firebase_config_at")?.toLongOrNull() ?: return null
  return json to at
}

fun saveStoredFirebaseConfig(json: String, atMillis: Long) {
  vault.set("firebase_config", json)
  vault.set("firebase_config_at", atMillis.toString())
}

fun clearStoredPublicKey() {
  vault.deleteObject("public_key")
  vault.deleteObject("public_key_at")
}

fun clearStoredFirebaseConfig() {
  vault.deleteObject("firebase_config")
  vault.deleteObject("firebase_config_at")
}