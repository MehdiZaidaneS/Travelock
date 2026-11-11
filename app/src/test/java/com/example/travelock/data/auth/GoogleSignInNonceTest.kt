package com.example.travelock.data.auth

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleSignInNonceTest {
    @Test
    fun googleReceivesTheSha256HashWhileSupabaseReceivesTheOriginal() {
        val nonce = GoogleSignInNonce.create()
        val raw = nonce.consume()
        assertEquals(64,raw.length)
        val expected = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected,nonce.hashedNonceForGoogle)
        assertNotEquals(raw,nonce.hashedNonceForGoogle)
    }

    @Test
    fun attemptsAreIndependentAndCanOnlyBeConsumedOnce() {
        val first = GoogleSignInNonce.create()
        val second = GoogleSignInNonce.create()
        assertNotEquals(first.hashedNonceForGoogle,second.hashedNonceForGoogle)
        first.consume()
        assertThrows(IllegalStateException::class.java) { first.consume() }
    }

    @Test
    fun diagnosticStringDoesNotExposeTheNonce() {
        val nonce = GoogleSignInNonce.create()
        val raw = nonce.consume()
        assertFalse(nonce.toString().contains(raw))
        assertFalse(nonce.toString().contains(nonce.hashedNonceForGoogle))
    }
}
