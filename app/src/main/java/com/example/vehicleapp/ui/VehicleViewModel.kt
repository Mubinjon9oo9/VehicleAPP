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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class VehicleState(
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

sealed interface VehicleIntent {
    data object ToggleSearchVisibility : VehicleIntent
    data class SearchQueryChanged(val value: String) : VehicleIntent
    data object SubmitSearch : VehicleIntent
    data object LoadRecentVehicles : VehicleIntent
    data class OpenVehicleDetail(val vin: String) : VehicleIntent
    data object DismissVehicleDetail : VehicleIntent
    data object ClearDetailError : VehicleIntent
    data class TokensChanged(val isLoggedIn: Boolean) : VehicleIntent
}

class VehicleViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state
    private val intents = MutableSharedFlow<VehicleIntent>(extraBufferCapacity = 64)
    private val authMutex = Mutex()
    private var hasLoadedInitialVehicles = false

    init {
        viewModelScope.launch {
            intents.collect { intent ->
                handleIntent(intent)
            }
        }
        viewModelScope.launch {
            repository.tokensFlow.collectLatest { tokens ->
                intents.emit(VehicleIntent.TokensChanged(tokens != null))
            }
        }
    }

    fun onIntent(intent: VehicleIntent) {
        if (!intents.tryEmit(intent)) {
            viewModelScope.launch {
                intents.emit(intent)
            }
        }
    }

    private suspend fun handleIntent(intent: VehicleIntent) {
        when (intent) {
            VehicleIntent.ToggleSearchVisibility -> toggleSearchVisibility()
            is VehicleIntent.SearchQueryChanged -> onSearchQueryChange(intent.value)
            VehicleIntent.SubmitSearch -> submitSearch()
            VehicleIntent.LoadRecentVehicles -> loadRecentVehicles()
            is VehicleIntent.OpenVehicleDetail -> openVehicleDetail(intent.vin)
            VehicleIntent.DismissVehicleDetail -> dismissVehicleDetail()
            VehicleIntent.ClearDetailError -> clearDetailError()
            is VehicleIntent.TokensChanged -> onTokensChanged(intent.isLoggedIn)
        }
    }

    private suspend fun onTokensChanged(isLoggedIn: Boolean) {
        reduce { state -> state.copy(isLoggedIn = isLoggedIn) }
        if (!isLoggedIn) {
            hasLoadedInitialVehicles = false
            reduce { it.copy(selectedVehicle = null) }
            ensureAuthenticated()
        } else if (!hasLoadedInitialVehicles) {
            loadRecentVehicles()
        }
    }

    private suspend fun toggleSearchVisibility() {
        reduce { it.copy(isSearchVisible = !it.isSearchVisible) }
    }

    private suspend fun onSearchQueryChange(value: String) {
        reduce { it.copy(searchQuery = value, listMessage = null) }
    }

    private suspend fun submitSearch() {
        val query = state.value.searchQuery
        val normalizedVin = normalizeVin(query)
        if (normalizedVin.isEmpty()) {
            reduce { it.copy(listMessage = "Введите запрос для поиска") }
            return
        }
        val authenticated = ensureAuthenticated()
        if (!authenticated) {
            return
        }
        reduce { it.copy(isLoading = true, listMessage = null, selectedVehicle = null) }
        try {
            val detail = repository.searchVehicleByVin(normalizedVin)
            reduce {
                it.copy(
                    isLoading = false,
                    selectedVehicle = detail,
                    listMessage = null,
                    detailError = null
                )
            }
        } catch (notFound: VehicleNotFoundException) {
            reduce {
                it.copy(
                    isLoading = false,
                    listMessage = notFound.message,
                    selectedVehicle = null
                )
            }
        } catch (error: Exception) {
            reduce {
                it.copy(
                    isLoading = false,
                    listMessage = error.message ?: "Ошибка поиска"
                )
            }
        }
    }

    private suspend fun loadRecentVehicles() {
        val authenticated = ensureAuthenticated()
        if (!authenticated) {
            return
        }
        reduce { it.copy(isLoading = true, listMessage = null) }
        try {
            val vehicles = repository.getRecentVehicles()
            hasLoadedInitialVehicles = true
            reduce {
                it.copy(
                    isLoading = false,
                    vehicles = vehicles,
                    listMessage = if (vehicles.isEmpty()) "Данные отсутствуют" else null
                )
            }
        } catch (error: Exception) {
            reduce {
                it.copy(
                    isLoading = false,
                    listMessage = error.message ?: "Не удалось загрузить данные"
                )
            }
        }
    }

    private suspend fun openVehicleDetail(vin: String) {
        val authenticated = ensureAuthenticated()
        if (!authenticated) {
            return
        }
        reduce { it.copy(isDetailLoading = true, detailError = null) }
        try {
            val detail = repository.getVehicleDetail(vin)
            reduce {
                it.copy(
                    isDetailLoading = false,
                    selectedVehicle = detail,
                    detailError = null
                )
            }
        } catch (error: Exception) {
            reduce {
                it.copy(
                    isDetailLoading = false,
                    detailError = error.message ?: "Не удалось получить данные автомобиля",
                    selectedVehicle = null
                )
            }
        }
    }

    private suspend fun dismissVehicleDetail() {
        reduce { it.copy(selectedVehicle = null) }
    }

    private suspend fun clearDetailError() {
        reduce { it.copy(detailError = null) }
    }

    private fun normalizeVin(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() }

    private suspend fun ensureAuthenticated(): Boolean = authMutex.withLock {
        if (state.value.isLoggedIn) {
            return@withLock true
        }
        val credentials = CredentialsProvider.credentials()
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            reduce {
                it.copy(
                    authError = "Заполните логин и пароль в CredentialsProvider.kt",
                    isAuthenticating = false
                )
            }
            return@withLock false
        }
        reduce { it.copy(isAuthenticating = true, authError = null) }
        return@withLock try {
            repository.login(credentials.username, credentials.password)
            reduce { it.copy(isAuthenticating = false, authError = null) }
            true
        } catch (error: Exception) {
            reduce {
                it.copy(
                    isAuthenticating = false,
                    authError = error.message ?: "Не удалось авторизоваться"
                )
            }
            false
        }
    }

    private fun reduce(transform: (VehicleState) -> VehicleState) {
        _state.update(transform)
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
