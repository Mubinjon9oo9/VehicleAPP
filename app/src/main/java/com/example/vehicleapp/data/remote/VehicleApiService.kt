package com.example.vehicleapp.data.remote

import com.example.vehicleapp.data.model.AuthResponse
import com.example.vehicleapp.data.model.SearchResponse
import com.example.vehicleapp.data.model.VehicleListResponse
import com.example.vehicleapp.data.model.VehicleDetailResponse
import com.example.vehicleapp.data.model.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Path

/**
 * Retrofit contract for the remote vehicle API.
 * Replace the endpoint paths or query parameters to match the server you use.
 */
interface VehicleApiService {

    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): AuthResponse

    @POST("refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse

    @GET("vehicles/search/")
    suspend fun searchVehicles(
        @Query("query") query: String
    ): SearchResponse

    @GET("vehicle")
    suspend fun recentVehicles(): VehicleListResponse

    @GET("vehicle/{vin}")
    suspend fun vehicleDetail(
        @Path("vin") vin: String
    ): VehicleDetailResponse

    @GET("vin/{vin}")
    suspend fun vehicleByVin(
        @Path("vin") vin: String
    ): VehicleDetailResponse
}
