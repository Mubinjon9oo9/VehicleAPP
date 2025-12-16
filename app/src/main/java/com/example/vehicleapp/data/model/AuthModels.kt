package com.example.vehicleapp.data.model

import com.squareup.moshi.Json

/**
 * Raw response from the auth endpoint. Field names are annotated to make
 * it easy to adapt to whichever API you plug in.
 */
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String
)

data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

/**
 * Domain representation of the tokens we will persist locally.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
)
