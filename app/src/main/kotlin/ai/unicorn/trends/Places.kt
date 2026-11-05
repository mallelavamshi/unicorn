/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.trends

import ai.unicorn.UnicornPrefs
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the user is, as names plus coordinates rounded to about 1 km: "Prosper" in "Texas", "US". Used only to make
 * hunches local; Unicorn's server doesn't store it.
 */
data class Place(
    val neighbourhood: String?,
    val city: String?,
    val country: String?,
    val region: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** The user's neighborhood from the phone's approximate location, only when they turned that on. */
class PlaceRepository(private val context: Context, private val prefs: UnicornPrefs) {
    private companion object {
        const val PLACE_TTL_MS = 6 * 60 * 60 * 1000L
        const val RETRY_AFTER_FAILURE_MS = 15 * 60 * 1000L
        const val LOOKUP_TIMEOUT_MS = 4_000L
    }

    @Volatile
    private var failedAt = 0L

    private val placePrefs = context.getSharedPreferences("unicorn_place", 0)

    /** The neighborhood, refreshed every few hours. Null when it is off or unavailable. */
    suspend fun neighbourhood(): Place? {
        if (!prefs.useNeighbourhood) return null
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val at = placePrefs.getLong("at", 0L)
        if (System.currentTimeMillis() - at < PLACE_TTL_MS) {
            val cached = Place(
                placePrefs.getString("neighbourhood", null), placePrefs.getString("city", null),
                placePrefs.getString("country", null), placePrefs.getString("region", null),
                placePrefs.getString("lat", null)?.toDoubleOrNull(), placePrefs.getString("lon", null)?.toDoubleOrNull(),
            )
            if (cached.neighbourhood != null || cached.city != null) return cached
        }
        // Without a fix the lookup can wait forever; hunches then use the connection's city instead
        if (System.currentTimeMillis() - failedAt < RETRY_AFTER_FAILURE_MS) return null
        val found = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { locate() }
        if (found == null) {
            failedAt = System.currentTimeMillis()
            return null
        }
        placePrefs.edit().putLong("at", System.currentTimeMillis())
            .putString("neighbourhood", found.neighbourhood).putString("city", found.city)
            .putString("country", found.country).putString("region", found.region)
            .putString("lat", found.lat?.toString()).putString("lon", found.lon?.toString()).apply()
        return found
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun locate(): Place? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        // A last known fix older than half an hour may be from another part of town
        val recent = providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            ?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 30 * 60 * 1_000_000_000L }
        val location = recent
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine { done ->
                    runCatching {
                        manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { done.resume(it) }
                    }.onFailure { done.resume(null) }
                }
            } else {
                null
            }
        location ?: return@withContext null
        val lat = (location.latitude * 100).roundToInt() / 100.0
        val lon = (location.longitude * 100).roundToInt() / 100.0
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.ENGLISH)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { done ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) = done.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = done.resume(null)
                })
            }
        } else {
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
        } ?: return@withContext null
        Place(
            address.subLocality ?: address.locality, address.locality ?: address.subAdminArea, address.countryCode,
            address.adminArea, lat, lon,
        )
    }
}
