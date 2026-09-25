package app.interfold.app.telemetry

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalWasmJsInterop::class)
class BrowserErrorEventTest {
  @Test
  fun imageResourceErrorNamesTheElementAndStripsTheQuery() {
    val report = report(sampleBrowserEvent("image"))
    assertEquals(
      "Browser error event · HTMLImageElement https://cdn.example/a.png",
      report.message,
    )
    assertEquals("HTMLImageElement", report.attributes["exception.type"])
    assertFalse(report.message.contains("sig"))
  }

  @Test
  fun typeErrorKeepsNameMessageSourceAndStack() {
    val report = report(sampleBrowserEvent("type-error"))
    assertEquals(
      "TypeError: boom @ https://app.example/main.js:12:4",
      report.message,
    )
    assertEquals("TypeError", report.attributes["exception.type"])
    assertEquals("https://app.example/main.js:12:4", report.attributes["exception.source"])
    val stack = report.attributes["exception.stacktrace"]!!
    assertTrue(stack.contains("at boom"))
    assertFalse(stack.contains("token"))
  }

  @Test
  fun readsErrorMessageFromObjectsThatAreNotJsStrings() {
    val report = report(sampleBrowserEvent("wrapped-message"))
    assertEquals("RuntimeError: memory access out of bounds", report.message)
    assertEquals("RuntimeError", report.attributes["exception.type"])
    assertTrue(report.attributes["exception.stacktrace"]!!.contains("at wasm"))
  }

  @Test
  fun webSocketRejectionIncludesUrlAndReadyState() {
    val report = report(sampleBrowserEvent("socket"))
    assertEquals(
      "Browser error event · WebSocket wss://api.example/socket readyState=3",
      report.message,
    )
    assertEquals("WebSocket", report.attributes["exception.type"])
    assertFalse(report.message.contains("access"))
  }

  @Test
  fun indexedDbRejectionIncludesTheRequestError() {
    val report = report(sampleBrowserEvent("idb"))
    assertEquals("NotFoundError: missing key · IDBRequest", report.message)
    assertEquals("NotFoundError", report.attributes["exception.type"])
  }

  @Test
  fun scriptErrorStaysLabeledWhenTheBrowserHidesDetails() {
    val report = report(sampleBrowserEvent("script-error"))
    assertEquals("Script error (cross-origin)", report.message)
  }

  private fun report(event: JsAny?): BrowserErrorReport =
    reportBrowserErrorJson(describeJsErrorEvent(event))
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "(kind) => {" +
    "if (kind === 'image') {" +
      "var img = {};" +
      "Object.defineProperty(img, 'constructor', { value: function HTMLImageElement() {} });" +
      "img.src = 'https://cdn.example/a.png?sig=secret';" +
      "return { type: 'error', message: '', target: img };" +
    "}" +
    "if (kind === 'type-error') {" +
      "return {" +
        "type: 'error'," +
        "message: 'Uncaught TypeError: boom'," +
        "filename: 'https://app.example/main.js?token=secret#L1'," +
        "lineno: 12," +
        "colno: 4," +
        "error: {" +
          "name: 'TypeError'," +
          "message: 'boom'," +
          "stack: 'TypeError: boom\\n    at boom (https://app.example/main.js?token=secret:12:4)'" +
        "}" +
      "};" +
    "}" +
    "if (kind === 'wrapped-message') {" +
      "return {" +
        "type: 'error'," +
        "error: {" +
          "name: 'RuntimeError'," +
          "message: { toString: function() { return 'memory access out of bounds'; } }," +
          "stack: 'RuntimeError: memory access out of bounds\\n    at wasm'" +
        "}" +
      "};" +
    "}" +
    "if (kind === 'socket') {" +
      "function WebSocket() {}" +
      "var ws = new WebSocket();" +
      "ws.url = 'wss://api.example/socket?access=secret';" +
      "ws.readyState = 3;" +
      "return { type: 'unhandledrejection', reason: { type: 'error', target: ws } };" +
    "}" +
    "if (kind === 'idb') {" +
      "var request = {};" +
      "Object.defineProperty(request, 'constructor', { value: function IDBRequest() {} });" +
      "request.error = { name: 'NotFoundError', message: 'missing key' };" +
      "return { type: 'unhandledrejection', reason: { type: 'error', target: request } };" +
    "}" +
    "return { type: 'error', message: 'Script error.' };" +
  "}"
)
private external fun sampleBrowserEvent(kind: String): JsAny?
