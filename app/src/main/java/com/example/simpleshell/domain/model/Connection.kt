package com.example.simpleshell.domain.model

data class Connection(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKey: String? = null,
    val privateKeyPassphrase: String? = null,
    val authType: AuthType = AuthType.PASSWORD,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
) {
    enum class AuthType {
        PASSWORD,
        KEY
    }
}
