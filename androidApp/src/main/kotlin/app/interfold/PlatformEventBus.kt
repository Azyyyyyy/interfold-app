package app.interfold

import app.interfold.app.utils.PlatformEvent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide bus for [PlatformEvent]s emitted from Android entry points (currently
 * `MainActivity` and `InterfoldFirebaseMessagingService`).
 *
 * Hoisted out of `MainActivity` so the messaging service — which the OS can wake
 * independently of the activity — can emit into the same pipeline `RootScreen`'s
 * collector already consumes. Mirrors the top-level `platformEventFlow` in
 * `main.ios.kt`.
 *
 * `replay = 3` matches the previous private buffer size, so events emitted before the
 * `RootScreen` collector attaches are still delivered.
 */
object PlatformEventBus {
  val flow = MutableSharedFlow<PlatformEvent>(replay = 3)
}
