package app.interfold.app.telemetry

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun installUncaughtExceptionTelemetry() {
  val previous = getUnhandledExceptionHook()
  setUnhandledExceptionHook { throwable ->
    runCatching { Telemetry.recordException(throwable) }
    previous?.invoke(throwable)
  }
}
