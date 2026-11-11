package com.example.travelock.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

// Keep one instance from the Google request until its token is sent to Supabase.
class GoogleSignInNonce private constructor(raw: String) {
    val hashedNonceForGoogle: String = hex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)))
    private val rawNonce = AtomicReference<String?>(raw)

    internal fun consume(): String = checkNotNull(rawNonce.getAndSet(null)) {
        "This Google sign-in attempt was already used. Start a new request."
    }

    override fun toString(): String = "GoogleSignInNonce(redacted)"

    companion object {
        private val random = SecureRandom()
        fun create(): GoogleSignInNonce = GoogleSignInNonce(hex(ByteArray(32).also(random::nextBytes)))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
