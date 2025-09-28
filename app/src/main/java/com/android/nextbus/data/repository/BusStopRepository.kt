package com.android.nextbus.data.repository

import android.content.Context
import android.util.Log
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.network.PlacesApiService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.Properties

class BusStopRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "BusStopRepository"
    }
    
    private val placesApiService = PlacesApiService()
    
    // State for bus stops
    private val _busStops = MutableStateFlow<List<BusStop>>(emptyList())
    val busStops: StateFlow<List<BusStop>> = _busStops.asStateFlow()
    
    // State for loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // State for error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Cache for API key
    private var cachedApiKey: String? = null
    
    /**
     * Get Google API key from .env file or assets
     */
    private fun getApiKey(): String {
        if (cachedApiKey != null) {
            return cachedApiKey!!
        }
        
        try {
            // First try to read from .env file in assets
            val inputStream = context.assets.open(".env")
            val properties = Properties()
            properties.load(inputStream)
            val apiKey = properties.getProperty("GOOGLE_API_KEY")
            if (apiKey != null) {
                cachedApiKey = apiKey
                return apiKey
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read .env from assets, trying hardcoded key")
        }
        
        // Fallback to the key from bushop app (same key you provided)
        val fallbackKey = "AIzaSyBDoWL3PAp-c5V_lijSufAC3wyJye3noM0"
        cachedApiKey = fallbackKey
        return fallbackKey
    }
    
    /**
     * Search for nearby bus stops at the given location
     * This mimics the exact functionality from bushop app
     */
    suspend fun searchNearbyBusStops(location: LatLng): Result<List<BusStop>> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            Log.d(TAG, "Searching for nearby bus stops at: ${location.latitude}, ${location.longitude}")
            
            val apiKey = getApiKey()
            
            // Use the same search approach as bushop app - try transit_station first
            val result = placesApiService.getNearbyBusStops(
                location = location,
                apiKey = apiKey,
                searchType = "transit_station"
            )
            
            result.fold(
                onSuccess = { busStops ->
                    Log.d(TAG, "Successfully found ${busStops.size} bus stops")
                    _busStops.value = busStops
                    _isLoading.value = false
                    Result.success(busStops)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch bus stops", exception)
                    _error.value = exception.message ?: "Unknown error occurred"
                    _isLoading.value = false
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchNearbyBusStops", e)
            _error.value = e.message ?: "Unknown error occurred"
            _isLoading.value = false
            Result.failure(e)
        }
    }
    
    /**
     * Search for multiple types of transit stops (bus, subway, transit)
     * This provides broader search results similar to bushop app's approach
     */
    suspend fun searchAllTransitTypes(location: LatLng): Result<List<BusStop>> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            Log.d(TAG, "Searching for all transit types at: ${location.latitude}, ${location.longitude}")
            
            val apiKey = getApiKey()
            
            val result = placesApiService.searchMultipleTypes(
                location = location,
                apiKey = apiKey
            )
            
            result.fold(
                onSuccess = { busStops ->
                    Log.d(TAG, "Successfully found ${busStops.size} transit stops")
                    _busStops.value = busStops
                    _isLoading.value = false
                    Result.success(busStops)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch transit stops", exception)
                    _error.value = exception.message ?: "Unknown error occurred"
                    _isLoading.value = false
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchAllTransitTypes", e)
            _error.value = e.message ?: "Unknown error occurred"
            _isLoading.value = false
            Result.failure(e)
        }
    }
    
    /**
     * Clear current bus stops and error state
     */
    fun clearBusStops() {
        _busStops.value = emptyList()
        _error.value = null
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Get cached bus stops without making new API call
     */
    fun getCachedBusStops(): List<BusStop> {
        return _busStops.value
    }
    
    /**
     * Check if there are cached bus stops
     */
    fun hasCachedBusStops(): Boolean {
        return _busStops.value.isNotEmpty()
    }
}
