package app.interfold.app.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryExceptionMessageTest {
  @Test
  fun keepsOrdinaryMessages() {
    assertEquals("Connection refused", readableExceptionMessage(RuntimeException("Connection refused")))
    assertEquals("RuntimeException", readableExceptionMessage(RuntimeException()))
    assertEquals("RuntimeException", readableExceptionMessage(RuntimeException("")))
  }

  @Test
  fun replacesJsObjectTags() {
    assertEquals(BROWSER_ERROR_EVENT, readableExceptionMessage(RuntimeException("[object Event]")))
    assertEquals(BROWSER_ERROR_EVENT, readableExceptionMessage(RuntimeException("[object]")))
    assertEquals(BROWSER_ERROR_EVENT, readableExceptionMessage(RuntimeException("JsException: [object Event]")))
  }

  @Test
  fun replacesDomEventDumps() {
    assertEquals(
      BROWSER_ERROR_EVENT,
      readableExceptionMessage(RuntimeException("""{"target":{},"type":"error","isTrusted":true}""")),
    )
    assertEquals(
      BROWSER_ERROR_EVENT,
      readableExceptionMessage(RuntimeException("{ target :{}, type : error , isTrusted :true}")),
    )
  }

  @Test
  fun usesCauseWhenPrimaryMessageIsBlank() {
    val nested = RuntimeException(null as String?, RuntimeException("[object Event]"))
    assertEquals(BROWSER_ERROR_EVENT, readableExceptionMessage(nested))
  }

  @Test
  fun readsJsErrorTypeFromUncaughtPrefix() {
    val error = RuntimeException(
      "Uncaught TypeError: Failed to construct 'Clipboard': Illegal constructor",
    )
    assertEquals("TypeError", readableExceptionType(error))
    assertEquals(
      "TypeError",
      readableExceptionType(RuntimeException("TypeError: Failed to construct 'Clipboard': Illegal constructor")),
    )
    assertEquals("RuntimeException", readableExceptionType(RuntimeException("Connection refused")))
  }

  @Test
  fun activityAttributesKeepOrdinaryMessages() {
    val attrs = exceptionActivityAttributes(RuntimeException("Connection refused"))
    assertEquals("Connection refused", attrs["exception.message"])
    assertEquals("RuntimeException", attrs["exception.type"])
  }

  @Test
  fun describesResourceEvents() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "error",
        targetKind = "HTMLImageElement",
        targetDetail = "https://cdn.example/a.png?sig=secret",
      ),
    )
    assertEquals(
      "Browser error event · HTMLImageElement https://cdn.example/a.png",
      report.message,
    )
    assertEquals("HTMLImageElement", report.attributes["exception.type"])
    assertFalse(report.message.contains("secret"))
  }

  @Test
  fun describesThrownErrorWithSourceAndStack() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "error",
        filename = "https://app.example/main.js?token=secret",
        line = 120,
        column = 4,
        errorName = "TypeError",
        errorMessage = "Cannot read properties of undefined (reading 'id')",
        errorStack = "TypeError: boom\n    at https://cdn.example/app.js?token=secret:12:4\n    at next",
      ),
    )
    assertEquals(
      "TypeError: Cannot read properties of undefined (reading 'id') @ https://app.example/main.js:120:4",
      report.message,
    )
    assertEquals("TypeError", report.attributes["exception.type"])
    assertEquals("https://app.example/main.js:120:4", report.attributes["exception.source"])
    val stack = report.attributes["exception.stacktrace"]!!
    assertTrue(stack.contains("at https://cdn.example/app.js"))
    assertFalse(stack.contains("secret"))
  }

  @Test
  fun labelsCrossOriginScriptErrors() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "error",
        message = "Script error.",
        filename = "https://other.example/lib.js",
        line = 2,
        column = 8,
      ),
    )
    assertEquals("Script error (cross-origin) @ https://other.example/lib.js:2:8", report.message)
    assertEquals("ErrorEvent", report.attributes["exception.type"])
  }

  @Test
  fun describesIndexedDbRequestErrors() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "unhandledrejection",
        errorName = "NotFoundError",
        errorMessage = "missing key",
        nestedType = "error",
        targetKind = "IDBRequest",
      ),
    )
    assertEquals("NotFoundError: missing key · IDBRequest", report.message)
    assertEquals("NotFoundError", report.attributes["exception.type"])
    assertEquals("IDBRequest", report.attributes["exception.target"])
  }

  @Test
  fun describesWebSocketEvents() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "unhandledrejection",
        errorKind = "Event",
        nestedType = "error",
        targetKind = "WebSocket",
        targetDetail = "wss://api.example/socket?access=secret readyState=3",
      ),
    )
    assertEquals(
      "Browser error event · WebSocket wss://api.example/socket readyState=3",
      report.message,
    )
    assertEquals("WebSocket", report.attributes["exception.type"])
    assertFalse(report.message.contains("secret"))
  }

  @Test
  fun distinguishesUnhandledRejectionsWithNoDetail() {
    val report = reportBrowserError(BrowserErrorFacts(eventType = "unhandledrejection"))
    assertEquals("Browser error event · unhandledrejection", report.message)
    assertEquals("UnhandledRejection", report.attributes["exception.type"])
  }

  @Test
  fun keepsExtractorFailures() {
    val report = reportBrowserError(BrowserErrorFacts(describeFailure = "SecurityError: blocked"))
    assertEquals("Browser error event · SecurityError: blocked", report.message)
  }

  @Test
  fun stripsDataUrlsFromTargets() {
    val report = reportBrowserError(
      BrowserErrorFacts(
        eventType = "error",
        targetKind = "HTMLImageElement",
        targetDetail = "data:image/png;base64,AAAA",
      ),
    )
    assertEquals("Browser error event · HTMLImageElement data:image/png", report.message)
    assertFalse(report.message.contains("AAAA"))
  }

  @Test
  fun parsesBrowserErrorFactsJson() {
    val report = reportBrowserErrorJson(
      """{"eventType":"error","targetKind":"HTMLScriptElement","targetDetail":"https://cdn.example/app.js","message":""}""",
    )
    assertEquals("Browser error event · HTMLScriptElement https://cdn.example/app.js", report.message)
  }

  @Test
  fun doesNotDoublePrefixErrorName() {
    val report = reportBrowserError(
      BrowserErrorFacts(errorName = "TypeError", errorMessage = "TypeError: already prefixed"),
    )
    assertEquals("TypeError: already prefixed", report.message)
  }

  @Test
  fun keepsTheFirstStackFrames() {
    val stack = (1..8).joinToString("\n") { "at frame$it" }
    val report = reportBrowserError(
      BrowserErrorFacts(errorName = "TypeError", errorMessage = "boom", errorStack = stack),
    )
    val frames = report.attributes["exception.stacktrace"]!!.lines()
    assertEquals(listOf("at frame1", "at frame2", "at frame3", "at frame4", "at frame5", "at frame6"), frames)
  }
}
