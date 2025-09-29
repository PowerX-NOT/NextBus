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
import java.net.HttpURLConnection
import java.net.URL

class NearbyBusStopService {
    
    companion object {
        private const val TAG = "NearbyBusStopService"
        private const val PROXIMITY_RADIUS = 1000 // 1km radius
        private const val PLACES_API_BASE_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
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
