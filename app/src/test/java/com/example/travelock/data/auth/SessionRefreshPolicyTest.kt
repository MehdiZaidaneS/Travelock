package com.example.travelock.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshPolicyTest {
    @Test
    fun clearsOnlyDefinitivelyInvalidSessions() {
        for (code in listOf("refresh_token_not_found","refresh_token_already_used","session_not_found","session_expired")) {
            assertTrue(SessionRefreshPolicy.isInvalidSession(400,code))
        }
        assertTrue(SessionRefreshPolicy.isInvalidSession(401,"session_not_found"))
        assertTrue(SessionRefreshPolicy.isInvalidSession(403,"session_expired"))
    }

    @Test
    fun outagesRateLimitsAndUnrelatedErrorsDoNotClearTheSession() {
        for (status in listOf(408,429,500,502,503)) {
            assertFalse(SessionRefreshPolicy.isInvalidSession(status,"refresh_token_not_found"))
        }
        for (code in listOf("invalid_credentials","provider_disabled","bad_jwt","over_request_rate_limit","unexpected_failure","unknown")) {
            assertFalse(SessionRefreshPolicy.isInvalidSession(400,code))
        }
    }
}
