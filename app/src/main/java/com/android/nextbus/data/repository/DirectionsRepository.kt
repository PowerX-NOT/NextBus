package com.android.nextbus.data.repository

import android.content.Context
import android.util.Log
import com.android.nextbus.data.network.DirectionsApiService
import com.android.nextbus.data.network.DirectionsResponse
import com.google.android.gms.maps.model.LatLng
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Properties

class DirectionsRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "DirectionsRepository"
    }
    
    private val apiService: DirectionsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/maps/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsApiService::class.java)
    }
    
    private fun getApiKey(): String {
        try {
            // First try to read from .env file in assets
            val inputStream = context.assets.open(".env")
            val properties = Properties()
            properties.load(inputStream)
            val apiKey = properties.getProperty("GOOGLE_API_KEY")
            if (apiKey != null) {
                Log.d(TAG, "API key loaded from .env file")
                return apiKey
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read .env from assets, using fallback key", e)
        }
        
        // Fallback to the same key used by BusStopRepository
        val fallbackKey = "AIzaSyBDoWL3PAp-c5V_lijSufAC3wyJye3noM0"
        Log.d(TAG, "Using fallback API key")
        return fallbackKey
    }
    
    suspend fun getWalkingDirections(
        origin: LatLng,
        destination: LatLng
    ): Result<DirectionsResponse> {
        return try {
            val originString = "${origin.latitude},${origin.longitude}"
            val destinationString = "${destination.latitude},${destination.longitude}"
            val apiKey = getApiKey()
            
            Log.d(TAG, "Getting walking directions from $originString to $destinationString")
            Log.d(TAG, "API Key available: ${apiKey.isNotEmpty()}")
            
            if (apiKey.isEmpty()) {
                Log.e(TAG, "API key is empty!")
                return Result.failure(Exception("API key not found"))
            }
            
            val response = apiService.getDirections(
                origin = originString,
                destination = destinationString,
                mode = "walking",
                apiKey = apiKey
            )
            
            Log.d(TAG, "API Response code: ${response.code()}")
            Log.d(TAG, "API Response successful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val directionsResponse = response.body()!!
                Log.d(TAG, "Directions response status: ${directionsResponse.status}")
                Log.d(TAG, "Number of routes: ${directionsResponse.routes.size}")
                
                if (directionsResponse.status == "OK") {
                    Result.success(directionsResponse)
                } else {
                    Log.e(TAG, "Directions API error: ${directionsResponse.status}")
                    Result.failure(Exception("Directions API error: ${directionsResponse.status}"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to get directions: ${response.code()}, Error: $errorBody")
                Result.failure(Exception("Failed to get directions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting directions", e)
            Result.failure(e)
        }
    }
}
