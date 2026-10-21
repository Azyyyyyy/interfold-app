package app.interfold.app.integration

internal fun <T> runOnClientMainThread(block: () -> T): T = block()
