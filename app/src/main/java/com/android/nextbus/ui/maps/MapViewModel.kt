package com.android.nextbus.ui.maps

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.network.NearbyBusStopService
import com.android.nextbus.data.network.BusStopRouteService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MapViewModel"
    }
    
    private val nearbyBusStopService = NearbyBusStopService()
    private val busStopRouteService = BusStopRouteService()
    
    // State for bus stops
    private val _busStops = MutableStateFlow<List<BusStop>>(emptyList())
    val busStops: StateFlow<List<BusStop>> = _busStops.asStateFlow()
    
    // State for selected bus stop
    private val _selectedBusStop = MutableStateFlow<BusStop?>(null)
    val selectedBusStop: StateFlow<BusStop?> = _selectedBusStop.asStateFlow()
    
    // State for loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // State for error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // State for search card
    private val _isSearchCardExpanded = MutableStateFlow(false)
    val isSearchCardExpanded: StateFlow<Boolean> = _isSearchCardExpanded.asStateFlow()
    
    private val _isSearchCardMinimized = MutableStateFlow(false)
    val isSearchCardMinimized: StateFlow<Boolean> = _isSearchCardMinimized.asStateFlow()
    
    // User location
    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()
    
    // Routes for selected bus stop
    private val _routes = MutableStateFlow<List<String>>(emptyList())
    val routes: StateFlow<List<String>> = _routes.asStateFlow()

    private val _isRoutesLoading = MutableStateFlow(false)
    val isRoutesLoading: StateFlow<Boolean> = _isRoutesLoading.asStateFlow()
    
    // Last search location to prevent duplicate searches
    private var lastSearchLocation: LatLng? = null
    
    fun searchNearbyBusStops(location: LatLng) {
        // Prevent duplicate searches for the same location
        if (lastSearchLocation != null && 
            calculateDistance(lastSearchLocation!!, location) < 100) { // 100 meters threshold
            Log.d(TAG, "Skipping duplicate search for nearby location")
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                lastSearchLocation = location
                
                Log.d(TAG, "Searching for nearby bus stops at: ${location.latitude}, ${location.longitude}")
                
                val result = nearbyBusStopService.getNearbyBusStops(
                    location.latitude,
                    location.longitude
                )
                
                result.fold(
                    onSuccess = { busStops ->
                        _busStops.value = busStops
                        Log.d(TAG, "Successfully loaded ${busStops.size} bus stops")
                    },
                    onFailure = { exception ->
                        _error.value = "Failed to load nearby bus stops: ${exception.message}"
                        Log.e(TAG, "Error loading bus stops", exception)
                    }
                )
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "Unexpected error in searchNearbyBusStops", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectBusStop(busStop: BusStop) {
        _selectedBusStop.value = busStop
        Log.d(TAG, "Selected bus stop: ${busStop.name}")

        // Fetch routes for this bus stop if we have a placeId
        val placeId = busStop.placeId
        if (placeId != null) {
            viewModelScope.launch {
                try {
                    _isRoutesLoading.value = true
                    _routes.value = emptyList()
                    val result = busStopRouteService.getRoutesForPlace(placeId)
                    result.fold(
                        onSuccess = { routes ->
                            _routes.value = routes
                            Log.d(TAG, "Loaded ${routes.size} routes for bus stop: ${busStop.name}")
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Error loading routes for bus stop: ${exception.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error while loading routes: ${e.message}", e)
                } finally {
                    _isRoutesLoading.value = false
                }
            }
        } else {
            _routes.value = emptyList()
            _isRoutesLoading.value = false
        }
    }
    
    fun clearSelectedBusStop() {
        _selectedBusStop.value = null
        _routes.value = emptyList()
        _isRoutesLoading.value = false
    }
    
    fun setSearchCardExpanded(expanded: Boolean) {
        _isSearchCardExpanded.value = expanded
        if (expanded) {
            _isSearchCardMinimized.value = false
        }
    }
    
    fun setSearchCardMinimized(minimized: Boolean) {
        _isSearchCardMinimized.value = minimized
        if (minimized) {
            _isSearchCardExpanded.value = false
        }
    }
    
    fun updateUserLocation(location: LatLng) {
        _userLocation.value = location
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearBusStops() {
        _busStops.value = emptyList()
        _selectedBusStop.value = null
        lastSearchLocation = null
    }
    
    // Calculate distance between two LatLng points in meters
    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }
    
    // Sample bus stops for testing (can be removed once API is working)
    fun loadSampleBusStops() {
        val sampleBusStops = listOf(
            BusStop(
                id = "sample_1",
                name = "Majestic Bus Station",
                vicinity = "Majestic, Bangalore",
                location = LatLng(12.9767, 77.5713)
            ),
            BusStop(
                id = "sample_2", 
                name = "Vidhana Soudha",
                vicinity = "Vidhana Soudha, Bangalore",
                location = LatLng(12.9794, 77.5912)
            ),
            BusStop(
                id = "sample_3",
                name = "Cubbon Park",
                vicinity = "Cubbon Park, Bangalore", 
                location = LatLng(12.9698, 77.5906)
            )
        )
        _busStops.value = sampleBusStops
    }
}