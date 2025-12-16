package com.example.vehicleapp.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the bearer token header to every request once we have a token stored.
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    interface TokenProvider {
        suspend fun accessToken(): String?
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.accessToken() }
        val request = if (!token.isNullOrBlank()) {
            chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
