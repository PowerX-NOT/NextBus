package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.BuildConfig
import com.android.nextbus.data.model.DirectionsResponse
import com.android.nextbus.data.model.Route
import com.android.nextbus.data.model.RouteLeg
import com.android.nextbus.data.model.RoutePolyline
import com.android.nextbus.data.model.RouteStep
import com.android.nextbus.data.model.TransitDetails
import com.android.nextbus.data.model.TransitLine
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DirectionsService {
    
    companion object {
        private const val TAG = "DirectionsService"
        private const val GOOGLE_ROUTES_API_BASE = "https://routes.googleapis.com/directions/v2:computeRoutes"
    }
    
    suspend fun getTransitDirections(
        origin: LatLng,
        destination: LatLng
    ): Result<DirectionsResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching transit directions from ${origin.latitude},${origin.longitude} to ${destination.latitude},${destination.longitude}")
            
            val requestBody = buildRoutesAPIRequestBody(origin, destination)
            val response = makeRoutesAPIRequest(requestBody)
            
            if (response.isNotEmpty()) {
                val directionsResponse = parseRoutesResponse(response)
                Log.d(TAG, "Successfully parsed ${directionsResponse.routes.size} routes")
                Result.success(directionsResponse)
            } else {
                Result.failure(Exception("Empty response from Routes API"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transit directions: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun buildRoutesAPIRequestBody(origin: LatLng, destination: LatLng): String {
        val requestJson = JSONObject().apply {
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
                put("allowedTravelModes", org.json.JSONArray().apply {
                    put("BUS")
                    put("SUBWAY")
                    put("TRAIN")
                    put("LIGHT_RAIL")
                })
            })
            put("computeAlternativeRoutes", false)
            put("routeModifiers", JSONObject().apply {
                put("avoidTolls", false)
                put("avoidHighways", false)
                put("avoidFerries", false)
            })
        }
        
        return requestJson.toString()
    }
    
    private fun makeRoutesAPIRequest(requestBody: String): String {
        var data = ""
        var urlConnection: HttpURLConnection? = null
        
        try {
            val url = URL(GOOGLE_ROUTES_API_BASE)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_API_KEY)
            urlConnection.setRequestProperty("X-Goog-FieldMask", 
                "routes.polyline.encodedPolyline,routes.legs.steps.polyline.encodedPolyline,routes.legs.steps.transitDetails.transitLine.nameShort")
            urlConnection.doOutput = true
            urlConnection.doInput = true
            
            // Write request body
            val writer = OutputStreamWriter(urlConnection.outputStream)
            writer.write(requestBody)
            writer.flush()
            writer.close()
            
            // Read response
            val responseCode = urlConnection.responseCode
            Log.d(TAG, "Routes API Response Code: $responseCode")
            
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                urlConnection.inputStream
            } else {
                urlConnection.errorStream
            }
            
            val br = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            
            data = sb.toString()
            br.close()
            
            Log.d(TAG, "Routes API Response: ${data.take(500)}...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error making Routes API request: ${e.message}")
            e.printStackTrace()
        } finally {
            urlConnection?.disconnect()
        }
        
        return data
    }
    
    private fun parseRoutesResponse(jsonData: String): DirectionsResponse {
        try {
            Log.d(TAG, "Parsing response: ${jsonData.take(500)}...")
            val jsonObject = JSONObject(jsonData)
            
            // Check for error
            if (jsonObject.has("error")) {
                val error = jsonObject.getJSONObject("error")
                val message = error.optString("message", "Unknown error")
                val code = error.optInt("code", -1)
                val status = error.optString("status", "UNKNOWN")
                Log.e(TAG, "Routes API Error: Code=$code, Status=$status, Message=$message")
                return DirectionsResponse(status = "ERROR")
            }
            
            val routes = mutableListOf<Route>()
            
            if (jsonObject.has("routes")) {
                val routesArray = jsonObject.getJSONArray("routes")
                Log.d(TAG, "Found ${routesArray.length()} routes in response")
                
                for (i in 0 until routesArray.length()) {
                    val routeJson = routesArray.getJSONObject(i)
                    
                    // Parse main route polyline
                    val polyline = if (routeJson.has("polyline")) {
                        val polylineJson = routeJson.getJSONObject("polyline")
                        val encoded = polylineJson.getString("encodedPolyline")
                        Log.d(TAG, "Route $i has polyline: ${encoded.take(50)}...")
                        RoutePolyline(encoded)
                    } else {
                        Log.w(TAG, "Route $i has no polyline field")
                        null
                    }
                    
                    // Parse legs
                    val legs = mutableListOf<RouteLeg>()
                    if (routeJson.has("legs")) {
                        val legsArray = routeJson.getJSONArray("legs")
                        
                        for (j in 0 until legsArray.length()) {
                            val legJson = legsArray.getJSONObject(j)
                            
                            // Parse steps
                            val steps = mutableListOf<RouteStep>()
                            if (legJson.has("steps")) {
                                val stepsArray = legJson.getJSONArray("steps")
                                
                                for (k in 0 until stepsArray.length()) {
                                    val stepJson = stepsArray.getJSONObject(k)
                                    
                                    val stepPolyline = if (stepJson.has("polyline")) {
                                        val stepPolylineJson = stepJson.getJSONObject("polyline")
                                        RoutePolyline(stepPolylineJson.getString("encodedPolyline"))
                                    } else null
                                    
                                    val transitDetails = if (stepJson.has("transitDetails")) {
                                        val transitJson = stepJson.getJSONObject("transitDetails")
                                        val transitLine = if (transitJson.has("transitLine")) {
                                            val lineJson = transitJson.getJSONObject("transitLine")
                                            TransitLine(lineJson.optString("nameShort"))
                                        } else null
                                        TransitDetails(transitLine)
                                    } else null
                                    
                                    steps.add(RouteStep(stepPolyline, transitDetails))
                                }
                            }
                            
                            val duration = legJson.optString("duration")
                            val distance = legJson.optString("distance")
                            
                            legs.add(RouteLeg(steps, duration, distance))
                        }
                    }
                    
                    routes.add(Route(polyline, legs))
                }
            }
            
            Log.d(TAG, "Parsed ${routes.size} routes")
            return DirectionsResponse(routes, "OK")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Routes API response: ${e.message}")
            e.printStackTrace()
            return DirectionsResponse(status = "PARSE_ERROR")
        }
    }
}
