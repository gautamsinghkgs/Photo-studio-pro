package com.photostudio.pro.feature.idphoto

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One passport / ID photo specification, loaded from assets/countries.json.
 *
 * ⚠️ GOVERNMENTS CHANGE THESE WITHOUT NOTICE. Every entry carries a sourceUrl.
 * Before shipping, spot-check at least the priority-1 markets against the
 * official page. And NEVER claim "guaranteed acceptance" in the app or the
 * store listing — that is a Google Play Deceptive Behavior risk. The shipped
 * disclaimer is strings.xml -> id_disclaimer.
 */
@Serializable
data class CountrySpec(
    val id: String,
    val name: String,
    val flag: String = "",
    val priority: Int = 3,
    val widthMm: Double,
    val heightMm: Double,
    val dpi: Int = 300,
    val bg: String = "#FFFFFF",
    @SerialName("headMinPct") val headMinPct: Double = 0.70,
    @SerialName("headMaxPct") val headMaxPct: Double = 0.80,
    @SerialName("eyeMinPct") val eyeMinPct: Double = 0.0,
    @SerialName("eyeMaxPct") val eyeMaxPct: Double = 1.0,
    val maxKb: Int = 1024,
    val format: String = "JPEG",
    val notes: String = "",
    val sourceUrl: String = "",
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
) {
    /** Exact output pixel dimensions for this spec. */
    val widthPx: Int get() = pixelWidth ?: IdPhotoMath.mmToPx(widthMm, dpi)
    val heightPx: Int get() = pixelHeight ?: IdPhotoMath.mmToPx(heightMm, dpi)

    val backgroundArgb: Int get() {
        val clean = bg.removePrefix("#")
        return try { (0xFF shl 24) or clean.toLong(16).toInt() } catch (_: Exception) { 0xFFFFFFFF.toInt() }
    }

    /** True when the spec actually constrains head size. */
    val hasHeadBand: Boolean get() = headMaxPct > headMinPct && headMinPct > 0.0

    /** True when the spec constrains the eye line. */
    val hasEyeBand: Boolean get() = !(eyeMinPct <= 0.0 && eyeMaxPct >= 1.0)
}

@Serializable
private data class CountriesFile(
    @SerialName("_meta") val meta: JsonMeta? = null,
    val countries: List<CountrySpec> = emptyList(),
)

@Serializable
private data class JsonMeta(
    val schemaVersion: Int = 1,
    val generatedOn: String = "",
    val warning: String = "",
    val units: String = "",
)

/** Loads and caches countries.json from assets. */
class CountryRepository(private val context: Context) {

    @Volatile private var cache: List<CountrySpec>? = null

    fun all(): List<CountrySpec> = cache ?: load().also { cache = it }

    fun priorityOnes(): List<CountrySpec> = all().filter { it.priority == 1 }

    fun byId(id: String): CountrySpec? = all().firstOrNull { it.id == id }

    fun search(q: String): List<CountrySpec> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return all()
        return all().filter { it.name.lowercase().contains(s) || it.id.contains(s) }
    }

    /** Which specs a free user may use. Pro unlocks everything. */
    fun freeSubset(limit: Int): List<CountrySpec> =
        all().sortedWith(compareBy({ it.priority }, { it.name })).take(limit.coerceAtLeast(1))

    private fun load(): List<CountrySpec> = runCatching {
        val text = context.assets.open("countries.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<CountriesFile>(text)
            .countries
    }.getOrElse {
        // A malformed asset must not crash the app or blank the tool. Fall back
        // to the two specs we are most confident about so the tool still works.
        listOf(
            CountrySpec("in", "India", "🇮🇳", 1, 35.0, 45.0, 300, "#FFFFFF", 0.50, 0.75, 0.50, 0.70, 200),
            CountrySpec("us", "United States", "🇺🇸", 1, 51.0, 51.0, 300, "#FFFFFF", 0.50, 0.69, 0.56, 0.69, 240),
        )
    }
}
