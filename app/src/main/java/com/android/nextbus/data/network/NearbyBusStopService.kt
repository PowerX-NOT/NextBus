package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.data.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class NearbyBusStopService {
    
    companion object {
        private const val TAG = "NearbyBusStopService"
        private const val BMTC_NEARBY_API_URL = "https://bmtcmobileapi.karnataka.gov.in/WebAPI/NearbyStations_v2"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    suspend fun getNearbyBusStops(
        latitude: Double,
        longitude: Double
    ): Result<List<BusStop>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for nearby bus stops using BMTC API at: $latitude, $longitude")
            
            val request = BMTCNearbyRequest(
                latitude = latitude,
                longitude = longitude,
                stationId = 0,
                stationFlag = 1 // BMTC stations
            )
            
            val response = callBMTCNearbyAPI(request)
            val busStops = parseBMTCResponse(response)
            
            Log.d(TAG, "Found ${busStops.size} nearby bus stops from BMTC API")
            Result.success(busStops)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby bus stops from BMTC API: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private suspend fun callBMTCNearbyAPI(request: BMTCNearbyRequest): BMTCNearbyResponse {
        val jsonBody = gson.toJson(request)
        Log.d(TAG, "BMTC API Request: $jsonBody")
        
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        
        // Generate a simple device ID (you can make this more sophisticated)
        val deviceId = "NextBus_${System.currentTimeMillis() % 1000000}"
        
        val httpRequest = Request.Builder()
            .url(BMTC_NEARBY_API_URL)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "NextBus/1.0")
            .addHeader("deviceId", deviceId)
            .addHeader("deviceType", "android")
            .addHeader("authToken", "N/A")
            .addHeader("lan", "en")
            .build()
        
        Log.d(TAG, "BMTC API Headers: deviceId=$deviceId, deviceType=android, authToken=N/A, lan=en")
        
        val response = httpClient.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error body"
            Log.e(TAG, "BMTC API Error Response: $errorBody")
            throw IOException("BMTC API call failed with code: ${response.code}, body: $errorBody")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
        Log.d(TAG, "BMTC API Response: ${responseBody.take(500)}...")
        
        // Try to parse as direct array first, then as wrapped response
        return try {
            // Check if response starts with array bracket
            if (responseBody.trim().startsWith("[")) {
                val stationDataList = gson.fromJson(responseBody, Array<BMTCStationData>::class.java).toList()
                BMTCNearbyResponse(data = stationDataList)
            } else {
                gson.fromJson(responseBody, BMTCNearbyResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing BMTC response: ${e.message}")
            throw IOException("Failed to parse BMTC API response: ${e.message}")
        }
    }
    
    private fun parseBMTCResponse(response: BMTCNearbyResponse): List<BusStop> {
        val stationsData = response.data
        if (stationsData.isNullOrEmpty()) {
            Log.e(TAG, "BMTC API returned no data")
            return emptyList()
        }
        
        Log.d(TAG, "Parsing ${stationsData.size} bus stops from BMTC API")
        
        return stationsData.map { station ->
            BusStop(
                id = station.geofenceId.toString(),
                name = station.geofenceName,
                vicinity = buildVicinity(station),
                location = LatLng(station.centerLat, station.centerLon),
                reference = null,
                placeId = station.geofenceId.toString(),
                rating = null,
                types = listOf("bus_station", "transit_station"),
                distance = station.distance,
                towards = station.towards
            )
        }
    }
    
    private fun buildVicinity(station: BMTCStationData): String {
        return buildString {
            append("Bus Stop")
            station.towards?.let { towards ->
                if (towards.isNotBlank()) {
                    append(" (Towards $towards)")
                }
            }
            // Note: Not including BMTC distance here as it's often incorrect (0.0)
            // GPS-calculated distance is shown separately in the UI
        }
    }
    
}
