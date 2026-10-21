package app.interfold.app.integration

import javax.swing.SwingUtilities

/** Decompose navigation on JVM is gated to the Swing EDT. */
internal fun <T> runOnClientMainThread(block: () -> T): T {
  if (SwingUtilities.isEventDispatchThread()) {
    return block()
  }
  var result: Result<T>? = null
  SwingUtilities.invokeAndWait {
    result = runCatching(block)
  }
  return result!!.getOrThrow()
}
