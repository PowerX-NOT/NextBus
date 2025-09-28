package com.android.nextbus.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import androidx.core.content.ContextCompat
import com.android.nextbus.R
import com.android.nextbus.ui.components.SearchCard
import com.android.nextbus.data.model.BusStop
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.toArgb

// Function to create custom bus stop marker icon
fun createBusStopMarkerIcon(context: Context): com.google.android.gms.maps.model.BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, R.drawable.bus_stop_icon)
    return drawable?.let {
        BitmapDescriptorFactory.fromBitmap(
            android.graphics.Bitmap.createScaledBitmap(
                (it as android.graphics.drawable.BitmapDrawable).bitmap,
                160, // width in pixels (increased from 100)
                160, // height in pixels (increased from 100)
                true
            )
        )
    } ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
}

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): LatLng? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                continuation.resume(LatLng(location.latitude, location.longitude))
            } else {
                continuation.resume(null)
            }
        }.addOnFailureListener {
            continuation.resume(null)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GoogleMapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // ViewModel for managing bus stops
    val mapViewModel: MapViewModel = viewModel { MapViewModel(context) }
    
    // Collect states from ViewModel
    val busStops by mapViewModel.busStops.collectAsState()
    val isLoadingBusStops by mapViewModel.isLoading.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()
    val showBusStops by mapViewModel.showBusStops.collectAsState()
    val error by mapViewModel.error.collectAsState()
    
    // Location permission
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // State for user location
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLocationLoading by remember { mutableStateOf(true) }
    
    // State for selected bus stop marker
    var selectedBusStop by remember { mutableStateOf<BusStop?>(null) }
    
    // State for minimizing SearchCard when showing bus stop details
    var isSearchCardMinimized by remember { mutableStateOf(false) }
    var isSearchCardExpanded by remember { mutableStateOf(false) }
    
    // Calculate dynamic recenter button position based on SearchCard state
    val recenterButtonBottomPadding by animateFloatAsState(
        targetValue = when {
            isSearchCardMinimized -> 60f // 40dp SearchCard + 20dp margin
            selectedBusStop != null -> screenHeight.value * 0.4f + 20f // 40% SearchCard + margin
            isSearchCardExpanded -> screenHeight.value * 0.95f + 20f // 95% SearchCard + margin
            else -> 180f // Default collapsed state + margin
        },
        animationSpec = tween(durationMillis = 300),
        label = "recenter_button_animation"
    )
    
    
    // Default fallback to Bangalore if location not available
    val defaultLocation = LatLng(12.9716, 77.5946)
    val currentLocation = userLocation ?: defaultLocation
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 16f)
    }
    
    // Get user location when permission is granted
    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            isLocationLoading = true
            val location = getCurrentLocation(context)
            userLocation = location
            isLocationLoading = false
            
            // Update camera position to user location
            location?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(it) // Use actual user location without offset
                            .zoom(16f) // Match recenter button zoom level
                            .bearing(0f) // Reset compass to north
                            .tilt(0f)    // Reset tilt to flat
                            .build()
                    ),
                    1000
                )
            }
        } else {
            isLocationLoading = false
        }
    }
    
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationPermissionState.status.isGranted,
                mapStyleOptions = com.android.nextbus.ui.theme.MapStyles.darkMapStyle
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false
            ),
            onMapClick = {
                // Minimize SearchCard when map is clicked and bus stop details are showing
                if (selectedBusStop != null && !isSearchCardMinimized) {
                    isSearchCardMinimized = true
                }
            }
        ) {
            // Display only the selected bus stop marker
            selectedBusStop?.let { busStop ->
                Marker(
                    state = MarkerState(position = busStop.location),
                    title = busStop.name,
                    snippet = busStop.vicinity,
                    icon = createBusStopMarkerIcon(context),
                    onClick = {
                        // Handle marker click - could show bus stop details
                        true
                    }
                )
            }
        }
        
        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button
            FloatingActionButton(
                onClick = { /* Handle back navigation */ },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
        
        // Recenter button (bottom-right)
        FloatingActionButton(
            onClick = {
                if (locationPermissionState.status.isGranted && userLocation != null) {
                    // Recenter to user location
                    userLocation?.let { location ->
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(location) // Use actual user location without offset
                                        .zoom(16f) // Slightly closer zoom for better visibility
                                        .bearing(0f) // Reset compass to north
                                        .tilt(0f)    // Reset tilt to flat
                                        .build()
                                ),
                                1000
                            )
                        }
                    }
                } else if (!locationPermissionState.status.isGranted) {
                    // Request permission if not granted
                    locationPermissionState.launchPermissionRequest()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = recenterButtonBottomPadding.dp)
                .size(56.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Recenter to my location"
            )
        }
        
        // Bottom search interface
        SearchCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            onSearchClick = { 
                isSearchCardExpanded = true
            },
            onFavoritesClick = { /* Handle favorites click */ },
            onNearbyClick = {
                if (locationPermissionState.status.isGranted && userLocation != null) {
                    userLocation?.let { location ->
                        mapViewModel.searchNearbyBusStops(location)
                    }
                } else {
                    locationPermissionState.launchPermissionRequest()
                }
            },
            busStops = busStops,
            isLoadingBusStops = isLoadingBusStops,
            userLocation = userLocation,
            selectedBusStop = selectedBusStop,
            isMinimized = isSearchCardMinimized,
            onMinimizeToggle = {
                isSearchCardMinimized = !isSearchCardMinimized
            },
            onExpandedChange = { expanded ->
                isSearchCardExpanded = expanded
            },
            onBusStopSelected = { busStop ->
                selectedBusStop = busStop
                isSearchCardMinimized = false // Reset minimize state when new bus stop is selected
                // Center map on selected bus stop
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(busStop.location)
                                .zoom(16f)
                                .bearing(0f)
                                .tilt(0f)
                                .build()
                        ),
                        1000
                    )
                }
            }
        )
        
        
        // Show error message if any
        error?.let { errorMessage ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .padding(top = 80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { mapViewModel.clearError() }
                    ) {
                        Text(
                            text = "Dismiss",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
    
    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}
