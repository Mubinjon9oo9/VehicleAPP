package com.example.vehicleapp.data

import android.util.Log
import com.example.vehicleapp.data.local.TokenStorage
import com.example.vehicleapp.data.model.AuthTokens
import com.example.vehicleapp.data.model.RefreshTokenRequest
import com.example.vehicleapp.data.model.VehicleDetail
import com.example.vehicleapp.data.model.VehicleSummary
import com.example.vehicleapp.data.model.toVehicleDetail
import com.example.vehicleapp.data.model.toVehicleSummaries
import com.example.vehicleapp.data.remote.VehicleApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException

private const val TAG = "VehicleRepository"

class VehicleRepository(
    private val apiService: VehicleApiService,
    private val tokenStorage: TokenStorage
) {

    val tokensFlow: Flow<AuthTokens?> = tokenStorage.tokensFlow

    suspend fun login(username: String, password: String): AuthTokens {
        val response = apiService.login(username, password)
        val tokens = AuthTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken
        )
        tokenStorage.saveTokens(tokens)
        return tokens
    }

    suspend fun refreshTokens(): AuthTokens {
        val currentTokens = tokenStorage.tokensFlow.firstOrNull()
            ?: throw IllegalStateException("Нет refresh токена для обновления")
        val response = apiService.refreshToken(
            RefreshTokenRequest(refreshToken = currentTokens.refreshToken)
        )
        val tokens = AuthTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken
        )
        tokenStorage.saveTokens(tokens)
        return tokens
    }

    suspend fun searchVehicles(query: String): List<VehicleSummary> {
        val response = runWithTokenRefresh {
            apiService.searchVehicles(query)
        }
        val vehicles = response.toVehicleSummaries()
        logSample("search", vehicles)
        return vehicles
    }

    suspend fun getRecentVehicles(limit: Int = 10): List<VehicleSummary> {
        val response = runWithTokenRefresh {
            apiService.recentVehicles()
        }
        val vehicles = response.toVehicleSummaries()
            .take(limit)
        logSample("recent", vehicles)
        return vehicles
    }

    suspend fun getVehicleDetail(vin: String): VehicleDetail {
        val response = runWithTokenRefresh {
            apiService.vehicleDetail(vin)
        }
        val detail = response.toVehicleDetail()
            ?: throw IllegalStateException("Не удалось разобрать данные по VIN $vin")
        Log.d(TAG, "Vehicle detail for $vin loaded")
        return detail
    }

    suspend fun searchVehicleByVin(vin: String): VehicleDetail {
        return try {
            val response = runWithTokenRefresh {
                apiService.vehicleByVin(vin)
            }
            response.toVehicleDetail()
                ?: throw IllegalStateException("Не удалось разобрать данные по VIN $vin")
        } catch (error: HttpException) {
            if (error.code() == 404 || error.code() == 500) {
                throw VehicleNotFoundException("Автомобиль с VIN $vin не найден")
            }
            throw error
        }
    }

    suspend fun logout() {
        tokenStorage.clear()
    }

    private suspend fun <T> runWithTokenRefresh(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: HttpException) {
            if (error.code() == 401) {
                refreshTokens()
                block()
            } else {
                throw error
            }
        }
    }

    private fun logSample(source: String, vehicles: List<VehicleSummary>) {
        vehicles.firstOrNull()?.let {
            Log.d(TAG, "Sample from $source list: $it")
        }
    }
}

class VehicleNotFoundException(message: String) : Exception(message)
