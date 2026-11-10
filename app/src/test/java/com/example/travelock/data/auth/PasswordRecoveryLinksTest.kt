package com.example.travelock.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordRecoveryLinksTest {
    private val project = "https://project.supabase.co"
    private val verify = "$project/auth/v1/verify"

    @Test
    fun acceptsOriginalEmailAndExplicitAppCallbackWithoutFollowingRedirects() {
        assertEquals("secret", PasswordRecoveryLinks.tokenHash(
            "$verify?token=secret&type=recovery&redirect_to=https%3A%2F%2Fexample.com", project,
        ))
        assertEquals("secret", PasswordRecoveryLinks.tokenHash(
            "${PasswordRecoveryLinks.CALLBACK_URL}?token_hash=secret&type=recovery", project,
        ))
    }

    @Test
    fun rejectsForeignOriginsOtherActionsAndSessionFragments() {
        for (link in listOf(
            "https://other.supabase.co/auth/v1/verify?token=secret&type=recovery",
            "https://project.supabase.co.attacker.test/auth/v1/verify?token=secret&type=recovery",
            "https://user@project.supabase.co/auth/v1/verify?token=secret&type=recovery",
            "http://project.supabase.co/auth/v1/verify?token=secret&type=recovery",
            "$project/wrong-path?token=secret&type=recovery",
            "$verify?token=secret&type=signup",
            "$verify?token=secret&type=magiclink",
            "travelock://auth/other?token_hash=secret&type=recovery",
            "travelock://auth/recovery#access_token=secret&type=recovery",
            "/auth/v1/verify?token=secret&type=recovery",
        )) rejectWithoutLeaking(link)
    }

    @Test
    fun rejectsMissingAmbiguousAndMalformedTokens() {
        for (query in listOf(
            "type=recovery", "token=&type=recovery", "token=secret",
            "token=secret&type=recovery&type=signup",
            "token=secret&token_hash=other&type=recovery",
            "token=secret&%74oken=other&type=recovery",
            "token=secret%20value&type=recovery",
            "token=secret%ZZ&type=recovery",
            "token=secret&type=recovery&error=expired",
        )) rejectWithoutLeaking("$verify?$query")
    }

    private fun rejectWithoutLeaking(link: String) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PasswordRecoveryLinks.tokenHash(link, project)
        }
        assertFalse(error.message.orEmpty().contains("secret"))
        assertEquals(null, error.cause)
    }
}
