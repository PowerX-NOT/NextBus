package com.android.nextbus.ui.maps

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.nextbus.data.model.BusStop
import com.android.nextbus.data.network.NearbyBusStopService
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
    private val googleRoutesService = com.android.nextbus.data.network.GoogleRoutesService()
    
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
        
        // Always fetch fresh routes for the selected bus stop
        fetchRoutesForSelectedBusStop(busStop)
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
    
    private fun fetchRoutesForSelectedBusStop(busStop: BusStop) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching comprehensive routes for selected bus stop: ${busStop.name}")
                
                // Clear existing routes and set loading state
                val updatedBusStop = busStop.copy(routes = emptyList(), isLoadingRoutes = true)
                _selectedBusStop.value = updatedBusStop
                
                // Also update the bus stop in the main list
                val updatedBusStops = _busStops.value.map { 
                    if (it.id == busStop.id) updatedBusStop else it 
                }
                _busStops.value = updatedBusStops
                
                // Use comprehensive route fetching
                val routesResult = googleRoutesService.getTransitRoutesForStation(busStop.location)
                
                routesResult.fold(
                    onSuccess = { routes ->
                        Log.d(TAG, "Found ${routes.size} comprehensive routes for ${busStop.name}")
                        
                        // Log route details for debugging
                        routes.forEach { route ->
                            Log.d(TAG, "Route: ${route.routeNumber} - ${route.routeName} (${route.agency})")
                        }
                        
                        val busStopWithRoutes = busStop.copy(routes = routes, isLoadingRoutes = false)
                        
                        // Update selected bus stop
                        _selectedBusStop.value = busStopWithRoutes
                        
                        // Update bus stop in the main list
                        val finalUpdatedBusStops = _busStops.value.map { 
                            if (it.id == busStop.id) busStopWithRoutes else it 
                        }
                        _busStops.value = finalUpdatedBusStops
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error fetching comprehensive routes for ${busStop.name}: ${exception.message}")
                        val busStopWithError = busStop.copy(isLoadingRoutes = false)
                        
                        _selectedBusStop.value = busStopWithError
                        
                        val finalUpdatedBusStops = _busStops.value.map { 
                            if (it.id == busStop.id) busStopWithError else it 
                        }
                        _busStops.value = finalUpdatedBusStops
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching comprehensive routes for ${busStop.name}: ${e.message}")
                val busStopWithError = busStop.copy(isLoadingRoutes = false)
                _selectedBusStop.value = busStopWithError
                
                val finalUpdatedBusStops = _busStops.value.map { 
                    if (it.id == busStop.id) busStopWithError else it 
                }
                _busStops.value = finalUpdatedBusStops
            }
        }
    }
    
    // Add method to refresh routes for a bus stop
    fun refreshRoutesForBusStop(busStop: BusStop) {
        fetchRoutesForSelectedBusStop(busStop)
    }

}