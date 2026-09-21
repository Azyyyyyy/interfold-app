package app.interfold.app.utils

/**
 * Test-side stub for [PlatformUtilities]. A factory function rather than an
 * `expect class` so Kotlin/Wasm does not treat inherited interface methods
 * with default parameter values as illegal actuals.
 *
 * Every behavioural method routes through [FailingPlatformUtilitiesBase]
 * (inlined on wasm via the same tripwire).
 */
expect fun failingPlatformUtilities(): PlatformUtilities
