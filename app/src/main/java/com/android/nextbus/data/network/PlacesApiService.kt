package com.android.nextbus.data.network

import android.util.Log
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

class PlacesApiService {
    
    companion object {
        private const val TAG = "PlacesApiService"
        private const val BASE_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        private const val PROXIMITY_RADIUS = 1000 // 1km radius, same as bushop app
    }
    
    suspend fun getNearbyBusStops(
        location: LatLng,
        apiKey: String,
        searchType: String = "transit_station"
    ): Result<List<BusStop>> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(location, searchType, apiKey)
            Log.d(TAG, "Places API URL: $url")
            
            val response = makeHttpRequest(url)
            Log.d(TAG, "API Response: $response")
            
            if (response.isEmpty()) {
                Log.e(TAG, "Empty response from Places API")
                return@withContext Result.failure(Exception("Empty response from Places API"))
            }
            
            val busStops = parseResponse(response)
            Log.d(TAG, "Parsed ${busStops.size} bus stops")
            
            Result.success(busStops)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching nearby bus stops", e)
            Result.failure(e)
        }
    }
    
    private fun buildUrl(location: LatLng, searchType: String, apiKey: String): String {
        return StringBuilder(BASE_URL).apply {
            append("?location=${location.latitude},${location.longitude}")
            append("&radius=$PROXIMITY_RADIUS")
            append("&type=$searchType")
            append("&sensor=true")
            append("&key=$apiKey")
        }.toString()
    }
    
    private fun makeHttpRequest(urlString: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()
            
            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            
            reader.close()
            stringBuilder.toString()
        } catch (e: IOException) {
            Log.e(TAG, "HTTP request failed", e)
            throw e
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun parseResponse(jsonData: String): List<BusStop> {
        return try {
            val jsonObject = JSONObject(jsonData)
            
            // Check for API errors
            if (jsonObject.has("error_message")) {
                val errorMessage = jsonObject.getString("error_message")
                Log.e(TAG, "API Error: $errorMessage")
                throw Exception("Places API Error: $errorMessage")
            }
            
            // Check status
            val status = jsonObject.optString("status", "")
            Log.d(TAG, "API Status: $status")
            
            if (status != "OK" && status != "ZERO_RESULTS") {
                Log.e(TAG, "API returned status: $status")
                if (status == "ZERO_RESULTS") {
                    return emptyList()
                }
                throw Exception("Places API returned status: $status")
            }
            
            // Parse results
            val resultsArray = jsonObject.getJSONArray("results")
            Log.d(TAG, "Found ${resultsArray.length()} results")
            
            parseResults(resultsArray)
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error", e)
            throw Exception("Failed to parse API response", e)
        }
    }
    
    private fun parseResults(resultsArray: JSONArray): List<BusStop> {
        val busStops = mutableListOf<BusStop>()
        
        for (i in 0 until resultsArray.length()) {
            try {
                val placeObject = resultsArray.getJSONObject(i)
                val busStop = parseSinglePlace(placeObject)
                busStops.add(busStop)
                Log.d(TAG, "Parsed bus stop: ${busStop.name} at ${busStop.location}")
            } catch (e: JSONException) {
                Log.w(TAG, "Failed to parse place at index $i", e)
                // Continue parsing other places
            }
        }
        
        return busStops
    }
    
    private fun parseSinglePlace(placeObject: JSONObject): BusStop {
        val name = placeObject.optString("name", "--NA--")
        val vicinity = placeObject.optString("vicinity", "--NA--")
        val placeId = placeObject.optString("place_id", "")
        val reference = placeObject.optString("reference", "")
        val rating = if (placeObject.has("rating")) {
            placeObject.getDouble("rating").toFloat()
        } else null
        
        // Parse geometry
        val geometry = placeObject.getJSONObject("geometry")
        val location = geometry.getJSONObject("location")
        val lat = location.getDouble("lat")
        val lng = location.getDouble("lng")
        
        // Parse types
        val types = mutableListOf<String>()
        if (placeObject.has("types")) {
            val typesArray = placeObject.getJSONArray("types")
            for (j in 0 until typesArray.length()) {
                types.add(typesArray.getString(j))
            }
        }
        
        return BusStop(
            id = placeId.ifEmpty { reference },
            name = name,
            vicinity = vicinity,
            location = LatLng(lat, lng),
            reference = reference,
            placeId = placeId,
            rating = rating,
            types = types
        )
    }
    
    // Additional search methods for different types of transit stops
    suspend fun searchMultipleTypes(
        location: LatLng,
        apiKey: String
    ): Result<List<BusStop>> = withContext(Dispatchers.IO) {
        val searchTypes = listOf("bus_station", "transit_station", "subway_station")
        val allBusStops = mutableListOf<BusStop>()
        
        for (searchType in searchTypes) {
            try {
                val result = getNearbyBusStops(location, apiKey, searchType)
                result.getOrNull()?.let { busStops ->
                    allBusStops.addAll(busStops)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to search for type: $searchType", e)
            }
        }
        
        // Remove duplicates based on place_id or location proximity
        val uniqueBusStops = removeDuplicates(allBusStops)
        Log.d(TAG, "Found ${uniqueBusStops.size} unique bus stops from all search types")
        
        Result.success(uniqueBusStops)
    }
    
    private fun removeDuplicates(busStops: List<BusStop>): List<BusStop> {
        val uniqueStops = mutableMapOf<String, BusStop>()
        
        for (stop in busStops) {
            val key = if (stop.placeId?.isNotEmpty() == true) {
                stop.placeId
            } else {
                // Use location-based key for stops without place_id
                "${stop.location.latitude}_${stop.location.longitude}"
            }
            
            // Keep the stop with more information (e.g., has rating)
            if (!uniqueStops.containsKey(key) || 
                (stop.rating != null && uniqueStops[key]?.rating == null)) {
                uniqueStops[key] = stop
            }
        }
        
        return uniqueStops.values.toList()
    }
}
