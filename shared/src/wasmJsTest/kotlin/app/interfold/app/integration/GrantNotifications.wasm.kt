package app.interfold.app.integration

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * Ask Karma to CDP-grant notifications for this origin, then snapshot the
 * native permission state. Fail only when the grant endpoint itself errors —
 * [acquireWebFCMToken] still owns the `requestPermission` checkpoint.
 */
internal suspend fun ensureKarmaNotificationGrant(): String {
  val snapshot = requestKarmaNotificationGrant().await<JsString>().toString()
  check(snapshot.contains("\"ok\":true") || snapshot.contains("\"ok\": true")) {
    "checkpoint notification grant: Karma CDP grant failed. $snapshot"
  }
  return snapshot
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "() => fetch('/__interfold_grant_notifications?origin=' + encodeURIComponent(location.origin))" +
    ".then(function (res) {" +
    "return res.text().then(function (body) {" +
    "var permission = (typeof Notification === 'undefined') ? 'missing' : Notification.permission;" +
    "var done = function (queryState) {" +
    "return JSON.stringify({" +
    "ok: res.ok, status: res.status, body: body," +
    "permission: permission, queryState: queryState, origin: location.origin" +
    "});" +
    "};" +
    "if (!navigator.permissions || !navigator.permissions.query) return done('unavailable');" +
    "return navigator.permissions.query({ name: 'notifications' })" +
    ".then(function (q) { return done(q.state); })" +
    ".catch(function () { return done('error'); });" +
    "});" +
    "})",
)
private external fun requestKarmaNotificationGrant(): Promise<JsString>
