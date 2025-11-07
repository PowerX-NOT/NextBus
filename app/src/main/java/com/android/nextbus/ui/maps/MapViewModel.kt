package com.android.nextbus.ui.maps

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.model.DirectionsResponse
import com.android.nextbus.data.network.NearbyBusStopService
import com.android.nextbus.data.network.DirectionsService
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
    private val directionsService = DirectionsService()
    
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
    
    // Selected place (from search)
    private val _selectedPlace = MutableStateFlow<LatLng?>(null)
    val selectedPlace: StateFlow<LatLng?> = _selectedPlace.asStateFlow()
    
    private val _selectedPlaceName = MutableStateFlow<String?>(null)
    val selectedPlaceName: StateFlow<String?> = _selectedPlaceName.asStateFlow()
    
    // Directions
    private val _directionsResponse = MutableStateFlow<DirectionsResponse?>(null)
    val directionsResponse: StateFlow<DirectionsResponse?> = _directionsResponse.asStateFlow()
    
    private val _isLoadingDirections = MutableStateFlow(false)
    val isLoadingDirections: StateFlow<Boolean> = _isLoadingDirections.asStateFlow()
    
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
    }
    
    fun clearSelectedBusStop() {
        _selectedBusStop.value = null
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
    
    fun selectPlace(location: LatLng, placeName: String) {
        _selectedPlace.value = location
        _selectedPlaceName.value = placeName
        Log.d(TAG, "Selected place: $placeName at $location")
        
        // Clear bus stops and selected bus stop when selecting a place
        _busStops.value = emptyList()
        _selectedBusStop.value = null
        
        // Fetch directions if user location is available
        _userLocation.value?.let { origin ->
            fetchDirections(origin, location)
        }
    }
    
    fun clearSelectedPlace() {
        _selectedPlace.value = null
        _selectedPlaceName.value = null
        _directionsResponse.value = null
    }
    
    fun fetchDirections(origin: LatLng, destination: LatLng) {
        viewModelScope.launch {
            try {
                _isLoadingDirections.value = true
                _error.value = null
                
                Log.d(TAG, "Fetching directions from $origin to $destination")
                
                val result = directionsService.getTransitDirections(origin, destination)
                
                result.fold(
                    onSuccess = { response ->
                        _directionsResponse.value = response
                        Log.d(TAG, "Successfully loaded directions with ${response.routes.size} routes")
                        response.routes.firstOrNull()?.let { route ->
                            Log.d(TAG, "First route has polyline: ${route.polyline != null}")
                            route.polyline?.let { poly ->
                                Log.d(TAG, "Polyline data: ${poly.encodedPolyline.take(50)}...")
                            }
                        }
                    },
                    onFailure = { exception ->
                        _error.value = "Failed to load directions: ${exception.message}"
                        Log.e(TAG, "Error loading directions", exception)
                    }
                )
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "Unexpected error in fetchDirections", e)
            } finally {
                _isLoadingDirections.value = false
            }
        }
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