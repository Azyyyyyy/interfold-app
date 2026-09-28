package app.interfold.app.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginMethodsTest {
  @Test
  fun hasAny_isFalseWhenNoProviders() {
    assertFalse(LoginMethods().hasAny)
  }

  @Test
  fun hasAny_isTrueWhenAnyProviderIsEnabled() {
    assertTrue(LoginMethods(google = true).hasAny)
    assertTrue(LoginMethods(cloudflare = true).hasAny)
  }

  @Test
  fun parse_emptyJson_isReadyWithNoProviders() {
    val (methods, accessGated) = parseLoginMethodsResponse(
      statusCode = 200,
      locationHeader = null,
      body = """{"cloudflare":false,"google":false,"discord":false,"apple":false}""",
    )
    assertFalse(methods.hasAny)
    assertFalse(accessGated)
  }

  @Test
  fun parse_enabledProvider() {
    val (methods, _) = parseLoginMethodsResponse(
      statusCode = 200,
      locationHeader = null,
      body = """{"cloudflare":false,"google":true,"discord":false,"apple":false}""",
    )
    assertTrue(methods.google)
    assertTrue(methods.hasAny)
  }

  @Test
  fun parse_cloudflareAccessChallenge() {
    val (methods, accessGated) = parseLoginMethodsResponse(
      statusCode = 302,
      locationHeader = "https://myteam.cloudflareaccess.com/cdn-cgi/access/login",
      body = "",
    )
    assertTrue(methods.cloudflare)
    assertTrue(accessGated)
  }

  @Test
  fun parse_nonSuccessWithoutAccessChallenge_fails() {
    assertFails {
      parseLoginMethodsResponse(
        statusCode = 404,
        locationHeader = null,
        body = "not found",
      )
    }
  }

  @Test
  fun parse_htmlSuccess_isTreatedAsAccess() {
    val (methods, accessGated) = parseLoginMethodsResponse(
      statusCode = 200,
      locationHeader = null,
      body = "<html>sign in</html>",
    )
    assertTrue(methods.cloudflare)
    assertTrue(accessGated)
  }
}
