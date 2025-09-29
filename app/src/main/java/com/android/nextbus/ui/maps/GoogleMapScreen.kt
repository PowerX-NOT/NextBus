package com.android.nextbus.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val configuration = LocalConfiguration.current
    
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
    
    // Animated bottom padding for recenter button
    val recenterButtonBottomPadding by animateDpAsState(
        targetValue = if (isSearchCardExpanded) {
            (configuration.screenHeightDp.toFloat() * 0.5f + 20f).dp
        } else {
            170.dp
        },
        animationSpec = tween(durationMillis = 300),
        label = "recenter_button_padding"
    )
    
    // Animated content padding for map to keep location centered
    val mapBottomPadding by animateDpAsState(
        targetValue = if (isSearchCardExpanded) {
            // When expanded, add padding equal to 50% of screen height
            (configuration.screenHeightDp.toFloat() * 0.5f).dp
        } else {
            // When collapsed, use normal padding for search card
            180.dp
        },
        animationSpec = tween(durationMillis = 300),
        label = "map_bottom_padding"
    )
    
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
            
            // Update camera position to user location
            location?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(it)
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
    
    // Adjust camera when search card expands/collapses to account for padding changes
    LaunchedEffect(isSearchCardExpanded) {
        // Small delay to let the padding animation start
        delay(50)
        
        val currentTarget = cameraPositionState.position.target
        val screenHeightDp = configuration.screenHeightDp
        
        // Calculate offset adjustment based on padding change
        val paddingChangeDp = if (isSearchCardExpanded) {
            // Expanding: search card covers more area, so move camera down (south) to keep content visible
            -(screenHeightDp * 0.5f - 180f) // Full padding difference = move south
        } else {
            // Collapsing: search card covers less area, so move camera up (north)
            (screenHeightDp * 0.5f - 180f) // Full padding difference = move north
        }
        
        // Convert dp offset to lat/lng offset with moderate movement factor
        val latOffset = (paddingChangeDp * 0.00002f) // Moderate movement factor
        
        val adjustedTarget = LatLng(
            currentTarget.latitude + latOffset,
            currentTarget.longitude
        )
        
        cameraPositionState.animate(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(adjustedTarget)
                    .zoom(cameraPositionState.position.zoom)
                    .bearing(cameraPositionState.position.bearing)
                    .tilt(cameraPositionState.position.tilt)
                    .build()
            ),
            300 // Match the padding animation duration
        )
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
            contentPadding = PaddingValues(
                bottom = mapBottomPadding // Dynamically adjust for search card
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
        
        // Recenter button (bottom-right) - dynamically positioned based on search card state
        FloatingActionButton(
            onClick = {
                if (locationPermissionState.status.isGranted && userLocation != null) {
                    // Recenter to user location
                    userLocation?.let { location ->
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(location)
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
                .padding(bottom = recenterButtonBottomPadding)
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
