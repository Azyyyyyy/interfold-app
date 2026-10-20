package app.interfold.app.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import app.interfold.kotlix.ktor.AndroidHttpGates
import app.interfold.kotlix.ktor.HostGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 17 (API 37) blocks TCP and UDP to local-network addresses unless the
 * app holds [PERMISSION]. That permission replaced the Android 16
 * `NEARBY_WIFI_DEVICES` stand-in. The system still shows it as Nearby devices,
 * because it belongs to that permission group. A server URL the user types
 * cannot go through the system device picker, so this grant is what lets the
 * app reach an API on the LAN.
 *
 * Public endpoints never prompt. The prompt runs only for a local-network
 * destination.
 */
object LocalNetworkAccess {
  const val PERMISSION: String = "android.permission.ACCESS_LOCAL_NETWORK"
  const val DENIED_MESSAGE: String =
    "Allow Nearby devices so Interfold can reach a server on your local network."

  private val lock = Any()
  private val localHosts = ConcurrentHashMap<String, Boolean>()

  @Volatile
  private var appContext: Context? = null

  @Volatile
  private var launch: (() -> Unit)? = null

  private var currentActivity: WeakReference<Activity>? = null
  private var pending: CompletableDeferred<Boolean>? = null

  /** After a denial, background calls fail until the user tries the server again. */
  @Volatile
  private var suppressPrompt: Boolean = false

  fun noteContext(context: Context) {
    appContext = context.applicationContext
  }

  fun attach(activity: Activity, requestPermission: () -> Unit) {
    noteContext(activity)
    currentActivity = WeakReference(activity)
    launch = requestPermission
    ensureGateInstalled()
  }

  fun ensureGateInstalled() {
    AndroidHttpGates.localNetwork = HostGate { host -> allowBlocking(host) }
  }

  fun onPermissionResult(granted: Boolean) {
    if (!granted) suppressPrompt = true
    val waiting = synchronized(lock) {
      pending.also { pending = null }
    }
    waiting?.complete(granted)
  }

  suspend fun ensureForUrl(url: String, userInitiated: Boolean = true): Boolean {
    val host = extractHost(url) ?: return true
    return ensureHost(host, userInitiated)
  }

  /** Literal LAN addresses and `.local` names. DNS names are classified at request time. */
  fun endpointNeedsPrompt(url: String): Boolean {
    val host = extractHost(url) ?: return false
    return isLocalNetworkAddress(host) == true
  }

  /**
   * OkHttp calls this on a worker thread. A call already on the main thread
   * cannot wait for the permission dialog, so it fails closed when the
   * destination is known to be local and the grant is missing.
   */
  fun allowBlocking(host: String): Boolean {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      if (Build.VERSION.SDK_INT < 37) return true
      val context = appContext
      if (context != null && hasPermission(context)) return true
      val key = host.lowercase()
      val local = localHosts[key] ?: isLocalNetworkAddress(host)?.also { localHosts[key] = it }
      return local != true
    }
    return runBlocking { ensureHost(host, userInitiated = false) }
  }

  private suspend fun ensureHost(host: String, userInitiated: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < 37) return true
    if (!hostNeedsLocalNetwork(host)) return true
    val context = appContext
    if (context != null && hasPermission(context)) return true
    if (!userInitiated && suppressPrompt) return false
    if (context == null || launch == null) return false

    val created = CompletableDeferred<Boolean>()
    val decision = synchronized(lock) {
      val current = appContext
      if (current != null && hasPermission(current)) return true
      pending?.let { return@synchronized it to false }
      if (launch == null) return false
      suppressPrompt = false
      pending = created
      created to true
    }
    val (deferred, shouldLaunch) = decision
    if (shouldLaunch) {
      try {
        launchOnMain()
      } catch (e: Exception) {
        Log.w("INTERFOLD", "Local network permission request failed: $e")
        onPermissionResult(false)
      }
    }
    return deferred.await()
  }

  private fun launchOnMain() {
    val activity = currentActivity?.get()
    val request = launch ?: return
    if (activity == null) {
      request()
      return
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      if (!activity.isDestroyed) request() else onPermissionResult(false)
    } else {
      activity.runOnUiThread {
        if (!activity.isDestroyed) request() else onPermissionResult(false)
      }
    }
  }

  private suspend fun hostNeedsLocalNetwork(host: String): Boolean {
    val key = host.lowercase()
    localHosts[key]?.let { return it }
    val literal = isLocalNetworkAddress(host)
    if (literal != null) {
      localHosts[key] = literal
      return literal
    }
    val resolved = withContext(Dispatchers.IO) {
      try {
        InetAddress.getAllByName(host).any { isLocalNetworkIp(it.address) }
      } catch (_: Exception) {
        null
      }
    }
    if (resolved != null) localHosts[key] = resolved
    return resolved == true
  }

  private fun hasPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

  private fun extractHost(urlOrHost: String): String? {
    val raw = urlOrHost.trim()
    if (raw.isEmpty()) return null
    val withScheme = if ("://" in raw) raw else "http://$raw"
    return try {
      URI(withScheme).host?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }
  }
}
