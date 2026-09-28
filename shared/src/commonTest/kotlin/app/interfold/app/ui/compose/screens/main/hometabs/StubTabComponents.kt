package app.interfold.app.ui.compose.screens.main.hometabs

import app.interfold.app.ui.model.main.hometabs.AltersComponent
import app.interfold.app.ui.model.main.hometabs.FriendsComponent
import app.interfold.app.ui.model.main.hometabs.JournalComponent

/**
 * Inner-tab stubs for [FakeHomeTabsComponent].
 *
 * Kotlin/Wasm hits an **internal compiler error** (ICE) in the FIR→IR fake-override
 * builder when a Kotlin class/object implements an interface whose members mention
 * Decompose `ChildPanels<…>` — `AltersComponent.panels`, `FriendsComponent.panels`,
 * `JournalComponent.panels`. Fake overrides are the members the compiler synthesizes
 * for inherited interface API; on wasm, `IrFakeOverrideSymbolBase.getOwner` then
 * throws `shouldNotBeCalled` and compilation aborts. This is a compiler bug, not a
 * problem with the stub bodies.
 *
 * FrontHistory has no `ChildPanels` property, so its stub can live in commonTest.
 * These three are expect/actual: JVM/iOS/Android provide throwing Kotlin objects;
 * wasm supplies an empty JS object via `unsafeCast` so the compiler never builds
 * those fake overrides. Scaffold tests only do `is Child.X` checks, so the JS
 * object is never messaged.
 */
internal expect fun stubAltersComponent(): AltersComponent

internal expect fun stubFriendsComponent(): FriendsComponent

internal expect fun stubJournalComponent(): JournalComponent
