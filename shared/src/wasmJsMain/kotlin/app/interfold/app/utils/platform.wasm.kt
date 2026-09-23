@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package app.interfold.app.utils

import app.interfold.PlatformEventBus
import app.interfold.app.Settings
import app.interfold.app.utils.bindings.CompactEncrypt
import app.interfold.app.utils.bindings.CryptoKey
import app.interfold.app.utils.bindings.crypto
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

actual val currentPlatform = DevicePlatform.Wasm

actual interface PlatformUtilities : CommonPlatformUtilities

actual interface PlatformDelegate

@JsFun("() => ({ name: 'RSA-OAEP', hash: 'SHA-256' })")
private external fun rsaKeyParams(): JsAny

@JsFun("() => ({ alg: 'RSA-OAEP-256', enc: 'A256GCM', cty: 'text/plain' })")
private external fun rsaJweHeader(): JsAny

@JsFun("() => ['encrypt']")
private external fun encryptUsage(): JsArray<JsString>

@JsFun("(string) => new TextEncoder().encode(string)")
private external fun encodeWithTextEncoder(string: String): Uint8Array

@JsFun("(buffer) => new TextDecoder().decode(buffer)")
private external fun decodeWithTextDecoder(buffer: JsAny): String

@JsFun("(array) => crypto.getRandomValues(array)")
private external fun randomizeArray(array: Uint8Array)

@JsFun("() => ({ name: 'AES-GCM' })")
private external fun aesGcmKeyParams(): JsAny

@JsFun("(iv) => ({ name: 'AES-GCM', iv: iv, tagLength: 128 })")
private external fun aesGcmAlgoParams(iv: Uint8Array): JsAny

@JsFun("() => ['encrypt']")
private external fun aesEncryptUsages(): JsArray<JsString>

@JsFun("() => ['decrypt']")
private external fun aesDecryptUsages(): JsArray<JsString>

@JsFun("(url) => { if ('serviceWorker' in navigator) { navigator.serviceWorker.register(url).then(reg => console.log('Service worker registered:', reg)).catch(err => console.warn('Service worker registration failed:', err)); } }")
private external fun registerServiceWorker(url: String)

/**
 * Delete the service worker's Cache Storage entry that holds the fetched Firebase
 * config. Fire-and-forget: the delete returns a Promise which we intentionally do not
 * await — the main-thread `localStorage` clear beside this call is what the app
 * actually depends on. The Cache Storage entry is a defensive nicety so background
 * push handlers don't fall back to a stale config from the previous server.
 */
@JsFun("() => { if ('caches' in self) { caches.delete('interfold-firebase-config-v1').catch(err => console.warn('Failed to delete Firebase config cache:', err)); caches.delete('octocon-firebase-config-v1').catch(err => console.warn('Failed to delete legacy Firebase config cache:', err)); } }")
private external fun deleteServiceWorkerFirebaseConfigCache()

/**
 * Delete the current Firebase JS SDK app instance if one has been initialised in this
 * page's runtime. Returns a Promise that resolves to `null` (either because the delete
 * succeeded or because there was nothing to delete). Used by
 * [PlatformUtilities.reinitPushNotifications] before it re-initialises against the new
 * endpoint's config.
 */
@JsFun(
  "() => { " +
    "if (typeof firebase === 'undefined' || !firebase.apps || firebase.apps.length === 0) return Promise.resolve(null); " +
    "return firebase.app().delete().catch(err => { console.warn('firebase.app().delete() failed:', err); return null; }); " +
    "}"
)
private external fun deleteFirebaseJsAppIfPresent(): Promise<JsAny?>

@JsFun("() => { if ('serviceWorker' in navigator) { navigator.serviceWorker.getRegistrations().then(registrations => { for (let registration of registrations) { registration.unregister(); } }); } }")
private external fun unregisterServiceWorkers()

@JsFun("() => window.matchMedia('(display-mode: standalone)').matches")
private external fun isStandalone(): Boolean

@JsFun("() => (typeof globalThis.__INTERFOLD_DEFAULT_API_ENDPOINT__ === 'string' ? globalThis.__INTERFOLD_DEFAULT_API_ENDPOINT__ : '')")
private external fun deploymentApiEndpoint(): String

/**
 * Settings for a cold web start. [serialized] is the localStorage payload, or
 * null when the browser has never saved settings. A deployment default from
 * `runtime-config.js` fills in only when that payload has no API endpoint.
 */
fun resolveWebClientSettings(serialized: String?): Settings {
  val stored = if (serialized.isNullOrBlank()) null else Settings.deserialize(serialized)
  return applyDeploymentApiEndpoint(stored, deploymentApiEndpoint())
}

@JsFun("(buffer) => new Uint8Array(buffer)")
private external fun jsUint8Array(buffer: JsAny): Uint8Array

private fun toUint8Array(bytes: ByteArray): Uint8Array {
    val ua = Uint8Array(bytes.size)
    for (i in bytes.indices) {
        ua[i] = bytes[i]
    }
    return ua
}

private fun Uint8Array.toByteArray(): ByteArray {
    return ByteArray(length) { this[it] }
}

private val alphabet = listOf(
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
    'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    '2', '3', '4', '5', '6', '7', '8', '9'
)

private val base64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

val platformUtilities = object : PlatformUtilities {
    override fun initialize(settings: Settings) {
        migrateLegacyWasmSettings()
        if (isAppInstalled()) {
            if (!settings.installServiceWorker) {
                saveSettings(settings.copy(installServiceWorker = true))
            } else {
                updateServiceWorkerRegistration(true)
            }
        } else {
            // Register the SW if either offline caching is enabled OR push
            // notifications are on — FCM's getToken hands us the same SW so we
            // reuse a single registration for both purposes.
            updateServiceWorkerRegistration(settings.installServiceWorker || settings.showPushNotifications)
        }
    }

    override fun isAppInstalled(): Boolean = isStandalone()

    override fun exitApplication(exitApplicationType: ExitApplicationType) {
        when (exitApplicationType) {
            // TODO: Make quick exit URL configurable on web?
            ExitApplicationType.QuickExit -> window.location.assign("https://google.com")
            ExitApplicationType.ForcedRestart -> window.location.reload()
        }
    }

    override fun saveSettings(settings: Settings) {
        migrateLegacyWasmSettings()
        val oldSettings = getSettings()
        localStorage.setItem(SETTINGS_LOCALSTORAGE_KEY, settings.serialize())

        val swNeededNow = settings.installServiceWorker || settings.showPushNotifications
        val swNeededBefore = (oldSettings?.installServiceWorker ?: false) ||
            (oldSettings?.showPushNotifications ?: false)
        if (swNeededNow != swNeededBefore) {
            updateServiceWorkerRegistration(swNeededNow)
        }
    }

    override fun clearServerConfigCache() {
        localStorage.removeItem("PUBLIC_KEY_PEM")
        localStorage.removeItem("PUBLIC_KEY_AT")
        localStorage.removeItem("FIREBASE_CONFIG_JSON")
        localStorage.removeItem("FIREBASE_CONFIG_AT")
        deleteServiceWorkerFirebaseConfigCache()
    }

    override suspend fun reinitPushNotifications(settings: Settings) {
        // In practice on wasm this hook rarely fires — the OAuth flow is a full page
        // reload, so the token appears in `initialSettings` at boot rather than
        // through `SettingsInterfaceImpl.setToken` at runtime. Kept for the
        // hypothetical same-page login flow and for symmetry with Android/iOS.
        try {
            deleteFirebaseJsAppIfPresent().await<JsAny?>()
        } catch (e: Throwable) {
            platformLog("PUSH", "Reinit: firebase.app().delete() failed (ignoring): $e")
        }

        if (!settings.showPushNotifications) return

        val token = tryRefreshWebFCMToken("${settings.apiEndpoint}/api") ?: return
        PlatformEventBus.flow.emit(PlatformEvent.PushNotificationTokenReceived(token))
    }

    fun updateServiceWorkerRegistration(enabled: Boolean) {
        if (enabled) {
            registerServiceWorker("/service-worker.js")
        } else {
            unregisterServiceWorkers()
        }
    }

    override fun showAlert(message: String) {
        // TODO: Implement a better alert system?
        window.alert(message)
    }

    override suspend fun recoveryCodeToJWE(recoveryCode: String, settings: Settings): String {
        try {
            val publicKey = PublicKeyProvider.getPublicKey("${settings.apiEndpoint}/api")
            val strippedKey = publicKey
                .replace(Regex("-----BEGIN.*?-----"), "")
                .replace(Regex("-----END.*?-----"), "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            val binaryKey = base64.decode(strippedKey)
            val key = crypto.subtle.importKey(
                "spki",
                toUint8Array(binaryKey),
                rsaKeyParams(),
                false,
                encryptUsage()
            ).await<CryptoKey>()

            val jwe = CompactEncrypt(encodeWithTextEncoder(recoveryCode))
                .setProtectedHeader(rsaJweHeader())
                .encrypt(key)
                .await<JsString>()

            platformLog("CRYPTO", "Encrypted recovery code")
            return jwe.toString()
        } catch (e: Exception) {
            platformLog("CRYPTO", "Failed to encrypt recovery code: $e")
            throw e
        }
    }

    override suspend fun generateRecoveryCode(settings: Settings): Pair<String, String> {
        platformLog("CRYPTO", "Creating recovery code")
        val array = Uint8Array(16)
        randomizeArray(array)

        val recoveryCode = List(16) { alphabet[array[it].toInt() and 31] }
            .joinToString("")
            .chunked(4)
            .joinToString("-")

        val jwe = recoveryCodeToJWE(recoveryCode, settings)
        return recoveryCode to jwe
    }

    private fun getSettings(): Settings? {
        migrateLegacyWasmSettings()
        val currentSettingsJson = localStorage.getItem(SETTINGS_LOCALSTORAGE_KEY)
        return if (currentSettingsJson != null) {
            try {
                resolveWebClientSettings(currentSettingsJson)
            } catch (e: Exception) {
                platformLog("SETTINGS", "Failed to deserialize settings in setupEncryptionKey: $e")
                return null
            }
        } else {
            resolveWebClientSettings(null)
        }
    }

    override suspend fun setupEncryptionKey(encryptionKey: String): Settings? {
        val currentSettings = getSettings() ?: return null

        // Persist the encryption key in IndexedDB via the web shim and store
        // a placeholder in settings so callers know it's persisted off-localStorage.
        val stored = webStoreEncryptionKey(encryptionKey)
        if (!stored) {
            // IDB write failed. Make sure we don't leave behind a localStorage flag
            // claiming the key is stored when it isn't (that previously forced users
            // to manually clear their IndexedDB to recover). Best-effort wipe any
            // partial IDB state from a prior buggy run too.
            try {
                webDeleteEncryptionKey()
            } catch (e: Throwable) {
                platformLog("SETTINGS", "Best-effort cleanup of IndexedDB encryption key failed: $e")
            }
            if (currentSettings.encryptedEncryptionKey != null) {
                saveSettings(currentSettings.copy(encryptedEncryptionKey = null))
            }
            return null
        }

        val updatedSettings = currentSettings.copy(encryptedEncryptionKey = "STORED_IN_INDEXEDDB")
        saveSettings(updatedSettings)
        return updatedSettings
    }

    override suspend fun getEncryptionKey(settings: Settings): String {
        val keyFromSettings = settings.encryptedEncryptionKey
        if (keyFromSettings != null && keyFromSettings != "STORED_IN_INDEXEDDB") {
            return keyFromSettings.trim()
        }

        val stored = webRetrieveEncryptionKey()
        if (stored != null) return stored.trim()

        // Self-heal: settings claim the key is in IndexedDB, but IDB has nothing.
        // This was the broken state previously produced by the silent IDB write failure
        // bug. Clear the stale flag (and any partial IDB residue) so the next setup
        // attempt isn't blocked and the user doesn't have to manually clear their
        // IndexedDB to recover.
        if (keyFromSettings == "STORED_IN_INDEXEDDB") {
            platformLog(
                "SETTINGS",
                "encryptedEncryptionKey was 'STORED_IN_INDEXEDDB' but IndexedDB had no key; clearing stale flag"
            )
            try {
                webDeleteEncryptionKey()
            } catch (e: Throwable) {
                platformLog("SETTINGS", "Best-effort cleanup of IndexedDB encryption key failed: $e")
            }
            saveSettings(settings.copy(encryptedEncryptionKey = null))
        }
        throw IllegalStateException("Encryption key not found")
    }

    override fun decryptEncryptionKey(encryptedEncryptionKey: String): String {
        return base64.decode(encryptedEncryptionKey).decodeToString().trim()
    }

    override suspend fun encryptData(data: String, settings: Settings): String {
        try {
            val keyString = getEncryptionKey(settings)
            val keyBytes = base64.decode(keyString.trim())

            val cryptoKey = crypto.subtle.importKey(
                "raw",
                toUint8Array(keyBytes),
                aesGcmKeyParams(),
                false,
                aesEncryptUsages()
            ).await<CryptoKey>()

            val iv = Uint8Array(12)
            randomizeArray(iv)

            val encryptedBuffer = crypto.subtle.encrypt(
                aesGcmAlgoParams(iv),
                cryptoKey,
                toUint8Array(data.encodeToByteArray())
            ).await<JsAny>()

            val encryptedBytes = jsUint8Array(encryptedBuffer).toByteArray()

            // Web Crypto appends the tag to the ciphertext
            val tagLength = 16
            val ciphertext = encryptedBytes.copyOfRange(0, encryptedBytes.size - tagLength)
            val tag = encryptedBytes.copyOfRange(encryptedBytes.size - tagLength, encryptedBytes.size)

            val ivBase64 = base64.encode(iv.toByteArray())
            val ciphertextBase64 = base64.encode(ciphertext)
            val tagBase64 = base64.encode(tag)

            val result = "enc|$ivBase64|$ciphertextBase64|$tagBase64"
            return result
        } catch (e: Exception) {
            platformLog("CRYPTO", "Failed to encrypt data: ${e.message}")
            platformLog("CRYPTO", "Exception type: ${e::class.simpleName}")
            platformLog("CRYPTO", "Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    override suspend fun decryptData(data: String, settings: Settings): String {
        val parts = data.split("|")
        require(data.startsWith("enc|") && parts.size == 4) { "Invalid encrypted data format" }

        try {
            val iv = toUint8Array(base64.decode(parts[1].trim()))
            val ciphertext = base64.decode(parts[2].trim())
            val tag = base64.decode(parts[3].trim())

            val keyString = getEncryptionKey(settings)
            val keyBytes = base64.decode(keyString.trim())

            val cryptoKey = crypto.subtle.importKey(
                "raw",
                toUint8Array(keyBytes),
                aesGcmKeyParams(),
                false,
                aesDecryptUsages()
            ).await<CryptoKey>()

            // Concatenate ciphertext and tag back for Web Crypto
            val encryptedBytes = ciphertext + tag

            val decryptedBuffer = crypto.subtle.decrypt(
                aesGcmAlgoParams(iv),
                cryptoKey,
                toUint8Array(encryptedBytes)
            ).await<JsAny>()

            return decodeWithTextDecoder(decryptedBuffer)
        } catch (e: Exception) {
            platformLog("CRYPTO", "Failed to decrypt data: $e")
            throw IllegalStateException("Failed to decrypt data", e)
        }
    }

    override fun openURL(
        url: String,
        colorSchemeParams: ColorSchemeParams,
        webURLOpenBehavior: WebURLOpenBehavior
    ) {
        when (webURLOpenBehavior) {
            WebURLOpenBehavior.NewTab -> window.open(url, "_blank")
            WebURLOpenBehavior.SameTab -> window.location.assign(url)
            WebURLOpenBehavior.PopupWindow -> window.open(url, "_blank", "popup=true")
        }
    }

    override fun openCloudflareAccessSession(
        url: String,
        apiBaseUrl: String,
        silent: Boolean,
        onAccessJwt: (String) -> Unit,
        onFinished: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        // Same-tab navigation keeps Access cookies in the browser jar for fetch/WS.
        // Mirror CF_Authorization into settings when readable (often HttpOnly → null).
        val existing = extractCfAuthorizationFromDocumentCookie()
        if (existing != null) {
            onAccessJwt(existing)
        }
        if (silent) {
            // Trigger CF's silent app-token rotation via a same-origin navigation.
            window.location.assign(apiBaseUrl.trimEnd('/'))
            onFinished()
            return
        }
        window.location.assign(url)
        onFinished()
    }

    // Stubs: not implemented on web
    override fun performAdditionalPushNotificationSetup() = Unit
    override fun updateWidgets(sessionInvalidated: Boolean) = Unit
}

private fun extractCfAuthorizationFromDocumentCookie(): String? {
    return try {
        app.interfold.app.api.extractCfAuthorizationCookie(kotlinx.browser.document.cookie)
    } catch (_: Exception) {
        null
    }
}

const val SETTINGS_LOCALSTORAGE_KEY = "interfold_settings"
private const val LEGACY_SETTINGS_LOCALSTORAGE_KEY = "octocon_settings"

fun migrateLegacyWasmSettings() {
  if (localStorage.getItem(SETTINGS_LOCALSTORAGE_KEY) != null) {
    localStorage.removeItem(LEGACY_SETTINGS_LOCALSTORAGE_KEY)
    return
  }
  val legacy = localStorage.getItem(LEGACY_SETTINGS_LOCALSTORAGE_KEY) ?: return
  localStorage.setItem(SETTINGS_LOCALSTORAGE_KEY, legacy)
  localStorage.removeItem(LEGACY_SETTINGS_LOCALSTORAGE_KEY)
}

actual object BuildConfig : BuildConfigInterface {
    override fun isDebug(): Boolean {
        // Check for explicit debug flag in localStorage
        val debugFlag = localStorage.getItem("INTERFOLD_DEBUG")
        if (debugFlag != null) {
            return debugFlag.toBoolean()
        }

        // Auto-detect: running on localhost or with query parameter means debug mode
        val hostname = window.location.hostname
        val isLocalhost = hostname == "localhost" || hostname == "127.0.0.1" || hostname.startsWith("192.168.")
        val hasDebugParam = window.location.search.contains("debug=true")

        val isDebug = isLocalhost || hasDebugParam
        if (isDebug) {
            platformLog("BUILD-CONFIG", "Running in debug mode (hostname=$hostname, hasDebugParam=$hasDebugParam)")
        }

        return isDebug
    }
}

actual fun buildRedirectUri(path: String): String {
    val origin = window.location.origin
    return "$origin/$path"
}
