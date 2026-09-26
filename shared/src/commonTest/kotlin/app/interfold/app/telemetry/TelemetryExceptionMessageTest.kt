package app.interfold.app.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
