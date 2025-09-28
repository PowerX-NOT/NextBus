package com.android.nextbus.ui.maps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.repository.BusStopRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log

class MapViewModel(context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "MapViewModel"
    }
    
    private val repository = BusStopRepository(context)
    
    // Expose repository states
    val busStops: StateFlow<List<BusStop>> = repository.busStops
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val error: StateFlow<String?> = repository.error
    
    // UI state for search button
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    // UI state for showing/hiding markers
    private val _showBusStops = MutableStateFlow(false)
    val showBusStops: StateFlow<Boolean> = _showBusStops.asStateFlow()
    
    // Last search location to avoid duplicate searches
    private var lastSearchLocation: LatLng? = null
    
    /**
     * Search for nearby bus stops at the given location
     * This mimics the exact button click functionality from bushop app
     */
    fun searchNearbyBusStops(location: LatLng) {
        // Check if we already searched at this location recently
        if (isSameLocation(location, lastSearchLocation)) {
            Log.d(TAG, "Already searched at this location, showing cached results")
            _showBusStops.value = true
            return
        }
        
        viewModelScope.launch {
            try {
                _isSearching.value = true
                Log.d(TAG, "Starting search for nearby bus stops at: ${location.latitude}, ${location.longitude}")
                
                // Clear previous results
                repository.clearBusStops()
                _showBusStops.value = false
                
                // Search for nearby bus stops (same as bushop app approach)
                val result = repository.searchNearbyBusStops(location)
                
                result.fold(
                    onSuccess = { busStops ->
                        Log.d(TAG, "Search completed successfully. Found ${busStops.size} bus stops")
                        lastSearchLocation = location
                        _showBusStops.value = true
                        
                        if (busStops.isEmpty()) {
                            Log.w(TAG, "No bus stops found at this location")
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Search failed", exception)
                        _showBusStops.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during search", e)
                _showBusStops.value = false
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Search for all types of transit stops (broader search)
     */
    fun searchAllTransitTypes(location: LatLng) {
        viewModelScope.launch {
            try {
                _isSearching.value = true
                Log.d(TAG, "Starting comprehensive transit search at: ${location.latitude}, ${location.longitude}")
                
                // Clear previous results
                repository.clearBusStops()
                _showBusStops.value = false
                
                // Search for all transit types
                val result = repository.searchAllTransitTypes(location)
                
                result.fold(
                    onSuccess = { busStops ->
                        Log.d(TAG, "Comprehensive search completed. Found ${busStops.size} transit stops")
                        lastSearchLocation = location
                        _showBusStops.value = true
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Comprehensive search failed", exception)
                        _showBusStops.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during comprehensive search", e)
                _showBusStops.value = false
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Toggle visibility of bus stop markers
     */
    fun toggleBusStopVisibility() {
        _showBusStops.value = !_showBusStops.value
        Log.d(TAG, "Bus stop visibility toggled to: ${_showBusStops.value}")
    }
    
    /**
     * Clear all bus stops and hide markers
     */
    fun clearBusStops() {
        repository.clearBusStops()
        _showBusStops.value = false
        lastSearchLocation = null
        Log.d(TAG, "Cleared all bus stops")
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        repository.clearError()
    }
    
    /**
     * Check if two locations are approximately the same (within ~100m)
     */
    private fun isSameLocation(location1: LatLng, location2: LatLng?): Boolean {
        if (location2 == null) return false
        
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            location1.latitude, location1.longitude,
            location2.latitude, location2.longitude,
            distance
        )
        
        return distance[0] < 100 // Within 100 meters
    }
    
    /**
     * Get current bus stops count
     */
    fun getBusStopsCount(): Int {
        return busStops.value.size
    }
    
    /**
     * Check if there are any bus stops to show
     */
    fun hasBusStops(): Boolean {
        return busStops.value.isNotEmpty()
    }
    
    /**
     * Get a specific bus stop by ID
     */
    fun getBusStopById(id: String): BusStop? {
        return busStops.value.find { it.id == id }
    }
    
    /**
     * Get bus stops within a certain distance from a location
     */
    fun getBusStopsNear(location: LatLng, radiusMeters: Float = 500f): List<BusStop> {
        return busStops.value.filter { busStop ->
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(
                location.latitude, location.longitude,
                busStop.location.latitude, busStop.location.longitude,
                distance
            )
            distance[0] <= radiusMeters
        }
    }
}
