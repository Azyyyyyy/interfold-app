@file:JsModule("@interfold/sample-browser-event")
@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.telemetry

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external fun sampleBrowserEvent(kind: String): JsAny?
