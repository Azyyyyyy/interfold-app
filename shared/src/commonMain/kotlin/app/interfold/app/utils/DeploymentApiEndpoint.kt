package app.interfold.app.utils

import app.interfold.app.Settings

/**
 * Uses [deploymentDefault] when the browser has no saved API endpoint.
 * A saved endpoint is left alone, including the built-in public default, because
 * that value may have been chosen on purpose.
 */
internal fun applyDeploymentApiEndpoint(stored: Settings?, deploymentDefault: String): Settings {
  val configured = deploymentDefault.trim().trimEnd('/')
  if (configured.isEmpty()) {
    return stored ?: Settings()
  }
  if (stored == null || stored.apiEndpoint.isBlank()) {
    return (stored ?: Settings()).copy(apiEndpoint = configured)
  }
  return stored
}
