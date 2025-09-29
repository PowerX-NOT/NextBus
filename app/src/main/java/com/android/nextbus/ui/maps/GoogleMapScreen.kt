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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.google.maps.android.compose.*
import com.android.nextbus.ui.components.SearchCard

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
fun GoogleMapScreen(
    onBackPressed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Location permission
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // State for user location
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLocationLoading by remember { mutableStateOf(true) }
    
    // Default fallback to Bangalore if location not available
    val defaultLocation = LatLng(12.9716, 77.5946)
    val currentLocation = userLocation ?: defaultLocation
    
    // Search card states
    var isSearchCardExpanded by rememberSaveable { mutableStateOf(false) }
    var isSearchCardMinimized by rememberSaveable { mutableStateOf(false) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }
    
    // Handle back button behavior
    BackHandler(
        enabled = isSearchCardExpanded || isSearchCardMinimized
    ) {
        when {
            isSearchCardExpanded -> isSearchCardExpanded = false
            isSearchCardMinimized -> isSearchCardMinimized = false
        }
    }
    
    // Get user location when permission is granted
    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            isLocationLoading = true
            val location = getCurrentLocation(context)
            userLocation = location
            isLocationLoading = false
            
            // Update camera position to user location with offset for search card
            location?.let {
                // Calculate offset to center location in visible area (same as recenter button)
                val offsetLat = it.latitude - 0.015 // Move up to account for large bottom padding
                val offsetLng = it.longitude + 0.008 // Move left to account for right padding
                val adjustedLocation = LatLng(offsetLat, offsetLng)
                
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(adjustedLocation)
                            .zoom(15f)
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
                compassEnabled = true
            ),
            contentPadding = PaddingValues(
                top = 600.dp, // Position compass lower, above recenter button
                start = 337.dp // Keep compass on the right side
            )
        ) {
            // Map markers will be added here when real data is integrated
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
                onClick = { 
                    onBackPressed?.invoke()
                },
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
                    // Recenter to user location, accounting for map padding
                    userLocation?.let { location ->
                        coroutineScope.launch {
                            // Calculate offset to center location in visible area
                            val offsetLat = location.latitude - 0.015 // Move up to account for large bottom padding
                            val offsetLng = location.longitude + 0.008 // Move left to account for right padding
                            val adjustedLocation = LatLng(offsetLat, offsetLng)
                            
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(adjustedLocation)
                                        .zoom(15f)
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
                .padding(bottom = 170.dp) // Increased bottom padding to move button up
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
            isExpanded = isSearchCardExpanded,
            isMinimized = isSearchCardMinimized,
            onExpandedChange = { isSearchCardExpanded = it },
            onMinimizedChange = { isSearchCardMinimized = it },
            onSearchClick = { isSearchCardExpanded = true },
            onFavoritesClick = { /* Handle favorites click */ }
        )
    }
    
    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}
