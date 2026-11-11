package com.example.travelock.data.auth

internal object SessionRefreshPolicy {
    private val invalidSessionCodes = setOf(
        "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired",
    )

    // Do not turn an outage, rate limit or unrelated Auth error into a logout.
    fun isInvalidSession(statusCode: Int, errorCode: String): Boolean =
        statusCode in setOf(400,401,403) && errorCode in invalidSessionCodes
}
