// Backend URL/token injection for wasm ITs lives in
// shared/karma.config.d/inject-backend-url.js (runtime globalThis).
// Do not DefinePlugin those keys here: Kotlin/Wasm @JsFun snippets get the
// empty compile-time values baked in and ignore the karma hook.
