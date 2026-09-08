package app.interfold.app.utils

/**
 * Test-side stub for [PlatformUtilities]. Unlike the
 * [FakePhoenixSocketSession][app.interfold.app.api.FakePhoenixSocketSession]
 * we use to drive
 * [ApiInterfaceImpl][app.interfold.app.ui.model.interfaces.ApiInterfaceImpl]
 * without a backend, this is NOT a swappable seam against a real common
 * implementation — [PlatformUtilities] itself IS the platform-abstraction
 * boundary, and the production per-platform impls do heavy host-side I/O
 * (writes to OS preferences, network fetches, exitProcess, launching the
 * user's browser) so they cannot be reused in unit tests.
 *
 * Every behavioural method routes through a shared `unsupported()` tripwire
 * on [FailingPlatformUtilitiesBase], so any unexpected call from a code path
 * under test fails loudly with a clear message. Platform actuals only add
 * what their `actual interface PlatformUtilities` adds beyond
 * [CommonPlatformUtilities] (e.g. a `Context` on Android, an injected
 * delegate on iOS).
 */
expect class FailingPlatformUtilities() : PlatformUtilities
