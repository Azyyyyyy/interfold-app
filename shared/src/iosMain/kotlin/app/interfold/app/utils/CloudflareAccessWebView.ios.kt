package app.interfold.app.utils

import app.interfold.app.api.CloudflareAccessCredentials
import app.interfold.app.api.extractCfAuthorizationCookie
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyleDone
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObjectMeta

@OptIn(ExperimentalForeignApi::class)
internal fun presentCloudflareAccessWebView(
  url: String,
  apiBaseUrl: String,
  silent: Boolean,
  onAccessJwt: (String) -> Unit,
  onFinished: () -> Unit,
  onFailed: (String) -> Unit,
) {
  val root = UIApplication.sharedApplication.keyWindow?.rootViewController
  if (root == null) {
    onFailed("No root view controller")
    onFinished()
    return
  }

  val nsUrl = NSURL.URLWithString(url)
  if (nsUrl == null) {
    onFailed("Invalid Cloudflare Access URL")
    onFinished()
    return
  }

  val hostVc = CloudflareAccessHostController(
    apiBaseUrl = apiBaseUrl.trimEnd('/'),
    silent = silent,
    onAccessJwt = onAccessJwt,
    onFinished = onFinished,
    onFailed = onFailed,
  )

  if (silent) {
    hostVc.modalPresentationStyle = UIModalPresentationFullScreen
    hostVc.view.alpha = 0.02
    root.presentViewController(hostVc, animated = false) {
      hostVc.load(NSURLRequest.requestWithURL(nsUrl))
    }
  } else {
    val nav = UINavigationController(rootViewController = hostVc)
    hostVc.navigationItem.rightBarButtonItem = UIBarButtonItem(
      title = "Done",
      style = UIBarButtonItemStyleDone,
      target = hostVc,
      action = NSSelectorFromString("onDoneTapped"),
    )
    root.presentViewController(nav, animated = true) {
      hostVc.load(NSURLRequest.requestWithURL(nsUrl))
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private class CloudflareAccessHostController(
  private val apiBaseUrl: String,
  private val silent: Boolean,
  private val onAccessJwt: (String) -> Unit,
  private val onFinished: () -> Unit,
  private val onFailed: (String) -> Unit,
) : UIViewController(nibName = null, bundle = null), WKNavigationDelegateProtocol {
  private var webView: WKWebView? = null
  private var completed = false

  override fun viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = UIColor.whiteColor
    val wv = WKWebView(frame = view.bounds, configuration = WKWebViewConfiguration())
    wv.navigationDelegate = this
    wv.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
    view.addSubview(wv)
    webView = wv
  }

  fun load(request: NSURLRequest) {
    viewDidLoad()
    webView?.loadRequest(request)
  }

  @ObjCAction
  fun onDoneTapped() {
    captureCookies(finishAfter = true, cancel = true)
  }

  private fun captureCookies(finishAfter: Boolean, cancel: Boolean = false) {
    val store = webView?.configuration?.websiteDataStore?.httpCookieStore ?: run {
      if (finishAfter) finish(if (cancel) "Cancelled" else "Cloudflare Access cookie not found")
      return
    }
    store.getAllCookies { cookies ->
      val blob = cookies
        ?.mapNotNull { cookie ->
          val http = cookie as? platform.Foundation.NSHTTPCookie ?: return@mapNotNull null
          "${http.name}=${http.value}"
        }
        ?.joinToString("; ")
      val jwt = cookies
        ?.mapNotNull { it as? platform.Foundation.NSHTTPCookie }
        ?.firstOrNull { it.name.equals(CloudflareAccessCredentials.COOKIE_NAME, ignoreCase = true) }
        ?.value
        ?: extractCfAuthorizationCookie(blob)

      if (jwt != null) {
        onAccessJwt(jwt)
        if (finishAfter) finish(null)
      } else if (finishAfter) {
        finish(if (cancel) "Cancelled" else "Cloudflare Access cookie not found")
      }
    }
  }

  private fun finish(failedMessage: String?) {
    if (completed) return
    completed = true
    if (failedMessage != null) onFailed(failedMessage)
    onFinished()
    dismissViewControllerAnimated(!silent, completion = null)
  }

  override fun webView(
    webView: WKWebView,
    decidePolicyForNavigationAction: WKNavigationAction,
    decisionHandler: (WKNavigationActionPolicy) -> Unit,
  ) {
    val next = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
    if (next.startsWith("interfold://")) {
      captureCookies(finishAfter = true)
      NSURL.URLWithString(next)?.let { UIApplication.sharedApplication.openURL(it) }
      decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
      return
    }
    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
  }

  override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
    val current = webView.URL?.absoluteString.orEmpty()
    captureCookies(finishAfter = silent && current.startsWith(apiBaseUrl))
  }
}
