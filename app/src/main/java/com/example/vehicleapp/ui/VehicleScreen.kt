package com.example.vehicleapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vehicleapp.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.BitmapFactory
import com.example.vehicleapp.data.model.VehicleDetail
import com.example.vehicleapp.data.model.VehicleSpec
import com.example.vehicleapp.data.model.VehicleSummary
import com.example.vehicleapp.ui.theme.VehicleAPPTheme

@Composable
fun VehicleApp() {
    VehicleAPPTheme {
        val context = LocalContext.current
        val vehicleViewModel: VehicleViewModel = viewModel(
            factory = VehicleViewModelFactory(context)
        )
        val uiState by vehicleViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            vehicleViewModel.loadRecentVehicles()
        }
        VehicleScreen(
            state = uiState,
            onSearchToggle = vehicleViewModel::toggleSearchVisibility,
            onSearchQueryChange = vehicleViewModel::onSearchQueryChange,
            onSearchSubmit = vehicleViewModel::performSearch,
            onVehicleClick = { vehicleViewModel.openVehicleDetail(it.vin) },
            onDetailDismiss = vehicleViewModel::dismissVehicleDetail,
            onDetailErrorDismiss = vehicleViewModel::clearDetailError
        )
    }
}

@Composable
fun VehicleScreen(
    state: VehicleUiState,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onVehicleClick: (VehicleSummary) -> Unit,
    onDetailDismiss: () -> Unit,
    onDetailErrorDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            VehicleTopBar(
                onSearchClick = onSearchToggle
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AuthStatusMessage(
                isAuthenticating = state.isAuthenticating,
                error = state.authError
            )
            if (state.detailError != null && state.selectedVehicle == null) {
                DetailErrorBanner(
                    message = state.detailError,
                    onDismiss = onDetailErrorDismiss
                )
            }
            SearchInput(
                isVisible = state.isSearchVisible,
                enabled = !state.isAuthenticating,
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = onSearchSubmit,
                isLoading = state.isLoading
            )
            VehicleListSection(
                isLoading = state.isLoading,
                vehicles = state.vehicles,
                message = state.listMessage,
                onVehicleClick = onVehicleClick
            )
        }
    }

    if (state.isDetailLoading && state.selectedVehicle == null) {
        DetailLoadingDialog()
    }
    state.selectedVehicle?.let { vehicle ->
        VehicleDetailSheet(
            vehicle = vehicle,
            onDismiss = onDetailDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTopBar(
    onSearchClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bid_sale),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Bid-Sale",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSearchClick,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Поиск"
                )
            }
        }
    )
}

@Composable
private fun SearchInput(
    isVisible: Boolean,
    enabled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean
) {
    val focusRequester = FocusRequester()
    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Поиск по VIN или номеру") },
            enabled = enabled,
            trailingIcon = {
                IconButton(
                    onClick = onSearch,
                    enabled = !isLoading && enabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Выполнить поиск"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
    }
}

@Composable
private fun AuthStatusMessage(
    isAuthenticating: Boolean,
    error: String?
) {
    when {
        isAuthenticating -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
                Text(
                    text = "Авторизация...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        error != null -> {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DetailErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    }
}

@Composable
private fun VehicleListSection(
    isLoading: Boolean,
    vehicles: List<VehicleSummary>,
    message: String?,
    onVehicleClick: (VehicleSummary) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        message != null -> {
            MessageCard(message = message)
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vehicles, key = { it.vin }) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onClick = { onVehicleClick(vehicle) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VehicleMainImage(vehicle.images.firstOrNull())
            if (vehicle.images.size > 1) {
                VehicleImageSlider(vehicle.images.drop(1))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = vehicle.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = vehicle.price.ifBlank { "$ —" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            VehicleSpecChips(vehicle)
        }
    }
}

@Composable
private fun VehicleMainImage(url: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(
            url = url,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VehicleImageSlider(images: List<String>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(images, key = { it }) { image ->
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                RemoteImage(
                    url = image,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleSpecChips(vehicle: VehicleSummary) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SpecChip("Год", vehicle.year)
        SpecChip("Цвет", vehicle.color)
        SpecChip("VIN", vehicle.vin)
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailLoadingDialog() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDetailSheet(
    vehicle: VehicleDetail,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        VehicleDetailBody(vehicle = vehicle, onDismiss = onDismiss)
    }
}

@Composable
private fun VehicleDetailBody(
    vehicle: VehicleDetail,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = vehicle.summary.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        VehicleMainImage(vehicle.summary.images.firstOrNull())
        if (vehicle.summary.images.size > 1) {
            VehicleImageSlider(vehicle.summary.images.drop(1))
        }
        DetailSpecList(vehicle.specs)
        vehicle.description?.let { description ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Описание",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Закрыть")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DetailSpecList(specs: List<VehicleSpec>) {
    if (specs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Характеристики",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        specs.forEach { spec ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = spec.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit
) {
    var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        imageBitmap = if (url.isNullOrBlank()) {
            null
        } else {
            loadRemoteImage(url)
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            placeholder()
        }
    }
}

private suspend fun loadRemoteImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 5000
    }
    try {
        connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

@Preview(showBackground = true)
@Composable
private fun VehicleScreenPreview() {
    val sampleVehicle = VehicleSummary(
        vin = "JT1234567890",
        brand = "Toyota",
        model = "Camry",
        year = "2019",
        color = "Белый",
        price = "2 450 000 ₽",
        images = listOf(
            "https://picsum.photos/seed/1/600/400",
            "https://picsum.photos/seed/2/600/400",
            "https://picsum.photos/seed/3/600/400"
        ),
        raw = emptyMap()
    )
    VehicleAPPTheme {
        VehicleScreen(
            state = VehicleUiState(
                isLoggedIn = true,
                isSearchVisible = true,
                searchQuery = "K123",
                vehicles = listOf(sampleVehicle, sampleVehicle.copy(vin = "WB9876543210", brand = "BMW", model = "X5")),
                selectedVehicle = VehicleDetail(
                    summary = sampleVehicle,
                    specs = listOf(
                        VehicleSpec("Марка", "Toyota"),
                        VehicleSpec("Модель", "Camry"),
                        VehicleSpec("VIN", "JT1234567890"),
                        VehicleSpec("Пробег", "45 000 миль")
                    ),
                    description = "Ухоженный автомобиль без ДТП."
                )
            ),
            onSearchToggle = {},
            onSearchQueryChange = {},
            onSearchSubmit = {},
            onVehicleClick = {},
            onDetailDismiss = {},
            onDetailErrorDismiss = {}
        )
    }
}
