package app.interfold.app.ui.compose.screens.main.hometabs

import app.interfold.app.ui.model.main.hometabs.AltersComponent
import app.interfold.app.ui.model.main.hometabs.FriendsComponent
import app.interfold.app.ui.model.main.hometabs.JournalComponent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

/**
 * Empty JS objects, not Kotlin types, so the wasm IR fake-override builder
 * never sees a class that implements `ChildPanels` interfaces (see common
 * [stubAltersComponent] KDoc).
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => ({})")
private external fun emptyJsObject(): JsAny

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun stubAltersComponent(): AltersComponent = emptyJsObject().unsafeCast()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun stubFriendsComponent(): FriendsComponent = emptyJsObject().unsafeCast()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun stubJournalComponent(): JournalComponent = emptyJsObject().unsafeCast()
