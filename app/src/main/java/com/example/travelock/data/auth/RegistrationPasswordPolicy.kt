package com.example.travelock.data.auth

object RegistrationPasswordPolicy {
    const val minimumLength = 8
    private const val symbols = """!@#$%^&*()_+-=[]{};'\:"|<>?,./`~"""

    fun missingRequirements(password: String): List<String> = buildList {
        if (password.codePointCount(0, password.length) < minimumLength) add("At least $minimumLength characters")
        if (password.none { it in 'a'..'z' }) add("A lowercase letter (a–z)")
        if (password.none { it in 'A'..'Z' }) add("An uppercase letter (A–Z)")
        if (password.none { it in '0'..'9' }) add("A digit (0–9)")
        if (password.none { it in symbols }) add("A symbol, such as !, @ or #")
    }

    // Map only known reason codes; never expose raw server descriptions/headers.
    fun serverRejection(reasons: List<String>): String = when {
        "pwned" in reasons -> "This password has appeared in a data breach. Choose a different password."
        "length" in reasons -> "Supabase requires a longer password. Use at least $minimumLength characters; try a longer password if the server requires more."
        "characters" in reasons -> "Include lowercase and uppercase letters, a digit and a symbol such as !, @ or #."
        else -> "Password rejected. Choose a stronger password with at least $minimumLength characters, lowercase and uppercase letters, a digit and a symbol."
    }
}
