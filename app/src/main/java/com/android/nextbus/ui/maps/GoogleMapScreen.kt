package com.android.nextbus.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.platform.LocalDensity
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.android.nextbus.ui.components.SearchCard
import com.android.nextbus.data.model.BusStop
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.android.nextbus.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor

// Utility function to create custom bitmap descriptor from drawable resource
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, width: Int = 160, height: Int = 160): BitmapDescriptor? {
    return try {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable?.let { drawable ->
            drawable.setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    } catch (e: Exception) {
        null
    }
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
fun GoogleMapScreen(
    onBackPressed: (() -> Unit)? = null,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isDarkTheme = isSystemInDarkTheme()
    
    // Collect state from ViewModel
    val busStops by viewModel.busStops.collectAsState()
    val selectedBusStop by viewModel.selectedBusStop.collectAsState()
    val highlightedBusStop by viewModel.highlightedBusStop.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSearchCardExpanded by viewModel.isSearchCardExpanded.collectAsState()
    val isSearchCardMinimized by viewModel.isSearchCardMinimized.collectAsState()
    val userLocationFromViewModel by viewModel.userLocation.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val isRoutesLoading by viewModel.isRoutesLoading.collectAsState()
    val routeSearchResults by viewModel.routeSearchResults.collectAsState()
    val isRouteSearchLoading by viewModel.isRouteSearchLoading.collectAsState()
    val routeSuggestions by viewModel.routeSuggestions.collectAsState()
    val isRouteSuggestionsLoading by viewModel.isRouteSuggestionsLoading.collectAsState()
    val selectedRouteNo by viewModel.selectedRouteNo.collectAsState()
    val routePolylines by viewModel.routePolylines.collectAsState()
    val isUpRouteVisible by viewModel.isUpRouteVisible.collectAsState()
    val isDownRouteVisible by viewModel.isDownRouteVisible.collectAsState()
    
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
    
    // Use ViewModel states instead of local states
    
    // Detect keyboard state
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible = imeInsets.getBottom(LocalDensity.current) > 0
    
    // Animated bottom padding for recenter button - only adjust for manual expansion, not keyboard
    val recenterButtonBottomPadding by animateDpAsState(
        targetValue = if (isSearchCardExpanded && !isKeyboardVisible) {
            (configuration.screenHeightDp.toFloat() * 0.5f + 20f).dp
        } else {
            170.dp
        },
        animationSpec = tween(durationMillis = 300),
        label = "recenter_button_padding"
    )
    
    // Animated content padding for map to keep location centered - only adjust for manual expansion, not keyboard
    val mapBottomPadding by animateDpAsState(
        targetValue = if (isSearchCardExpanded && !isKeyboardVisible) {
            // When manually expanded, add padding equal to 50% of screen height
            (configuration.screenHeightDp.toFloat() * 0.5f).dp
        } else {
            // When collapsed or keyboard visible, use normal padding for search card
            180.dp
        },
        animationSpec = tween(durationMillis = 300),
        label = "map_bottom_padding"
    )

    val density = LocalDensity.current
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    LaunchedEffect(selectedBusStop) {
        val stop = selectedBusStop ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(stop.location)
                    .zoom(16f)
                    .build()
            ),
            1000
        )
    }

    LaunchedEffect(routePolylines) {
        val allPoints = routePolylines.flatMap { it.points }
        if (allPoints.size < 2) return@LaunchedEffect

        val boundsBuilder = LatLngBounds.builder()
        allPoints.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()

        // Keep some padding so the route isn't clipped by UI (search card, status bar)
        val paddingPx = with(density) { 72.dp.toPx() }.toInt()
        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                1000
            )
        } catch (_: Exception) {
            // Ignore bounds animation failures (can happen if map not laid out yet)
        }
    }
    
    // Handle back button behavior
    BackHandler(
        enabled = isSearchCardExpanded || isSearchCardMinimized || busStops.isNotEmpty() || selectedBusStop != null || selectedRouteNo != null
    ) {
        when {
            // If a bus stop is selected, clear selection first
            selectedBusStop != null -> {
                viewModel.clearSelectedBusStop()
                if (selectedRouteNo != null) {
                    viewModel.setSearchCardExpanded(true)
                }
            }
            selectedRouteNo != null -> {
                viewModel.clearSelectedRoute()
                highlightedBusStop?.let {
                    viewModel.selectBusStop(it)
                    viewModel.setSearchCardExpanded(true)
                }
            }
            // If there are bus stops visible, clear them
            busStops.isNotEmpty() -> {
                viewModel.clearBusStops()
                viewModel.setSearchCardExpanded(false)
            }
            // Then handle search card states
            isSearchCardExpanded -> viewModel.setSearchCardExpanded(false)
            isSearchCardMinimized -> viewModel.setSearchCardMinimized(false)
        }
    }
    
    // Get user location when permission is granted
    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            isLocationLoading = true
            val location = getCurrentLocation(context)
            userLocation = location
            isLocationLoading = false
            
            // Update ViewModel with user location
            location?.let {
                viewModel.updateUserLocation(it)
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
    
    // Adjust camera when search card expands/collapses to account for padding changes (only for manual expansion, not keyboard)
    LaunchedEffect(isSearchCardExpanded, isKeyboardVisible) {
        // Only adjust camera for manual expansion, not when keyboard appears
        if (isKeyboardVisible) return@LaunchedEffect
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
                mapStyleOptions = if (isDarkTheme) {
                    com.android.nextbus.ui.theme.MapStyles.darkMapStyle
                } else {
                    null
                }
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
            // Selected route polyline(s)
            routePolylines
                .filter { routeLine ->
                    when (routeLine.direction) {
                        0 -> isUpRouteVisible
                        1 -> isDownRouteVisible
                        else -> true
                    }
                }
                .forEach { routeLine ->
                AnimatedRoutePolyline(
                    points = routeLine.points,
                    color = if (routeLine.direction == 0) Color(0xFF1E88E5) else Color(0xFFE53935),
                    width = 10f
                )
            }

            // Bus stop markers with custom pins
            // If a bus stop is selected, only show that one.
            // If a route is selected (route details), hide nearby markers to avoid clutter.
            // Otherwise show all nearby markers.
            val markersToShow: List<BusStop> = when {
                selectedBusStop != null -> listOf(selectedBusStop!!)
                selectedRouteNo != null -> highlightedBusStop?.let { listOf(it) } ?: emptyList()
                else -> busStops
            }
            
            markersToShow.forEach { busStop ->
                val isSelectedMarker = (selectedBusStop?.id == busStop.id) || (highlightedBusStop?.id == busStop.id)

                val customIcon = if (isSelectedMarker) {
                    // Use yellow pin for selected bus stop
                    bitmapDescriptorFromVector(context, R.drawable.bus_stop_yellow, 160, 160)
                } else {
                    // Use red pin for unselected bus stops
                    bitmapDescriptorFromVector(context, R.drawable.bus_stop_red, 160, 160)
                }
                
                Marker(
                    state = MarkerState(position = busStop.location),
                    title = busStop.name,
                    snippet = busStop.vicinity,
                    icon = customIcon ?: BitmapDescriptorFactory.defaultMarker(
                        if (isSelectedMarker) {
                            BitmapDescriptorFactory.HUE_YELLOW // Fallback to yellow for selected
                        } else {
                            BitmapDescriptorFactory.HUE_RED // Fallback to red for unselected
                        }
                    ),
                    onClick = {
                        viewModel.selectBusStop(busStop)
                        // Expand search card to show bus stop details
                        viewModel.setSearchCardExpanded(true)
                        // Animate camera to selected bus stop
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(busStop.location)
                                        .zoom(16f)
                                        .build()
                                ),
                                1000
                            )
                        }
                        true // Consume the click event
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
        
        // Error display
        error?.let { errorMessage ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = 60.dp), // Account for status bar and back button
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    TextButton(
                        onClick = { viewModel.clearError() }
                    ) {
                        Text(
                            text = "Dismiss",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Bottom search interface
        SearchCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            isExpanded = isSearchCardExpanded,
            isMinimized = isSearchCardMinimized,
            onExpandedChange = { viewModel.setSearchCardExpanded(it) },
            onMinimizedChange = { viewModel.setSearchCardMinimized(it) },
            onSearchClick = { viewModel.setSearchCardExpanded(true) },
            onFavoritesClick = { /* Handle favorites click */ },
            onNearbyClick = {
                // Immediately expand the search card for responsive UI
                viewModel.setSearchCardExpanded(true)
                
                // Then search for nearby bus stops using current location
                userLocation?.let { location ->
                    viewModel.searchNearbyBusStops(location)
                } ?: run {
                    // If no location, try to get it first
                    if (locationPermissionState.status.isGranted) {
                        coroutineScope.launch {
                            val location = getCurrentLocation(context)
                            location?.let {
                                userLocation = it
                                viewModel.updateUserLocation(it)
                                viewModel.searchNearbyBusStops(it)
                            }
                        }
                    } else {
                        locationPermissionState.launchPermissionRequest()
                    }
                }
            },
            onRouteQueryChange = { query ->
                viewModel.searchBusStopsByRoute(query)
            },
            onRouteSelected = { routeNo ->
                viewModel.clearSelectedBusStop(keepHighlighted = true)
                viewModel.selectRoute(routeNo)
                viewModel.setSearchCardExpanded(true)
            },
            onBusStopSelected = { busStop ->
                viewModel.selectBusStop(busStop)
                // Expand search card to show bus stop details
                viewModel.setSearchCardExpanded(true)
            },
            busStops = busStops,
            routeSuggestions = routeSuggestions,
            routeSearchResults = routeSearchResults,
            selectedRouteNo = selectedRouteNo,
            isUpRouteVisible = isUpRouteVisible,
            isDownRouteVisible = isDownRouteVisible,
            onUpRouteVisibleChange = { visible -> viewModel.setUpRouteVisible(visible) },
            onDownRouteVisibleChange = { visible -> viewModel.setDownRouteVisible(visible) },
            selectedBusStop = selectedBusStop,
            isLoadingBusStops = isLoading,
            isRouteSearchLoading = isRouteSearchLoading,
            isRouteSuggestionsLoading = isRouteSuggestionsLoading,
            userLocation = userLocation,
            routes = routes,
            isLoadingRoutes = isRoutesLoading
        )
    }
    
    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}

@Composable
private fun AnimatedRoutePolyline(
    points: List<LatLng>,
    color: Color,
    width: Float,
    durationMs: Int = 3000,
    stepMeters: Float = 15f
) {
    val densified = remember(points) {
        densifyPolyline(points, stepMeters)
    }
    if (densified.size < 2) return

    val transition = rememberInfiniteTransition(label = "route_polyline")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing)
        ),
        label = "route_progress"
    )

    val n = densified.size
    val idx = kotlin.math.max(2, (progress * n).toInt())
    val animatedPoints = densified.subList(0, idx.coerceAtMost(n))

    Polyline(
        points = densified,
        color = color.copy(alpha = 0.35f),
        width = width
    )

    Polyline(
        points = animatedPoints,
        color = color,
        width = width
    )
}

private fun densifyPolyline(points: List<LatLng>, stepMeters: Float): List<LatLng> {
    if (points.size < 2) return points
    if (stepMeters <= 0f) return points

    val out = ArrayList<LatLng>(points.size * 2)
    out.add(points.first())

    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val dist = distanceMeters(a, b)
        if (dist <= stepMeters) {
            out.add(b)
            continue
        }

        val steps = (dist / stepMeters).toInt().coerceAtLeast(1)
        for (s in 1..steps) {
            val t = (s.toDouble() / steps.toDouble()).coerceIn(0.0, 1.0)
            out.add(interpolateLatLng(a, b, t))
        }
    }

    return out
}

private fun distanceMeters(a: LatLng, b: LatLng): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        a.latitude,
        a.longitude,
        b.latitude,
        b.longitude,
        results
    )
    return results[0]
}

private fun interpolateLatLng(a: LatLng, b: LatLng, t: Double): LatLng {
    val lat = a.latitude + ((b.latitude - a.latitude) * t)
    val lng = a.longitude + ((b.longitude - a.longitude) * t)
    return LatLng(lat, lng)
}
