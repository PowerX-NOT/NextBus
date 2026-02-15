package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import android.location.Location

class NearbyBusStopService {
    
    companion object {
        private const val TAG = "NearbyBusStopService"
        private const val PROXIMITY_RADIUS = 1000 // 1km radius
        private const val PLACES_API_BASE_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    }

    suspend fun resolveBusStopToGooglePlace(
        stopName: String,
        latitude: Double,
        longitude: Double,
        polylinePoints: List<LatLng>? = null
    ): Result<BusStop?> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = stopName.trim()
            if (trimmedName.isEmpty()) return@withContext Result.success(null)

            val encodedKeyword = URLEncoder.encode(trimmedName, "UTF-8")
            val searchTypes = listOf("bus_station", "transit_station")
            val radiusMeters = 1200

            val candidates = mutableListOf<BusStop>()
            for (type in searchTypes) {
                val url = StringBuilder(PLACES_API_BASE_URL).apply {
                    append("?location=$latitude,$longitude")
                    append("&radius=$radiusMeters")
                    append("&type=$type")
                    append("&keyword=$encodedKeyword")
                    append("&sensor=true")
                    append("&key=${BuildConfig.GOOGLE_API_KEY}")
                }.toString()

                val response = downloadUrl(url)
                if (response.isNotEmpty()) {
                    candidates.addAll(parseResponse(response))
                }
            }

            val uniqueCandidates = candidates.distinctBy { it.placeId ?: it.name }
            if (uniqueCandidates.isEmpty()) return@withContext Result.success(null)

            val best = uniqueCandidates.minByOrNull { candidate ->
                val dist = distanceMeters(latitude, longitude, candidate.location.latitude, candidate.location.longitude)
                val namePenalty = namePenalty(trimmedName, candidate.name)
                val polyPenalty = polylinePoints?.let { pts ->
                    distanceToPolylineMeters(pts, candidate.location)
                } ?: 0.0
                dist + namePenalty + (polyPenalty * 1.5)
            }

            Result.success(best)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stop to Google Place: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getNearbyBusStops(
        latitude: Double,
        longitude: Double
    ): Result<List<BusStop>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for nearby bus stops at: $latitude, $longitude")
            
            // Try multiple search types for better results
            val searchTypes = listOf("bus_station", "transit_station", "subway_station")
            val allBusStops = mutableListOf<BusStop>()
            
            for (searchType in searchTypes) {
                try {
                    val url = buildPlacesUrl(latitude, longitude, searchType)
                    Log.d(TAG, "Places API URL for $searchType: $url")
                    
                    val response = downloadUrl(url)
                    if (response.isNotEmpty()) {
                        val busStops = parseResponse(response)
                        allBusStops.addAll(busStops)
                        Log.d(TAG, "Found ${busStops.size} places for type: $searchType")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching for type $searchType: ${e.message}")
                }
            }
            
            // Remove duplicates based on place_id
            val uniqueBusStops = allBusStops.distinctBy { it.placeId ?: it.name }
            Log.d(TAG, "Total unique bus stops found: ${uniqueBusStops.size}")
            
            Result.success(uniqueBusStops)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby bus stops: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun buildPlacesUrl(latitude: Double, longitude: Double, placeType: String): String {
        return StringBuilder(PLACES_API_BASE_URL).apply {
            append("?location=$latitude,$longitude")
            append("&radius=$PROXIMITY_RADIUS")
            append("&type=$placeType")
            append("&sensor=true")
            append("&key=${BuildConfig.GOOGLE_API_KEY}")
        }.toString()
    }
    
    private fun downloadUrl(urlString: String): String {
        var data = ""
        var inputStream: java.io.InputStream? = null
        var urlConnection: HttpURLConnection? = null
        
        try {
            val url = URL(urlString)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.connect()
            
            inputStream = urlConnection.inputStream
            val br = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            
            data = sb.toString()
            br.close()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading URL: ${e.message}")
            e.printStackTrace()
        } finally {
            inputStream?.close()
            urlConnection?.disconnect()
        }
        
        Log.d(TAG, "Downloaded data length: ${data.length}")
        return data
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results.firstOrNull()?.toDouble() ?: Double.MAX_VALUE
    }

    private fun namePenalty(query: String, candidate: String): Double {
        val q = query.lowercase()
        val c = candidate.lowercase()
        return when {
            c == q -> 0.0
            c.contains(q) || q.contains(c) -> 50.0
            else -> 400.0
        }
    }

    private fun distanceToPolylineMeters(points: List<LatLng>, target: LatLng): Double {
        if (points.size < 2) return Double.MAX_VALUE
        val nearest = nearestPointOnPolylineSegments(points, target)
        return distanceMeters(target.latitude, target.longitude, nearest.latitude, nearest.longitude)
    }

    private fun nearestPointOnPolylineSegments(points: List<LatLng>, target: LatLng): LatLng {
        if (points.isEmpty()) return target
        if (points.size == 1) return points.first()

        val refLat = target.latitude
        var bestPoint = points.first()
        var bestDist = Double.MAX_VALUE

        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val p = projectPointToSegment(refLat, a, b, target)
            val d = distanceMeters(target.latitude, target.longitude, p.latitude, p.longitude)
            if (d < bestDist) {
                bestDist = d
                bestPoint = p
            }
        }

        return bestPoint
    }

    private fun projectPointToSegment(refLat: Double, a: LatLng, b: LatLng, p: LatLng): LatLng {
        val ax = lngToMeters(refLat, a.longitude)
        val ay = latToMeters(a.latitude)
        val bx = lngToMeters(refLat, b.longitude)
        val by = latToMeters(b.latitude)
        val px = lngToMeters(refLat, p.longitude)
        val py = latToMeters(p.latitude)

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLen2 = (abx * abx) + (aby * aby)
        if (abLen2 <= 0.0) return a

        var t = ((apx * abx) + (apy * aby)) / abLen2
        if (t < 0.0) t = 0.0
        if (t > 1.0) t = 1.0

        val x = ax + (t * abx)
        val y = ay + (t * aby)
        val lat = metersToLat(y)
        val lng = metersToLng(refLat, x)
        return LatLng(lat, lng)
    }

    private fun latToMeters(lat: Double): Double {
        return lat * 111_320.0
    }

    private fun lngToMeters(refLat: Double, lng: Double): Double {
        val latRad = Math.toRadians(refLat)
        return lng * (111_320.0 * kotlin.math.cos(latRad))
    }

    private fun metersToLat(yMeters: Double): Double {
        return yMeters / 111_320.0
    }

    private fun metersToLng(refLat: Double, xMeters: Double): Double {
        val latRad = Math.toRadians(refLat)
        val denom = 111_320.0 * kotlin.math.cos(latRad)
        return if (denom == 0.0) 0.0 else xMeters / denom
    }
    
    private fun parseResponse(jsonData: String): List<BusStop> {
        val busStops = mutableListOf<BusStop>()
        
        try {
            Log.d(TAG, "Parsing JSON data: ${jsonData.take(200)}...")
            
            val jsonObject = JSONObject(jsonData)
            
            // Check if the response has an error
            if (jsonObject.has("error_message")) {
                Log.e(TAG, "API Error: ${jsonObject.getString("error_message")}")
                return emptyList()
            }
            
            if (jsonObject.has("status")) {
                val status = jsonObject.getString("status")
                Log.d(TAG, "API Status: $status")
                if (status != "OK" && status != "ZERO_RESULTS") {
                    Log.e(TAG, "API returned status: $status")
                    return emptyList()
                }
            }
            
            val jsonArray = jsonObject.getJSONArray("results")
            Log.d(TAG, "Found ${jsonArray.length()} results")
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val placeJson = jsonArray.getJSONObject(i)
                    val busStop = parseBusStop(placeJson)
                    if (busStop != null) {
                        busStops.add(busStop)
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing place at index $i: ${e.message}")
                }
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
            e.printStackTrace()
        }
        
        return busStops
    }
    
    private fun parseBusStop(jsonObject: JSONObject): BusStop? {
        return try {
            val placeName = if (jsonObject.has("name")) {
                jsonObject.getString("name")
            } else {
                "--NA--"
            }
            
            val vicinity = if (jsonObject.has("vicinity")) {
                jsonObject.getString("vicinity")
            } else {
                "--NA--"
            }
            
            val geometry = jsonObject.getJSONObject("geometry")
            val location = geometry.getJSONObject("location")
            val latitude = location.getDouble("lat")
            val longitude = location.getDouble("lng")
            
            val placeId = if (jsonObject.has("place_id")) {
                jsonObject.getString("place_id")
            } else null
            
            val reference = if (jsonObject.has("reference")) {
                jsonObject.getString("reference")
            } else null
            
            val rating = if (jsonObject.has("rating")) {
                jsonObject.getDouble("rating")
            } else null
            
            val types = if (jsonObject.has("types")) {
                val typesArray = jsonObject.getJSONArray("types")
                (0 until typesArray.length()).map { typesArray.getString(it) }
            } else emptyList()
            
            BusStop(
                id = placeId ?: reference ?: placeName,
                name = placeName,
                vicinity = vicinity,
                location = LatLng(latitude, longitude),
                reference = reference,
                placeId = placeId,
                rating = rating,
                types = types
            )
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing bus stop: ${e.message}")
            null
        }
    }
}
