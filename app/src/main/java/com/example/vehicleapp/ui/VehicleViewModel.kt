package com.example.vehicleapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.vehicleapp.data.CredentialsProvider
import com.example.vehicleapp.data.VehicleRepository
import com.example.vehicleapp.data.VehicleNotFoundException
import com.example.vehicleapp.data.local.TokenStorage
import com.example.vehicleapp.data.model.VehicleDetail
import com.example.vehicleapp.data.model.VehicleSummary
import com.example.vehicleapp.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class VehicleUiState(
    val isLoggedIn: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val listMessage: String? = null,
    val vehicles: List<VehicleSummary> = emptyList(),
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val selectedVehicle: VehicleDetail? = null
)

class VehicleViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState
    private val authMutex = Mutex()
    private var hasLoadedInitialVehicles = false

    init {
        viewModelScope.launch {
            repository.tokensFlow.collectLatest { tokens ->
                _uiState.update { state ->
                    state.copy(isLoggedIn = tokens != null)
                }
                if (tokens == null) {
                    hasLoadedInitialVehicles = false
                    _uiState.update { it.copy(selectedVehicle = null) }
                    ensureAuthenticated()
                } else if (!hasLoadedInitialVehicles) {
                    loadRecentVehicles()
                }
            }
        }
    }

    fun toggleSearchVisibility() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible) }
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value, listMessage = null) }
    }

    fun performSearch() {
        val query = uiState.value.searchQuery
        val normalizedVin = normalizeVin(query)
        if (normalizedVin.isEmpty()) {
            _uiState.update { it.copy(listMessage = "Введите запрос для поиска") }
            return
        }
        viewModelScope.launch {
            val authenticated = ensureAuthenticated()
            if (!authenticated) {
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, listMessage = null, selectedVehicle = null) }
            try {
                val detail = repository.searchVehicleByVin(normalizedVin)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedVehicle = detail,
                        listMessage = null,
                        detailError = null
                    )
                }
            } catch (notFound: VehicleNotFoundException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        listMessage = notFound.message,
                        selectedVehicle = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        listMessage = error.message ?: "Ошибка поиска"
                    )
                }
            }
        }
    }

    fun loadRecentVehicles() {
        viewModelScope.launch {
            val authenticated = ensureAuthenticated()
            if (!authenticated) {
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, listMessage = null) }
            try {
                val vehicles = repository.getRecentVehicles()
                hasLoadedInitialVehicles = true
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        vehicles = vehicles,
                        listMessage = if (vehicles.isEmpty()) "Данные отсутствуют" else null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        listMessage = error.message ?: "Не удалось загрузить данные"
                    )
                }
            }
        }
    }

    fun openVehicleDetail(vin: String) {
        viewModelScope.launch {
            val authenticated = ensureAuthenticated()
            if (!authenticated) {
                return@launch
            }
            _uiState.update { it.copy(isDetailLoading = true, detailError = null) }
            try {
                val detail = repository.getVehicleDetail(vin)
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        selectedVehicle = detail,
                        detailError = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        detailError = error.message ?: "Не удалось получить данные автомобиля",
                        selectedVehicle = null
                    )
                }
            }
        }
    }

    fun dismissVehicleDetail() {
        _uiState.update { it.copy(selectedVehicle = null) }
    }

    fun clearDetailError() {
        _uiState.update { it.copy(detailError = null) }
    }

    private fun normalizeVin(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() }

    private suspend fun ensureAuthenticated(): Boolean = authMutex.withLock {
        if (uiState.value.isLoggedIn) {
            return@withLock true
        }
        val credentials = CredentialsProvider.credentials()
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            _uiState.update {
                it.copy(
                    authError = "Заполните логин и пароль в CredentialsProvider.kt",
                    isAuthenticating = false
                )
            }
            return@withLock false
        }
        _uiState.update { it.copy(isAuthenticating = true, authError = null) }
        return@withLock try {
            repository.login(credentials.username, credentials.password)
            _uiState.update { it.copy(isAuthenticating = false, authError = null) }
            true
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    authError = error.message ?: "Не удалось авторизоваться"
                )
            }
            false
        }
    }
}

class VehicleViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
            val tokenStorage = TokenStorage(context)
            val api = NetworkModule.provideVehicleApi(tokenStorage)
            val repository = VehicleRepository(api, tokenStorage)
            return VehicleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
