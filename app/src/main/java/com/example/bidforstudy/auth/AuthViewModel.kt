package com.example.bidforstudy.auth

import androidx.lifecycle.ViewModel
import com.example.bidforstudy.data.AuthRepository
import com.example.bidforstudy.data.InMemoryAuthRepository

class AuthViewModel(
    private val repository: AuthRepository = InMemoryAuthRepository
) : ViewModel() {

    var currentUser: String? = null
        private set

    fun login(username: String, password: String): Boolean {
        val u = username.trim()
        val ok = repository.login(u, password)
        if (ok) {
            currentUser = u
        }
        return ok
    }

    fun register(username: String, password: String): Boolean {
        val u = username.trim()
        return repository.register(u, password)
    }

    fun getCurrentUserTokens(): Int {
        val user = currentUser ?: return 0
        return repository.getTokens(user)
    }

    fun addTokensForCurrentUser(amount: Int) {
        val user = currentUser ?: return
        repository.addTokens(user, amount)
    }

    fun logout() {
        currentUser = null
    }
}
