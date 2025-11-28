package com.example.bidforstudy.data

interface AuthRepository {
    fun register(username: String, password: String): Boolean
    fun login(username: String, password: String): Boolean

    fun getTokens(username: String): Int
    fun addTokens(username: String, amount: Int)
}
