package app.interfold

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import app.interfold.app.api.extractCfAuthorizationCookie

/**
 * In-app WebView for Cloudflare Access login / silent application-token rotation.
 * Captures `CF_Authorization` from the WebView cookie jar for [apiBaseUrl].
 */
class CloudflareAccessWebViewActivity : ComponentActivity() {
  private var finished = false

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val url = intent.getStringExtra(EXTRA_URL).orEmpty()
    val apiBaseUrl = intent.getStringExtra(EXTRA_API_BASE).orEmpty().trimEnd('/')
    val silent = intent.getBooleanExtra(EXTRA_SILENT, false)

    if (url.isBlank() || apiBaseUrl.isBlank()) {
      failAndFinish("Missing Cloudflare Access session URL")
      return
    }

    if (silent) {
      // Keep a tiny window so the WebView still runs; no chrome for the user.
      setTheme(android.R.style.Theme_Translucent_NoTitleBar)
    }

    val webView = WebView(this).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      CookieManager.getInstance().setAcceptCookie(true)
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

      webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
          tryCapture(apiBaseUrl)
          // Silent refresh: once we've hit the API host, capture and leave.
          if (silent && finishedUrl != null && finishedUrl.startsWith(apiBaseUrl)) {
            tryCapture(apiBaseUrl, finish = true)
          }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val next = request?.url?.toString().orEmpty()
          if (next.startsWith("interfold://")) {
            tryCapture(apiBaseUrl, finish = true)
            // Also deliver deep link into the main activity.
            startActivity(
              Intent(Intent.ACTION_VIEW, request?.url).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(packageName)
              }
            )
            return true
          }
          return false
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
          val next = url.orEmpty()
          if (next.startsWith("interfold://")) {
            tryCapture(apiBaseUrl, finish = true)
            startActivity(
              Intent(Intent.ACTION_VIEW, android.net.Uri.parse(next)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(packageName)
              }
            )
            return true
          }
          return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          tryCapture(apiBaseUrl)
        }
      }
    }

    setContentView(webView)
    webView.loadUrl(url)
  }

  private fun tryCapture(apiBaseUrl: String, finish: Boolean = false) {
    val cookies = CookieManager.getInstance().getCookie(apiBaseUrl)
    val jwt = extractCfAuthorizationCookie(cookies)
    if (jwt != null) {
      CloudflareAccessSessionBridge.onAccessJwt?.invoke(jwt)
      if (finish) {
        complete()
      }
    } else if (finish) {
      failAndFinish("Cloudflare Access cookie not found")
    }
  }

  private fun failAndFinish(message: String) {
    if (finished) return
    finished = true
    CloudflareAccessSessionBridge.onFailed?.invoke(message)
    CloudflareAccessSessionBridge.onFinished?.invoke()
    CloudflareAccessSessionBridge.clear()
    finish()
  }

  private fun complete() {
    if (finished) return
    finished = true
    CloudflareAccessSessionBridge.onFinished?.invoke()
    CloudflareAccessSessionBridge.clear()
    finish()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    failAndFinish("Cancelled")
  }

  companion object {
    const val EXTRA_URL = "url"
    const val EXTRA_API_BASE = "api_base"
    const val EXTRA_SILENT = "silent"

    fun start(
      context: Context,
      url: String,
      apiBaseUrl: String,
      silent: Boolean,
      onAccessJwt: (String) -> Unit,
      onFinished: () -> Unit,
      onFailed: (String) -> Unit,
    ) {
      CloudflareAccessSessionBridge.onAccessJwt = onAccessJwt
      CloudflareAccessSessionBridge.onFinished = onFinished
      CloudflareAccessSessionBridge.onFailed = onFailed
      context.startActivity(
        Intent(context, CloudflareAccessWebViewActivity::class.java).apply {
          putExtra(EXTRA_URL, url)
          putExtra(EXTRA_API_BASE, apiBaseUrl)
          putExtra(EXTRA_SILENT, silent)
          if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      )
    }
  }
}

internal object CloudflareAccessSessionBridge {
  var onAccessJwt: ((String) -> Unit)? = null
  var onFinished: (() -> Unit)? = null
  var onFailed: ((String) -> Unit)? = null

  fun clear() {
    onAccessJwt = null
    onFinished = null
    onFailed = null
  }
}
