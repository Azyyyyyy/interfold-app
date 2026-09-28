@file:JsModule("@interfold/describe-js-error-event")
@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.telemetry

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external fun describeJsErrorEvent(event: JsAny?): String
