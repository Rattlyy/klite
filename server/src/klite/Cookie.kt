package klite

import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

data class Cookie(val name: String, val value: String, val expires: Instant? = null, val maxAgeSec: Int? = null,
                  val path: String? = "/", val domain: String? = null,
                  val httpOnly: Boolean = false, val secure: Boolean = false, val sameSite: SameSite? = null) {
  enum class SameSite { Lax, Strict, None }

  override fun toString() = "$name=${value.urlEncode()}" + (sequenceOf(
    expires?.let { "Expires=" + RFC_1123_DATE_TIME.format(it.atOffset(UTC)) },
    maxAgeSec?.let { "Max-Age=$it" },
    path?.let { "Path=$it" },
    domain?.let { "Domain=$it" },
    "HttpOnly".takeIf { httpOnly }, "Secure".takeIf { secure }, sameSite?.let { "SameSite=$it" }
  ).filterNotNull().joinToString("") { "; $it" })
}

fun decodeCookies(cookies: String?): Params = cookies?.split(';')?.associate { keyValue(it.trim()) } ?: emptyMap()
