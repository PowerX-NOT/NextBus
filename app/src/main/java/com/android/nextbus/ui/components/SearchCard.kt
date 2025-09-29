package com.android.nextbus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.BusStop
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import android.location.Location
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

data class PlaceSearchResult(
    val name: String,
    val address: String,
    val placeId: String? = null
)

@Composable
fun SearchCard(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onPlaceSelected: (PlaceSearchResult) -> Unit = {},
    busStops: List<BusStop> = emptyList(),
    isLoadingBusStops: Boolean = false,
    onBusStopSelected: (BusStop?) -> Unit = {},
    userLocation: LatLng? = null,
    selectedBusStop: BusStop? = null,
    isMinimized: Boolean = false,
    onMinimizeToggle: () -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
    onGetDirections: (LatLng, LatLng) -> Unit = { _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showingBusStops by remember { mutableStateOf(false) }
    var showingBusStopDetails by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val context = LocalContext.current
    
    // Show bus stop details when a bus stop is selected
    LaunchedEffect(selectedBusStop) {
        if (selectedBusStop != null) {
            showingBusStopDetails = true
            isExpanded = true
        }
    }
    
    // Notify parent about expanded state changes
    LaunchedEffect(isExpanded) {
        onExpandedChange(isExpanded)
    }
    
    // Animation for expansion - different heights based on state
    val animatedHeight by animateFloatAsState(
        targetValue = when {
            isMinimized -> 40f // Only handle bar visible (40dp total height)
            showingBusStopDetails -> screenHeight.value * 0.4f // 40% for bus stop details
            isExpanded -> screenHeight.value * 0.95f // 95% for full search/list
            else -> 160f // Collapsed state
        },
        animationSpec = tween(durationMillis = 300),
        label = "height_animation"
    )
    
    Card(
        modifier = modifier
            .height(animatedHeight.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Handle drag gestures based on current state
                        if (showingBusStopDetails) {
                            // When showing bus stop details, dragging should toggle minimize state
                            if (dragOffset < -100f) { // Dragged up significantly
                                if (isMinimized) {
                                    // Expand from minimized to details view
                                    onMinimizeToggle()
                                    isExpanded = true
                                }
                            } else if (dragOffset > 100f) { // Dragged down significantly
                                if (!isMinimized) {
                                    // Minimize to handle bar only
                                    onMinimizeToggle()
                                }
                            }
                        } else {
                            // Normal expand/collapse behavior for search
                            if (dragOffset < -100f) { // Dragged up significantly
                                isExpanded = true
                            } else if (dragOffset > 100f) { // Dragged down significantly
                                isExpanded = false
                            }
                        }
                        dragOffset = 0f
                    }
                ) { _, dragAmount ->
                    dragOffset += dragAmount.y
                }
            },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Navigation handle
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(3.dp)
                    )
                    .align(Alignment.CenterHorizontally)
                    .clickable { 
                        if (showingBusStopDetails) {
                            // Toggle between minimized and 40% details view
                            val wasMinimized = isMinimized
                            onMinimizeToggle()
                            // If we were minimized and now expanding, ensure content is visible
                            if (wasMinimized) {
                                isExpanded = true
                            }
                        } else {
                            // Normal expand/collapse behavior
                            isExpanded = !isExpanded
                        }
                    }
            )
            
            // Only show content when not minimized
            if (!isMinimized) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Search bar - hide when showing bus stop details
                if (!isExpanded && !showingBusStopDetails) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { 
                            isExpanded = true
                            onSearchClick() 
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Search for routes, stops, or places",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (!showingBusStopDetails) {
                // Active search field when expanded
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) {
                            isSearching = true
                            println("Starting search for: $query")
                            searchPlaces(context, query) { results ->
                                println("Search completed. Results count: ${results.size}")
                                searchResults = results
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                            isSearching = false
                        }
                    },
                    placeholder = { Text("Search for places, routes, or stops...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    isSearching = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            // Only show spacer and quick actions when not showing bus stop details
            if (!showingBusStopDetails) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quick actions - hide when showing bus stop details
                Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Favorites button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFavoritesClick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorites",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Favorites",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Recent searches
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { /* Handle recent */ }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Nearby stops
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { 
                            onNearbyClick()
                            showingBusStops = true
                            isExpanded = true
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            }
            
            // Expanded content - only visible when expanded
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show bus stop details, bus stops results, or search results or suggestions
                if (showingBusStopDetails && selectedBusStop != null) {
                    BusStopDetailsView(
                        busStop = selectedBusStop,
                        userLocation = userLocation,
                        onGetDirections = onGetDirections,
                        onBackClick = {
                            showingBusStopDetails = false
                            showingBusStops = true
                            // Clear selected bus stop when going back
                            onBusStopSelected(null)
                        }
                    )
                } else if (showingBusStops) {
                    if (isLoadingBusStops) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Searching nearby bus stops...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else if (busStops.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby Bus Stops (${busStops.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = {
                                    showingBusStops = false
                                    searchQuery = ""
                                }
                            ) {
                                Text("Back to Search")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(busStops) { busStop ->
                                BusStopResultItem(
                                    busStop = busStop,
                                    userLocation = userLocation,
                                    onClick = {
                                        onBusStopSelected(busStop)
                                        showingBusStopDetails = true
                                        showingBusStops = false
                                    }
                                )
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "No nearby bus stops found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        showingBusStops = false
                                        searchQuery = ""
                                        // Clear selected bus stop when going back
                                        onBusStopSelected(null)
                                    }
                                ) {
                                    Text("Back to Search")
                                }
                            }
                            Text(
                                text = "Try moving to a different location or check your internet connection.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty()) {
                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Search Results",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(searchResults) { result ->
                                SearchResultItem(
                                    result = result,
                                    onClick = {
                                        onPlaceSelected(result)
                                        isExpanded = false
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        // Clear selected bus stop when selecting a place
                                        onBusStopSelected(null)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No results found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Search suggestions when no query
                    Text(
                        text = "Search Options",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Search categories
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchCategoryItem(
                            title = "Bus Routes",
                            description = "Find routes by number or destination",
                            onClick = {
                                searchQuery = "Route "
                            }
                        )
                        
                        SearchCategoryItem(
                            title = "Bus Stops",
                            description = "Search for nearby bus stops",
                            onClick = {
                                searchQuery = "Bus stop "
                            }
                        )
                        
                        SearchCategoryItem(
                            title = "Places",
                            description = "Find locations and landmarks",
                            onClick = {
                                searchQuery = ""
                            }
                        )
                    }
                }
            }
        }
            } // Close the !isMinimized if statement
    }
}

@Composable
private fun SearchResultItem(
    result: PlaceSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Location",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = result.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (result.address.isNotEmpty()) {
                Text(
                    text = result.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryItem(
    title: String,
    description: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun BusStopResultItem(
    busStop: BusStop,
    userLocation: LatLng?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate distance from user location
    val distance = userLocation?.let { 
        calculateDistance(it, busStop.location)
    }
    
    val distanceText = distance?.let { dist ->
        when {
            dist < 1000f -> "${dist.toInt()}m"
            else -> "${"%.1f".format(dist / 1000f)}km"
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Bus Stop",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = busStop.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                // Display distance
                distanceText?.let { distance ->
                    Text(
                        text = distance,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            if (busStop.vicinity.isNotEmpty() && busStop.vicinity != "--NA--") {
                Text(
                    text = busStop.vicinity,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            
            // Show tap instruction
            Text(
                text = "Tap to show on map",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Show on map",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Function to search places using Google Places API
private fun searchPlaces(
    context: Context,
    query: String,
    onResult: (List<PlaceSearchResult>) -> Unit
) {
    try {
        // Initialize Places if not already done
        if (!Places.isInitialized()) {
            // You'll need to add your API key to strings.xml
            // For now, using a placeholder - replace with actual API key
            Places.initialize(context, BuildConfig.GOOGLE_API_KEY)
        }

        val placesClient = Places.createClient(context)
        val token = AutocompleteSessionToken.newInstance()

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(token)
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                println("Places API Success: Found ${response.autocompletePredictions.size} predictions")
                val results = response.autocompletePredictions.map { prediction ->
                    PlaceSearchResult(
                        name = prediction.getPrimaryText(null).toString(),
                        address = prediction.getSecondaryText(null)?.toString() ?: "",
                        placeId = prediction.placeId
                    )
                }
                onResult(results)
            }
            .addOnFailureListener { exception ->
                println("Places API Error: ${exception.message}")
                exception.printStackTrace()
                onResult(emptyList())
            }
    } catch (e: Exception) {
        // Handle initialization error
        println("Places API Initialization Error: ${e.message}")
        e.printStackTrace()
        onResult(emptyList())
    }
}

/**
 * Calculate distance between two LatLng points using Android Location API
 * Returns distance in meters
 */
private fun calculateDistance(from: LatLng, to: LatLng): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        from.latitude, from.longitude,
        to.latitude, to.longitude,
        results
    )
    return results[0]
}

// Function to open Google Maps directions
private fun openDirections(context: android.content.Context, latitude: Double, longitude: Double) {
    val uri = Uri.parse("google.navigation:q=$latitude,$longitude&mode=w") // w = walking
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    
    // Check if Google Maps is installed
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // Fallback to web version if Google Maps app is not installed
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=walking")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        context.startActivity(webIntent)
    }
}

@Composable
private fun BusStopDetailsView(
    busStop: BusStop,
    userLocation: LatLng?,
    onGetDirections: (LatLng, LatLng) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate distance
    val distance = userLocation?.let { 
        calculateDistance(it, busStop.location)
    }
    
    val distanceText = distance?.let { dist ->
        when {
            dist < 1000f -> "${dist.toInt()}m away"
            else -> "${"%.1f".format(dist / 1000f)}km away"
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bus Stop Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onBackClick) {
                Text("Back to List")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bus stop details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Bus stop name
                Text(
                    text = busStop.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Distance
                distanceText?.let { distance ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Distance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = distance,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Address/Vicinity
                if (busStop.vicinity.isNotEmpty() && busStop.vicinity != "--NA--") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Address:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = busStop.vicinity,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Location coordinates
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Coordinates:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${busStop.location.latitude}, ${busStop.location.longitude}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                // Bus stop types if available
                if (busStop.types.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Types:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(busStop.types) { type ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.