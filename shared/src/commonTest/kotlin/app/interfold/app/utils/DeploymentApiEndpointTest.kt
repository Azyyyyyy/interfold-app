package app.interfold.app.utils

import app.interfold.app.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class DeploymentApiEndpointTest {
  @Test
  fun usesBuiltInDefaultWhenNothingIsConfigured() {
    val resolved = applyDeploymentApiEndpoint(stored = null, deploymentDefault = "")
    assertEquals(Settings.DEFAULT_API_ENDPOINT, resolved.apiEndpoint)
  }

  @Test
  fun usesDeploymentDefaultWhenNoSettingsAreStored() {
    val resolved = applyDeploymentApiEndpoint(
      stored = null,
      deploymentDefault = "https://api.example.com/",
    )
    assertEquals("https://api.example.com", resolved.apiEndpoint)
  }

  @Test
  fun usesDeploymentDefaultWhenStoredEndpointIsBlank() {
    val resolved = applyDeploymentApiEndpoint(
      stored = Settings(apiEndpoint = "  "),
      deploymentDefault = "https://api.example.com",
    )
    assertEquals("https://api.example.com", resolved.apiEndpoint)
  }

  @Test
  fun keepsASavedEndpoint() {
    val resolved = applyDeploymentApiEndpoint(
      stored = Settings(apiEndpoint = "https://api.interfold.co.uk"),
      deploymentDefault = "https://api.example.com",
    )
    assertEquals("https://api.interfold.co.uk", resolved.apiEndpoint)
  }
}
