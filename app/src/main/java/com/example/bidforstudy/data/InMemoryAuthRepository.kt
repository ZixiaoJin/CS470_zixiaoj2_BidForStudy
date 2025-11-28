package com.example.bidforstudy.data

object InMemoryAuthRepository : AuthRepository {

    // username -> password
    private val users = mutableMapOf<String, String>()

    // username -> tokens
    private val tokens = mutableMapOf<String, Int>()

    override fun register(username: String, password: String): Boolean {
        val u = username.trim()
        if (u.isBlank() || password.isBlank()) return false
        if (users.containsKey(u)) return false  // already exists

        users[u] = password
        tokens[u] = 100   // 🎁 award 100 tokens on registration
        return true
    }

    override fun login(username: String, password: String): Boolean {
        val u = username.trim()
        val stored = users[u] ?: return false
        return stored == password
    }

    override fun getTokens(username: String): Int {
        val u = username.trim()
        return tokens[u] ?: 0
    }

    override fun addTokens(username: String, amount: Int) {
        val u = username.trim()
        val current = tokens[u] ?: 0
        tokens[u] = current + amount
    }
}
