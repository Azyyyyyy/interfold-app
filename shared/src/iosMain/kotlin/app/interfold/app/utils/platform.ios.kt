package app.interfold.app.utils

import app.interfold.app.FirebaseIOSOptionsBridge
import app.interfold.app.Settings
import app.interfold.app.clearStoredFirebaseConfig
import app.interfold.app.clearStoredPublicKey
import app.interfold.app.getSettingsFromKeychain
import app.interfold.app.saveSettingsToKeychain
import app.interfold.app.saveStoredFirebaseConfig
import com.liftric.kvault.KVault
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import platform.Foundation.NSData
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.SafariServices.SFSafariViewController
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.posix.arc4random_uniform
import platform.posix.exit
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

actual val currentPlatform = DevicePlatform.iOS

@Suppress("unused") // Used in Swift
fun getInterfoldPublicKey(endpoint: String): String {
  return runBlocking(ioDispatcher) {
    PublicKeyProvider.getPublicKey(endpoint)
  }
}

actual interface PlatformUtilities : CommonPlatformUtilities {
  var injectedPlatformDelegate: PlatformDelegate?

  fun injectPlatformDelegate(platformDelegate: PlatformDelegate) {
    injectedPlatformDelegate = platformDelegate
  }
}

actual interface PlatformDelegate {
  fun recoveryCodeToJWE(recoveryCode: String, endpoint: String): String

  fun encryptData(key: NSData, iv: NSData, plainText: String): NSData
  fun decryptData(key: NSData, iv: NSData, cipherText: NSData, tag: NSData): String?

  fun updateWidgets(sessionInvalidated: Boolean)

  /**
   * Tear down the current `FirebaseApp` (async, via `FirebaseApp.app()?.delete { }` on
   * the main thread), then `FirebaseApp.configure(options:)` with the new options and
   * re-attach the `MessagingDelegate`. Fire-and-forget from the Kotlin caller's
   * perspective — the Swift implementation dispatches the async work internally, and
   * the token flows back naturally through the existing `MessagingDelegate` →
   * `providePushNotificationToken` path.
   */
  fun reconfigureFirebase(bridge: FirebaseIOSOptionsBridge)
}



var sfSafariViewController: SFSafariViewController? = null

val encryptionVault = KVault("interfold_encryption_key", accessibility = KVault.Accessible.WhenUnlockedThisDeviceOnly)

@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
val platformUtilities = object : PlatformUtilities {
  override var injectedPlatformDelegate: PlatformDelegate? = null

  private val alphabet = listOf(
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
    'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    '2', '3', '4', '5', '6', '7', '8', '9'
  )

  override fun exitApplication(exitApplicationType: ExitApplicationType) {
    exit(0)
  }

  override fun saveSettings(settings: Settings) {
    saveSettingsToKeychain(settings)
  }

  override fun clearServerConfigCache() {
    clearStoredPublicKey()
    clearStoredFirebaseConfig()
  }

  override suspend fun reinitPushNotifications(settings: Settings) {
    val endpoint = "${settings.apiEndpoint}/api"

    val config = try {
      FirebaseConfigProvider.getConfig(FirebasePlatform.IOS, endpoint) as? FirebaseConfig.IOS
    } catch (e: Exception) {
      NSLog("Reinit: failed to fetch Firebase config: $e")
      null
    } ?: run {
      NSLog("Reinit: no Firebase config available; skipping reconfigure")
      return
    }

    try {
      val json = globalSerializer.encodeToString(config)
      saveStoredFirebaseConfig(json, Clock.System.now().toEpochMilliseconds())
    } catch (e: Exception) {
      NSLog("Reinit: failed to persist Firebase config to KVault (continuing anyway): $e")
    }

    val delegate = injectedPlatformDelegate ?: run {
      NSLog("Reinit: PlatformDelegate not injected; skipping Firebase reconfigure")
      return
    }

    // Kick off the Swift-side delete + reconfigure. The token flows back through the
    // existing MessagingDelegate → providePushNotificationToken pipeline; no manual
    // fetch needed here (and no gate on showPushNotifications — if push is off,
    // Firebase still needs to be bound to the correct project for when the user
    // later toggles push on).
    delegate.reconfigureFirebase(FirebaseIOSOptionsBridge(config))
  }

  override fun showAlert(message: String) {
    val viewController =
      UIApplication.sharedApplication.keyWindow?.rootViewController?.modalViewController

    val alert = UIAlertController.alertControllerWithTitle(
      title = null,
      message = message,
      preferredStyle = UIAlertControllerStyleAlert
    )

    viewController?.presentViewController(alert, animated = true, completion = null)
  }

  override suspend fun recoveryCodeToJWE(recoveryCode: String, settings: Settings): String {
    require(injectedPlatformDelegate != null) {
      "PlatformDelegate must be injected before calling recoveryCodeToJWE"
    }
    return injectedPlatformDelegate!!.recoveryCodeToJWE(recoveryCode, "${settings.apiEndpoint}/api")
  }

  override suspend fun generateRecoveryCode(settings: Settings): Pair<String, String> {
    val recoveryCode =
      List(16) { alphabet[arc4random_uniform(alphabet.size.toUInt()).toInt()] }
        .joinToString("")
        .chunked(4)
        .joinToString("-")

    val jwe = recoveryCodeToJWE(recoveryCode, settings)
    return recoveryCode to jwe
  }

  override suspend fun setupEncryptionKey(encryptionKey: String): Settings {
    encryptionVault.set("encryption_key", encryptionKey)

    val settings = getSettingsFromKeychain().copy(encryptedEncryptionKey = "STORED IN KEYCHAIN")
    saveSettings(settings)
    return settings
  }

  override suspend fun getEncryptionKey(settings: Settings): String {
    return encryptionVault.string("encryption_key")!!
  }

  override fun decryptEncryptionKey(encryptedEncryptionKey: String): String {
    return Base64.decode(encryptedEncryptionKey).decodeToString()
  }

  private fun generateIV(): ByteArray = usePinned {
    val iv = ByteArray(12)
    SecRandomCopyBytes(kSecRandomDefault, iv.size.toULong(), iv.refTo(0))

    return iv
  }

  override suspend fun encryptData(data: String, settings: Settings): String {
    val iv = generateIV()
    val key = Base64.decode(this.getEncryptionKey(settings))

    val cipherText = injectedPlatformDelegate!!.encryptData(key.toNSData(), iv.toNSData(), data).toByteArray()

    val tagLength = 16
    val tag = cipherText.copyOfRange(cipherText.size - tagLength, cipherText.size)
    val actualCipherText = cipherText.copyOfRange(0, cipherText.size - tagLength)

    val ivBase64 = Base64.encode(iv)
    val cipherTextBase64 = Base64.encode(actualCipherText)
    val tagBase64 = Base64.encode(tag)

    val result = "enc|$ivBase64|$cipherTextBase64|$tagBase64"
    return result
  }

  override suspend fun decryptData(data: String, settings: Settings): String {
    require (injectedPlatformDelegate != null) {
      "PlatformDelegate must be injected before calling decryptData"
    }

    val parts = data.replace("\n", "").split("|")

    require(data.startsWith("enc|") && parts.size == 4) { "Invalid encrypted data format" }

    val iv = Base64.decode(parts[1])
    val cipherText = Base64.decode(parts[2])
    val tag = Base64.decode(parts[3])

    val key = Base64.decode(this.getEncryptionKey(settings))

    val result = injectedPlatformDelegate!!.decryptData(key.toNSData(), iv.toNSData(), cipherText.toNSData(), tag.toNSData())
    if(result == null) {
      throw IllegalStateException("Failed to decrypt data")
    } else {
      return result
    }
  }

  override fun openURL(
    url: String,
    colorSchemeParams: ColorSchemeParams,
    webURLOpenBehavior: WebURLOpenBehavior
  ) {
    val nsUrl = NSURL(string = url)

    sfSafariViewController = SFSafariViewController(nsUrl)

    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
      sfSafariViewController!!,
      animated = true,
      completion = null
    )
  }

  override fun performAdditionalPushNotificationSetup() {
    UIApplication.sharedApplication.registerForRemoteNotifications()
  }

  override fun updateWidgets(sessionInvalidated: Boolean) {
    require (injectedPlatformDelegate != null) {
      "PlatformDelegate must be injected before calling updateWidgets"
    }
    injectedPlatformDelegate?.updateWidgets(sessionInvalidated)
  }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray = ByteArray(this@toByteArray.length.toInt()).apply {
  usePinned {
    memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
  }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData = memScoped {
  NSData.create(bytes = allocArrayOf(this@toNSData),
    length = this@toNSData.size.toULong())
}

actual object BuildConfig : BuildConfigInterface {
  @OptIn(ExperimentalNativeApi::class)
  override fun isDebug(): Boolean {
    return Platform.isDebugBinary
  }
}

actual fun buildRedirectUri(path: String): String = "interfold://$path"
