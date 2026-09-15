package app.interfold.app.api

/**
 * Extracts the Cloudflare Access application JWT from a `Cookie` header or
 * `CookieManager.getCookie` blob (`name=value; name2=value2`).
 */
fun extractCfAuthorizationCookie(cookieBlob: String?): String? {
  if (cookieBlob.isNullOrBlank()) return null
  return cookieBlob
    .split(';')
    .asSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("${CloudflareAccessCredentials.COOKIE_NAME}=", ignoreCase = true) }
    ?.substringAfter('=', missingDelimiterValue = "")
    ?.takeIf { it.isNotBlank() }
}
