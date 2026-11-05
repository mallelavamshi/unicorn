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

import ai.unicorn.UnicornCore
import java.net.URLEncoder
import java.util.TimeZone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A playful guess about what the area is asking, with [why] it was guessed. Never presented as real data. */
@Serializable
data class Hunch(val emoji: String, val text: String, val kind: String, val why: String)

@Serializable
data class HunchPlace(val city: String? = null, val hood: String? = null, val region: String? = null, val country: String)

@Serializable
data class Hunches(val place: HunchPlace, val hunches: List<Hunch> = emptyList(), val credits: List<String> = emptyList()) {
    /** "Prosper", or the state when the town isn't known. */
    val town get() = place.hood ?: place.city ?: place.region
}

/**
 * Hunches from the server, built from the weather, the calendar, the time and the town. The town name and rounded
 * coordinates are sent only when the user turned on their neighborhood; otherwise the server uses the connection's
 * approximate city. The last result is kept so the screen has something to show offline.
 */
class HunchesRepository(private val core: UnicornCore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = core.appContext.getSharedPreferences("unicorn_hunches", 0)

    /** The last answer, when it was fetched, and whether it used the neighborhood (turning that on refetches). */
    @Volatile
    private var recent: Triple<Long, Boolean, Hunches>? = null

    /** Screens that open together share one request instead of each looking up the location. */
    private val inFlight = Mutex()

    /** The last hunches shown, for an instant first paint while fresh ones load. */
    fun cached(): Hunches? = recent?.third
        ?: prefs.getString("last", null)?.let { runCatching { json.decodeFromString(Hunches.serializer(), it) }.getOrNull() }

    suspend fun fetch(): Hunches? = inFlight.withLock { load() }

    private suspend fun load(): Hunches? {
        val local = core.prefs.useNeighbourhood
        recent?.let { (at, usedLocal, hunches) ->
            if (usedLocal == local && System.currentTimeMillis() - at < 10 * 60 * 1000L) return hunches
        }
        val place = runCatching { core.places.neighbourhood() }.getOrNull()
        val params = buildList {
            add("tz" to TimeZone.getDefault().id)
            place?.country?.let { add("country" to it) }
            place?.region?.let { add("region" to it) }
            place?.city?.let { add("city" to it) }
            place?.neighbourhood?.takeIf { it != place.city }?.let { add("hood" to it) }
            if (place?.lat != null && place.lon != null) {
                add("lat" to place.lat.toString())
                add("lon" to place.lon.toString())
            }
        }.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return runCatching {
            val body = core.ai.get("/v1/hunches?$params")
            json.decodeFromString(Hunches.serializer(), body).also {
                prefs.edit().putString("last", body).apply()
                recent = Triple(System.currentTimeMillis(), local, it)
            }
        }.getOrElse {
            prefs.getString("last", null)?.let { runCatching { json.decodeFromString(Hunches.serializer(), it) }.getOrNull() }
        }
    }
}
