package app.interfold

import app.interfold.app.utils.PlatformEvent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide bus for [PlatformEvent]s on wasm. Hoisted out of `main.wasm.kt` so
 * `platform.wasm.kt` (which lives in `shared/`) can emit into the same flow that
 * `RootScreen`'s collector consumes. Mirrors the Android `PlatformEventBus`.
 *
 * `replay = 3` matches the previous buffer size, so events emitted before the
 * collector attaches are still delivered.
 */
object PlatformEventBus {
  val flow = MutableSharedFlow<PlatformEvent>(replay = 3)
}
