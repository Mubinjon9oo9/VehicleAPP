package com.example.vehicleapp.data

/**
 * Update the username/password values with the credentials issued for your API.
 * These are used for automatic authentication before making requests.
 */
object CredentialsProvider {

    data class ApiCredentials(
        val username: String,
        val password: String
    )

    fun credentials(): ApiCredentials = ApiCredentials(
        username = "testuser",
        password = "test-secure-password"
    )
}
