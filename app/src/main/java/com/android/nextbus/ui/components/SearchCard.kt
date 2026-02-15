package com.android.nextbus.ui.components

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.BusStop
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import android.location.Location

data class PlaceSearchResult(
    val name: String,
    val address: String,
    val placeId: String? = null
)

private enum class SearchMode { Places, Routes }

@Composable
fun SearchCard(
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    isMinimized: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onMinimizedChange: (Boolean) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onPlaceSelected: (PlaceSearchResult) -> Unit = {},
    onRouteQueryChange: (String) -> Unit = {},
    onRouteSelected: (String) -> Unit = {},
    onBusStopSelected: (BusStop) -> Unit = {},
    busStops: List<BusStop> = emptyList(),
    routeSuggestions: List<String> = emptyList(),
    routeSearchResults: List<BusStop> = emptyList(),
    selectedBusStop: BusStop? = null,
    isLoadingBusStops: Boolean = false,
    isRouteSearchLoading: Boolean = false,
    isRouteSuggestionsLoading: Boolean = false,
    userLocation: com.google.android.gms.maps.model.LatLng? = null,
    routes: List<String> = emptyList(),
    isLoadingRoutes: Boolean = false
) {
    var dragOffset by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var searchMode by remember { mutableStateOf(SearchMode.Places) }
    var selectedRouteNo by remember { mutableStateOf<String?>(null) }
    
    // Function to add item to recent searches
    fun addToRecentSearches(item: PlaceSearchResult) {
        val updatedRecents = listOf(item) + recentSearches.filter { it.name != item.name }
        recentSearches = updatedRecents.take(5) // Keep only last 5 recent searches
    }
    
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val context = LocalContext.current
    
    // Detect keyboard state
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible = imeInsets.getBottom(density) > 0
    
    // Animation for expansion - 95% when keyboard visible, 50% when manually expanded, 160dp when collapsed
    val animatedHeight by animateFloatAsState(
        targetValue = when {
            isKeyboardVisible -> screenHeight.value * 0.95f // 95% when keyboard is visible
            isExpanded -> screenHeight.value * 0.5f // 50% when manually expanded
            else -> 160f // Normal collapsed height
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
                        // Determine if we should expand or collapse based on drag direction and distance
                        if (dragOffset < -100f) { // Dragged up significantly
                            onExpandedChange(true)
                        } else if (dragOffset > 100f) { // Dragged down significantly
                            onExpandedChange(false)
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
                    .clickable { onExpandedChange(!isExpanded) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Search bar - hide when bus stops are shown
            if (!isExpanded && busStops.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { 
                            onExpandedChange(true)
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
            } else if (isExpanded && busStops.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = searchMode == SearchMode.Places,
                        onClick = {
                            searchMode = SearchMode.Places
                            searchQuery = ""
                            isSearching = false
                            searchResults = emptyList()
                            onRouteQueryChange("")
                            selectedRouteNo = null
                        },
                        label = { Text("Places") }
                    )
                    FilterChip(
                        selected = searchMode == SearchMode.Routes,
                        onClick = {
                            searchMode = SearchMode.Routes
                            searchQuery = ""
                            isSearching = false
                            searchResults = emptyList()
                            onRouteQueryChange("")
                            selectedRouteNo = null
                        },
                        label = { Text("Routes") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active search field when expanded
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        selectedRouteNo = null
                        if (searchMode == SearchMode.Places) {
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
                        } else {
                            onRouteQueryChange(query)
                        }
                    },
                    placeholder = {
                        Text(
                            if (searchMode == SearchMode.Places) {
                                "Search for places..."
                            } else {
                                "Search bus stops by route (e.g. 500D)"
                            }
                        )
                    },
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick actions - hide favorites when bus stops are shown
            if (busStops.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Favorites button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { onFavoritesClick() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
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
                    
                    // Nearby stops
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { onNearbyClick() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Nearby",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Nearby",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Show heading for bus stops or selected bus stop with divider below
            if (busStops.isNotEmpty() || selectedBusStop != null) {
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (selectedBusStop != null) {
                        selectedBusStop.name
                    } else {
                        "Nearby Bus Stations (${busStops.size})"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                
                // NextBus branding - show when not expanded (for both nearby list and selected bus stop)
                if (!isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "NextBus",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Expanded content - only visible when expanded
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Loading state for bus stops
                if (isLoadingBusStops) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Searching for nearby bus stops...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Selected bus stop details or bus stop results
                else if (selectedBusStop != null) {
                    // Show selected bus stop details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Address/Vicinity
                        if (selectedBusStop.vicinity.isNotEmpty()) {
                            Text(
                                text = selectedBusStop.vicinity,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Distance if available
                        userLocation?.let { userLoc ->
                            val distance = calculateDistance(userLoc, selectedBusStop.location)
                            val distanceText = if (distance < 1000) {
                                "${distance.toInt()} m away"
                            } else {
                                "${String.format("%.0f", distance / 1000)} km away"
                            }
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        // Rating if available
                        selectedBusStop.rating?.let { rating ->
                            Text(
                                text = "Rating: ${String.format("%.1f", rating)} ⭐",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Routes for this bus stop
                        when {
                            isLoadingRoutes -> {
                                Text(
                                    text = "Loading routes...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            routes.isNotEmpty() -> {
                                Text(
                                    text = "Routes (${routes.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    items(routes) { route ->
                                        RouteChip(label = route)
                                    }
                                }
                            }
                        }
                        
                        // Types/Categories
                        if (selectedBusStop.types.isNotEmpty()) {
                            Text(
                                text = selectedBusStop.types.joinToString(", ") { 
                                    it.replace("_", " ").replaceFirstChar { char -> 
                                        if (char.isLowerCase()) char.titlecase() else char.toString() 
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        
                        // Location coordinates
                        Text(
                            text = "Location: ${String.format("%.6f", selectedBusStop.location.latitude)}, ${String.format("%.6f", selectedBusStop.location.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                else if (busStops.isNotEmpty()) {
                    LazyColumn {
                        items(busStops) { busStop ->
                            BusStopResultItem(
                                busStop = busStop,
                                userLocation = userLocation,
                                onClick = {
                                    onBusStopSelected(busStop)
                                }
                            )
                        }
                    }
                }
                else if (searchMode == SearchMode.Routes) {
                    when {
                        isRouteSuggestionsLoading -> {
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
                        }
                        searchQuery.isBlank() -> {
                            Text(
                                text = "Enter a route number to search",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        routeSuggestions.isEmpty() -> {
                            Text(
                                text = "No matching routes found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = "Route (${routeSuggestions.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn {
                                items(routeSuggestions) { routeNo ->
                                    RouteSuggestionItem(
                                        routeNo = routeNo,
                                        onClick = {
                                            selectedRouteNo = routeNo
                                            onRouteSelected(routeNo)
                                        }
                                    )
                                }
                            }

                            if (selectedRouteNo != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                when {
                                    isRouteSearchLoading -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    routeSearchResults.isNotEmpty() -> {
                                        Text(
                                            text = "Stops (${routeSearchResults.size})",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LazyColumn {
                                            items(routeSearchResults) { busStop ->
                                                BusStopResultItem(
                                                    busStop = busStop,
                                                    userLocation = userLocation,
                                                    onClick = {
                                                        onBusStopSelected(busStop)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Search results or suggestions
                else if (searchQuery.isNotEmpty()) {
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
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn {
                            items(searchResults) { result ->
                                SearchResultItem(
                                    result = result,
                                    onClick = {
                                        addToRecentSearches(result)
                                        onPlaceSelected(result)
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                )
                            }
                        }
                    }
                } else if (recentSearches.isNotEmpty()) {
                    // Recent searches when no search results
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn {
                        items(recentSearches) { result ->
                            SearchResultItem(
                                result = result,
                                onClick = {
                                    onPlaceSelected(result)
                                    searchQuery = ""
                                    searchResults = emptyList()
                                }
                            )
                        }
                    }
                } else {
                    // Show message when no recent searches
                    Text(
                        text = "No recent searches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSuggestionItem(
    routeNo: String,
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
            contentDescription = "Route",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = routeNo,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RouteChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
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
    userLocation: com.google.android.gms.maps.model.LatLng?,
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
            contentDescription = "Bus Stop",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = busStop.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (busStop.vicinity.isNotEmpty() && busStop.vicinity != "--NA--") {
                Text(
                    text = busStop.vicinity,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            
            // Show distance if user location is available
            userLocation?.let { userLoc ->
                val distance = calculateDistance(userLoc, busStop.location)
                val distanceText = if (distance < 1000) {
                    "${distance.toInt()} m away"
                } else {
                    "${String.format("%.0f", distance / 1000)} km away"
                }
                Text(
                    text = distanceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            
            // Show bus stop types
            if (busStop.types.isNotEmpty()) {
                Text(
                    text = busStop.types.joinToString(", ") { 
                        it.replace("_", " ").replaceFirstChar { char -> 
                            if (char.isLowerCase()) char.titlecase() else char.toString() 
                        } 
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        
        // Show rating if available
        busStop.rating?.let { rating ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "★",
                    color = Color(0xFFFFD700), // Gold color
                    fontSize = 16.sp
                )
                Text(
                    text = String.format("%.1f", rating),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Helper function to calculate distance between two LatLng points
private fun calculateDistance(point1: com.google.android.gms.maps.model.LatLng, point2: com.google.android.gms.maps.model.LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        point1.latitude, point1.longitude,
        point2.latitude, point2.longitude,
        results
    )
    return results[0]
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
