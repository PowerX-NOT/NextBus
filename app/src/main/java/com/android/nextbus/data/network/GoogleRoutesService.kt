package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.BusRoute
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

class GoogleRoutesService {

    companion object {
        private const val TAG = "GoogleRoutesService"
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val PLACES_DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json"
        private const val PLACES_NEARBY_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    }

    suspend fun getTransitRoutesForStation(
        stationLocation: LatLng,
        searchRadius: Double = 0.01 // Increased radius to ~1km
    ): Result<List<BusRoute>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching comprehensive transit routes for station at: ${stationLocation.latitude}, ${stationLocation.longitude}")

            val routes = mutableSetOf<BusRoute>() // Use Set to avoid duplicates
            
            // Strategy 1: Get routes using multiple destination patterns
            val destinationRoutes = fetchRoutesFromMultipleDestinations(stationLocation, searchRadius)
            routes.addAll(destinationRoutes)
            
            // Strategy 2: Try to get transit information from nearby places
            val nearbyTransitRoutes = fetchRoutesFromNearbyTransitStops(stationLocation)
            routes.addAll(nearbyTransitRoutes)
            
            // Strategy 3: Use a grid pattern for more comprehensive coverage
            val gridRoutes = fetchRoutesFromGridPattern(stationLocation, searchRadius)
            routes.addAll(gridRoutes)

            val uniqueRoutes = routes.toList().distinctBy { "${it.routeNumber}-${it.agency}" }
            Log.d(TAG, "Found ${uniqueRoutes.size} unique routes after comprehensive search")

            Result.success(uniqueRoutes)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting transit routes: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun fetchRoutesFromMultipleDestinations(
        origin: LatLng, 
        radius: Double
    ): List<BusRoute> = withContext(Dispatchers.IO) {
        val routes = mutableListOf<BusRoute>()
        
        // Generate more comprehensive destination points
        val destinations = generateComprehensiveDestinations(origin, radius)
        
        // Use coroutines to make parallel API calls for better performance
        val routeJobs = destinations.chunked(5).map { destinationChunk ->
            async {
                val chunkRoutes = mutableListOf<BusRoute>()
                destinationChunk.forEach { destination ->
                    try {
                        val routesForDestination = fetchRoutes(origin, destination)
                        chunkRoutes.addAll(routesForDestination)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching routes to destination: ${e.message}")
                    }
                }
                chunkRoutes
            }
        }
        
        val allChunkResults = routeJobs.awaitAll()
        allChunkResults.forEach { routes.addAll(it) }
        
        return@withContext routes
    }

    private suspend fun fetchRoutesFromNearbyTransitStops(
        stationLocation: LatLng
    ): List<BusRoute> = withContext(Dispatchers.IO) {
        val routes = mutableListOf<BusRoute>()
        
        try {
            // Find nearby transit stops first
            val nearbyStops = findNearbyTransitStops(stationLocation)
            
            // For each nearby stop, try to get routes
            nearbyStops.forEach { nearbyStop ->
                try {
                    val routesToNearby = fetchRoutes(stationLocation, nearbyStop)
                    routes.addAll(routesToNearby)
                    
                    // Also try reverse direction
                    val routesFromNearby = fetchRoutes(nearbyStop, stationLocation)
                    routes.addAll(routesFromNearby)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching routes to nearby stop: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearby transit stops: ${e.message}")
        }
        
        return@withContext routes
    }

    private suspend fun fetchRoutesFromGridPattern(
        origin: LatLng, 
        radius: Double
    ): List<BusRoute> = withContext(Dispatchers.IO) {
        val routes = mutableListOf<BusRoute>()
        
        // Create a grid pattern around the station for comprehensive coverage
        val gridSize = 5 // 5x5 grid
        val step = radius / gridSize
        
        for (i in -gridSize..gridSize) {
            for (j in -gridSize..gridSize) {
                if (i == 0 && j == 0) continue // Skip origin point
                
                val destination = LatLng(
                    origin.latitude + (i * step),
                    origin.longitude + (j * step)
                )
                
                try {
                    val gridRoutes = fetchRoutes(origin, destination)
                    routes.addAll(gridRoutes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching routes for grid point ($i, $j): ${e.message}")
                }
            }
        }
        
        return@withContext routes
    }

    private fun generateComprehensiveDestinations(origin: LatLng, radius: Double): List<LatLng> {
        val destinations = mutableListOf<LatLng>()

        // Radial pattern - multiple rings at different distances
        val rings = listOf(radius * 0.3, radius * 0.6, radius * 1.0)
        val anglesPerRing = listOf(8, 12, 16) // More points for outer rings

        rings.forEachIndexed { ringIndex, ringRadius ->
            val angles = anglesPerRing[ringIndex]
            for (i in 0 until angles) {
                val angle = (2 * PI * i) / angles
                val latOffset = ringRadius * cos(angle)
                val lngOffset = ringRadius * sin(angle)
                
                destinations.add(
                    LatLng(
                        origin.latitude + latOffset,
                        origin.longitude + lngOffset
                    )
                )
            }
        }

        // Add cardinal and intercardinal directions at various distances
        val directions = listOf(
            Pair(1.0, 0.0), Pair(-1.0, 0.0), Pair(0.0, 1.0), Pair(0.0, -1.0), // Cardinal
            Pair(1.0, 1.0), Pair(-1.0, -1.0), Pair(1.0, -1.0), Pair(-1.0, 1.0) // Intercardinal
        )
        
        val distances = listOf(radius * 0.5, radius * 0.8, radius * 1.2)
        
        directions.forEach { (latDir, lngDir) ->
            distances.forEach { distance ->
                destinations.add(
                    LatLng(
                        origin.latitude + (latDir * distance),
                        origin.longitude + (lngDir * distance)
                    )
                )
            }
        }

        return destinations
    }

    private fun findNearbyTransitStops(location: LatLng): List<LatLng> {
        val nearbyStops = mutableListOf<LatLng>()
        
        try {
            val url = "$PLACES_NEARBY_URL?" +
                    "location=${location.latitude},${location.longitude}&" +
                    "radius=1000&" +
                    "type=transit_station&" +
                    "key=${BuildConfig.GOOGLE_API_KEY}"
            
            val response = makeHttpRequest(url)
            val jsonObject = JSONObject(response)
            
            if (jsonObject.has("results")) {
                val results = jsonObject.getJSONArray("results")
                for (i in 0 until minOf(results.length(), 10)) { // Limit to 10 nearby stops
                    val result = results.getJSONObject(i)
                    val geometry = result.getJSONObject("geometry")
                    val locationObj = geometry.getJSONObject("location")
                    
                    nearbyStops.add(
                        LatLng(
                            locationObj.getDouble("lat"),
                            locationObj.getDouble("lng")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearby transit stops: ${e.message}")
        }
        
        return nearbyStops
    }

    private fun fetchRoutes(origin: LatLng, destination: LatLng): List<BusRoute> {
        val url = URL(ROUTES_API_URL)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_API_KEY)
            connection.setRequestProperty("X-Goog-FieldMask", "routes.legs.steps.transitDetails")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("origin", JSONObject().apply {
                    put("location", JSONObject().apply {
                        put("latLng", JSONObject().apply {
                            put("latitude", origin.latitude)
                            put("longitude", origin.longitude)
                        })
                    })
                })
                put("destination", JSONObject().apply {
                    put("location", JSONObject().apply {
                        put("latLng", JSONObject().apply {
                            put("latitude", destination.latitude)
                            put("longitude", destination.longitude)
                        })
                    })
                })
                put("travelMode", "TRANSIT")
                put("transitPreferences", JSONObject().apply {
                    put("routingPreference", "FEWER_TRANSFERS")
                    put("allowedTravelModes", JSONArray().apply {
                        put("BUS")
                        put("SUBWAY")
                        put("TRAIN")
                        put("LIGHT_RAIL")
                    })
                })
                put("computeAlternativeRoutes", true)
                put("routeModifiers", JSONObject().apply {
                    put("avoidTolls", false)
                    put("avoidHighways", false)
                    put("avoidFerries", false)
                })
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                return parseRoutesResponse(response.toString())
            } else {
                Log.e(TAG, "Routes API error: $responseCode")
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = StringBuilder()
                var line: String?

                while (errorReader.readLine().also { line = it } != null) {
                    errorResponse.append(line)
                }
                errorReader.close()

                Log.e(TAG, "Error response: $errorResponse")
            }

        } catch (e: IOException) {
            Log.e(TAG, "IOException in fetchRoutes: ${e.message}")
        } catch (e: JSONException) {
            Log.e(TAG, "JSONException in fetchRoutes: ${e.message}")
        } finally {
            connection.disconnect()
        }

        return emptyList()
    }

    private fun makeHttpRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.requestMethod = "GET"
            connection.connect()
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            
            response.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error making HTTP request: ${e.message}")
            ""
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRoutesResponse(jsonData: String): List<BusRoute> {
        val busRoutes = mutableListOf<BusRoute>()

        try {
            val jsonObject = JSONObject(jsonData)

            if (!jsonObject.has("routes")) {
                return emptyList()
            }

            val routesArray = jsonObject.getJSONArray("routes")

            for (i in 0 until routesArray.length()) {
                try {
                    val route = routesArray.getJSONObject(i)

                    if (route.has("legs")) {
                        val legs = route.getJSONArray("legs")

                        for (j in 0 until legs.length()) {
                            val leg = legs.getJSONObject(j)

                            if (leg.has("steps")) {
                                val steps = leg.getJSONArray("steps")

                                for (k in 0 until steps.length()) {
                                    val step = steps.getJSONObject(k)

                                    if (step.has("transitDetails")) {
                                        val transitDetails = step.getJSONObject("transitDetails")
                                        
                                        if (transitDetails.has("transitLine")) {
                                            val transitLine = transitDetails.getJSONObject("transitLine")

                                            val routeNumber = when {
                                                transitLine.has("nameShort") -> transitLine.getString("nameShort")
                                                transitLine.has("name") -> {
                                                    val name = transitLine.getString("name")
                                                    // Extract route number from name if it contains numbers
                                                    val numberPattern = Regex("\\d+[A-Za-z]*")
                                                    numberPattern.find(name)?.value ?: name
                                                }
                                                else -> continue
                                            }

                                            val routeName = if (transitLine.has("name")) {
                                                transitLine.getString("name")
                                            } else null

                                            val agency = when {
                                                transitDetails.has("transitLineAgencies") -> {
                                                    val agencies = transitDetails.getJSONArray("transitLineAgencies")
                                                    if (agencies.length() > 0) {
                                                        agencies.getJSONObject(0).optString("name", null)
                                                    } else null
                                                }
                                                transitLine.has("agencies") -> {
                                                    val agencies = transitLine.getJSONArray("agencies")
                                                    if (agencies.length() > 0) {
                                                        agencies.getJSONObject(0).optString("name", null)
                                                    } else null
                                                }
                                                else -> null
                                            }

                                            val color = transitLine.optString("color", null)
                                            val textColor = transitLine.optString("textColor", null)

                                            // Get vehicle type for better categorization
                                            val vehicleType = if (transitDetails.has("transitLine")) {
                                                transitLine.optString("vehicle", null)
                                            } else null

                                            busRoutes.add(
                                                BusRoute(
                                                    routeNumber = routeNumber,
                                                    routeName = routeName,
                                                    agency = agency,
                                                    color = color,
                                                    textColor = textColor
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing route at index $i: ${e.message}")
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
        }

        return busRoutes
    }
}