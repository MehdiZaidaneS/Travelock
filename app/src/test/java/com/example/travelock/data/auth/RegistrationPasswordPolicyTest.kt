package com.example.travelock.data.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationPasswordPolicyTest {
    @Test fun matchesConfirmedSupabaseRulesAtLengthBoundary() {
        assertTrue(RegistrationPasswordPolicy.missingRequirements("Abcdef1!").isEmpty())
        assertTrue(RegistrationPasswordPolicy.missingRequirements("Abcde1!").any { "8 characters" in it })
    }

    @Test fun identifiesEachMissingCharacterGroup() {
        val failures = listOf("ABCDEFG1!" to "lowercase", "abcdefg1!" to "uppercase",
            "Abcdefgh!" to "digit", "Abcdefg12" to "symbol")
        failures.forEach { (password, missing) ->
            assertEquals(1, RegistrationPasswordPolicy.missingRequirements(password).size)
            assertTrue(RegistrationPasswordPolicy.missingRequirements(password).single().contains(missing))
        }
    }

    @Test fun whitespaceAndEmojiDoNotCountAsRequiredSymbols() {
        assertTrue(RegistrationPasswordPolicy.missingRequirements("Abcdef12 ").any { "symbol" in it })
        assertTrue(RegistrationPasswordPolicy.missingRequirements("Abcdef12😀").any { "symbol" in it })
        // No trimming: spaces are allowed as additional characters.
        assertTrue(RegistrationPasswordPolicy.missingRequirements(" Abcdef1! ").isEmpty())
    }

    @Test fun serverReasonMessagesAreMappedWithoutExposingUnknownContent() {
        assertTrue(RegistrationPasswordPolicy.serverRejection(listOf("pwned")).contains("data breach"))
        assertTrue(RegistrationPasswordPolicy.serverRejection(listOf("length")).contains("longer"))
        assertTrue(RegistrationPasswordPolicy.serverRejection(listOf("characters")).contains("uppercase"))
        assertFalse(RegistrationPasswordPolicy.serverRejection(listOf("private-request-secret")).contains("secret"))
    }
}
