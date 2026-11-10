package com.example.travelock.data.auth

import java.net.URI
import java.net.URLDecoder

// Keep parsing separate from verification: only Supabase can validate the token.
internal object PasswordRecoveryLinks {
    const val CALLBACK_URL = "travelock://auth/recovery"
    private const val INVALID_LINK = "Use an unused password-recovery link from this project's email."

    fun tokenHash(link: String, supabaseUrl: String): String {
        // URI/decoder exceptions can contain the input. Never attach them to an
        // error shown in logs, since a recovery link grants account access.
        try {
            val uri = URI(link.trim())
            val project = URI(supabaseUrl.trim().trimEnd('/'))
            val callback = URI(CALLBACK_URL)
            val projectLink = sameOrigin(uri, project) &&
                uri.rawPath == project.rawPath.trimEnd('/') + "/auth/v1/verify"
            val appLink = sameOrigin(uri, callback) && uri.rawPath == callback.rawPath
            require((projectLink || appLink) && uri.rawUserInfo == null && uri.rawFragment == null)
            val pairs = requireNotNull(uri.rawQuery).split('&').map { part ->
                val pieces = part.split('=', limit = 2)
                require(pieces.size == 2)
                URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces[1], "UTF-8")
            }
            require(pairs.map { it.first }.distinct().size == pairs.size)
            val values = pairs.toMap()
            require(values["type"] == "recovery" && "error" !in values)
            val tokens = listOfNotNull(values["token"], values["token_hash"])
            require(tokens.size == 1)
            return tokens.single().also { token ->
                require(token.isNotBlank() && token.none { it.isWhitespace() })
            }
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(INVALID_LINK)
        } catch (_: java.net.URISyntaxException) {
            throw IllegalArgumentException(INVALID_LINK)
        }
    }

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme != null && first.host != null &&
            first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) && first.port == second.port
}
