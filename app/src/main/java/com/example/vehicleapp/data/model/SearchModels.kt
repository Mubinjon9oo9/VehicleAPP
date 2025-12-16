package com.example.vehicleapp.data.model

import com.squareup.moshi.Json
import java.text.NumberFormat
import java.util.Locale

data class SearchResponse(
    @Json(name = "results") val results: List<Map<String, Any?>> = emptyList()
)

data class VehicleListResponse(
    @Json(name = "vehicles") val vehicles: List<Map<String, Any?>> = emptyList()
)

data class VehicleDetailResponse(
    @Json(name = "vehicle") val vehicle: Map<String, Any?> = emptyMap()
)

data class VehicleSummary(
    val vin: String,
    val brand: String,
    val model: String,
    val year: String,
    val color: String,
    val price: String,
    val images: List<String>,
    val raw: Map<String, Any?>
) {
    val title: String = listOf(brand, model)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { vin }
}

data class VehicleDetail(
    val summary: VehicleSummary,
    val specs: List<VehicleSpec>,
    val description: String?
)

data class VehicleSpec(
    val label: String,
    val value: String
)

fun SearchResponse.toVehicleSummaries(): List<VehicleSummary> =
    results.mapNotNull { it.toVehicleSummary() }

fun VehicleListResponse.toVehicleSummaries(): List<VehicleSummary> =
    vehicles.mapNotNull { it.toVehicleSummary() }

fun VehicleDetailResponse.toVehicleDetail(): VehicleDetail? =
    vehicle.toVehicleDetailData()

private fun Map<String, Any?>.toVehicleSummary(): VehicleSummary? {
    val vin = firstNonEmpty(listOf("vin", "VIN", "Vin", "vehicle_vin", "id"))
    if (vin.isBlank()) return null
    val brand = firstNonEmpty(listOf("brand", "make", "manufacturer"))
    val model = firstNonEmpty(listOf("model", "vehicle_model", "car_model"))
    val year = firstNonEmpty(listOf("year", "manufacture_year", "model_year"))
    val color = firstNonEmpty(listOf("color", "colour", "body_color"))
    val priceRaw = firstNonEmpty(listOf("price_usd", "price", "price_rub", "cost", "amount"))
    val images = extractImageUrls(this)
    val price = formatPrice(priceRaw)
    return VehicleSummary(
        vin = vin,
        brand = brand,
        model = model,
        year = year,
        color = color,
        price = price,
        images = images,
        raw = this
    )
}

private fun Map<String, Any?>.toVehicleDetailData(): VehicleDetail? {
    val summary = toVehicleSummary() ?: return null
    val description = firstNonEmpty(
        listOf("description", "notes", "comment", "history", "summary")
    ).ifBlank { null }
    val specs = buildSpecs(summary, this)
    return VehicleDetail(
        summary = summary,
        specs = specs,
        description = description
    )
}

private fun buildSpecs(
    summary: VehicleSummary,
    source: Map<String, Any?>
): List<VehicleSpec> {
    val specDefinitions = listOf(
        SpecCandidate("Марка", listOf("brand", "make", "manufacturer"), summary.brand),
        SpecCandidate("Модель", listOf("model", "vehicle_model"), summary.model),
        SpecCandidate("VIN", listOf("vin", "vehicle_vin"), summary.vin),
        SpecCandidate("Год", listOf("year", "model_year", "manufacture_year"), summary.year),
        SpecCandidate("Цвет кузова", listOf("color", "body_color"), summary.color),
        SpecCandidate("Цена", listOf("price", "price_usd", "cost", "amount"), summary.price),
        SpecCandidate("Пробег", listOf("mileage", "odometer", "odometer_value")),
        SpecCandidate("Двигатель", listOf("engine", "engine_type", "engine_description")),
        SpecCandidate("КПП", listOf("transmission", "gearbox")),
        SpecCandidate("Привод", listOf("drivetrain", "drive_type")),
        SpecCandidate("Топливо", listOf("fuel", "fuel_type")),
        SpecCandidate("Кузов", listOf("body", "body_type")),
        SpecCandidate("Владельцы", listOf("owners", "owner_count")),
        SpecCandidate("Регион", listOf("region", "location", "city"))
    )

    return specDefinitions.mapNotNull { spec ->
        val value = spec.prefilled.ifBlank {
            source.firstNonEmpty(spec.keys)
        }
        value.takeIf { it.isNotBlank() }?.let { VehicleSpec(spec.label, it) }
    }
}

private data class SpecCandidate(
    val label: String,
    val keys: List<String>,
    val prefilled: String = ""
)

private fun Map<String, Any?>.firstNonEmpty(keys: List<String>): String =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.toString()?.takeIf { it.isNotBlank() }
    } ?: ""

private fun extractImageUrls(source: Map<String, Any?>): List<String> {
    val imageKeys = listOf("images", "photos", "gallery", "image_urls", "pictures")
    imageKeys.forEach { key ->
        val value = source[key]
        val urls = when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.takeIf { url -> url.isNotBlank() } }
            is Array<*> -> value.mapNotNull { it?.toString()?.takeIf { url -> url.isNotBlank() } }
            is String -> value.split(",").mapNotNull { it.trim().takeIf { url -> url.isNotBlank() } }
            else -> emptyList()
        }
        if (urls.isNotEmpty()) {
            return urls
        }
    }
    return emptyList()
}

private fun formatPrice(raw: String): String {
    if (raw.isBlank()) return "$ —"
    val normalized = raw.replace(" ", "").replace(",", ".")
    val digitsOnly = normalized.replace(Regex("[^0-9.]"), "")
    val number = digitsOnly.toDoubleOrNull()
    return number?.let {
        NumberFormat.getCurrencyInstance(Locale.US).format(it)
    } ?: raw
}
