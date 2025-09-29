package com.android.nextbus.ui.maps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.repository.BusStopRepository
import com.android.nextbus.data.repository.DirectionsRepository
import com.android.nextbus.data.network.DirectionsResponse
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Job

class MapViewModel(context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "MapViewModel"
    }
    
    private val repository = BusStopRepository(context)
    private val directionsRepository = DirectionsRepository(context)
    
    // Job for managing coroutines
    private var searchJob: Job? = null
    private var directionsJob: Job? = null
    
    // Expose repository states
    val busStops: StateFlow<List<BusStop>> = repository.busStops
    val isLoading: StateFlow<Boolean> = repository.isLoading
    
    // UI state for search button
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    // UI state for showing bus stops
    private val _showBusStops = MutableStateFlow(false)
    val showBusStops: StateFlow<Boolean> = _showBusStops.asStateFlow()
    
    // Directions state
    private val _directionsResponse = MutableStateFlow<DirectionsResponse?>(null)
    val directionsResponse: StateFlow<DirectionsResponse?> = _directionsResponse.asStateFlow()
    
    private val _isLoadingDirections = MutableStateFlow(false)
    val isLoadingDirections: StateFlow<Boolean> = _isLoadingDirections.asStateFlow()
    
    // Last search location to avoid duplicate searches
    private var lastSearchLocation: LatLng? = null
    
    /**
     * Search for nearby bus stops at the given location
     */
    fun searchNearbyBusStops(location: LatLng) {
        // Check if we already searched at this location recently
        if (isSameLocation(location, lastSearchLocation)) {
            Log.d(TAG, "Already searched at this location, showing cached results")
            _showBusStops.value = true
            return
        }
        
        // Cancel previous search job
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
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
        // Cancel previous search job
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
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
     * Get walking directions from origin to destination
     */
    fun getWalkingDirections(origin: LatLng, destination: LatLng) {
        // Cancel previous directions job
        directionsJob?.cancel()
        
        directionsJob = viewModelScope.launch {
            _isLoadingDirections.value = true
            try {
                Log.d(TAG, "Getting walking directions from $origin to $destination")
                
                val result = directionsRepository.getWalkingDirections(origin, destination)
                
                result.onSuccess { directionsResponse ->
                    Log.d(TAG, "Successfully got directions with ${directionsResponse.routes.size} routes")
                    _directionsResponse.value = directionsResponse
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to get directions", exception)
                    _directionsResponse.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting directions", e)
                _directionsResponse.value = null
            } finally {
                _isLoadingDirections.value = false
            }
        }
    }
    
    /**
     * Clear current directions
     */
    fun clearDirections() {
        _directionsResponse.value = null
        directionsJob?.cancel()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        searchJob?.cancel()
        directionsJob?.cancel()
        repository.clearBusStops()
        _directionsResponse.value = null
        lastSearchLocation = null
        Log.d(TAG, "ViewModel cleaned up")
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
    
    /**
     * Check if two locations are approximately the same (within ~100m)
     */
    private fun isSameLocation(loc1: LatLng, loc2: LatLng?): Boolean {
        if (loc2 == null) return false
        
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            loc1.latitude, loc1.longitude,
            loc2.latitude, loc2.longitude,
            distance
        )
        
        return distance[0] < 100f // Within 100 meters
    }
}
