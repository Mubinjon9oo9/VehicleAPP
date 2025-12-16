package com.example.vehicleapp.data.remote

import com.example.vehicleapp.data.local.TokenStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

    /**
     * Update this base URL to point at the server you are integrating with.
     */
    private const val BASE_URL = "http://91.232.105.105:8000"

    fun provideVehicleApi(tokenStorage: TokenStorage): VehicleApiService {
        val authInterceptor = AuthInterceptor(tokenStorage)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BODY is helpful during development; lower it for production builds.
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient
            .Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VehicleApiService::class.java)
    }
}
