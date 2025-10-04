package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.BusRoute
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
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

class GoogleRoutesService {

    companion object {
        private const val TAG = "GoogleRoutesService"
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
    }

    suspend fun getTransitRoutesForStation(
        stationLocation: LatLng,
        searchRadius: Double = 0.005
    ): Result<List<BusRoute>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching transit routes for station at: ${stationLocation.latitude}, ${stationLocation.longitude}")

            val routes = mutableListOf<BusRoute>()
            val destinations = generateNearbyDestinations(stationLocation, searchRadius)

            for (destination in destinations) {
                try {
                    val routesForDestination = fetchRoutes(stationLocation, destination)
                    routes.addAll(routesForDestination)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching routes to destination: ${e.message}")
                }
            }

            val uniqueRoutes = routes.distinctBy { it.routeNumber }
            Log.d(TAG, "Found ${uniqueRoutes.size} unique routes")

            Result.success(uniqueRoutes)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting transit routes: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun generateNearbyDestinations(origin: LatLng, radius: Double): List<LatLng> {
        val destinations = mutableListOf<LatLng>()

        val offsets = listOf(
            Pair(radius, 0.0),
            Pair(-radius, 0.0),
            Pair(0.0, radius),
            Pair(0.0, -radius),
            Pair(radius, radius),
            Pair(-radius, -radius),
            Pair(radius, -radius),
            Pair(-radius, radius)
        )

        offsets.forEach { (latOffset, lngOffset) ->
            destinations.add(
                LatLng(
                    origin.latitude + latOffset,
                    origin.longitude + lngOffset
                )
            )
        }

        return destinations
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
                })
                put("computeAlternativeRoutes", true)
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
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
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
            e.printStackTrace()
        } catch (e: JSONException) {
            Log.e(TAG, "JSONException in fetchRoutes: ${e.message}")
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }

        return emptyList()
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
                                        val transitLine = transitDetails.getJSONObject("transitLine")

                                        val routeNumber = if (transitLine.has("nameShort")) {
                                            transitLine.getString("nameShort")
                                        } else if (transitLine.has("name")) {
                                            transitLine.getString("name")
                                        } else {
                                            continue
                                        }

                                        val routeName = if (transitLine.has("name")) {
                                            transitLine.getString("name")
                                        } else null

                                        val agency = if (transitDetails.has("transitLineAgencies")) {
                                            val agencies = transitDetails.getJSONArray("transitLineAgencies")
                                            if (agencies.length() > 0) {
                                                agencies.getJSONObject(0).optString("name")
                                            } else null
                                        } else null

                                        val color = if (transitLine.has("color")) {
                                            transitLine.getString("color")
                                        } else null

                                        val textColor = if (transitLine.has("textColor")) {
                                            transitLine.getString("textColor")
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
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing route at index $i: ${e.message}")
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
            e.printStackTrace()
        }

        return busRoutes
    }
}
