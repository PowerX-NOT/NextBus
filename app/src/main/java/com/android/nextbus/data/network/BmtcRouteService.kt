package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.data.model.BusStop
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BmtcRouteService {

    companion object {
        private const val TAG = "BmtcRouteService"
        private const val BASE_URL = "https://bmtcmobileapi.karnataka.gov.in/WebAPI"
    }

    data class RouteSuggestion(
        val routeNo: String,
        val routeParentId: Int
    )

    data class RoutePolyline(
        val direction: Int,
        val points: List<LatLng>
    )

    data class LiveVehicle(
        val direction: Int,
        val vehicleId: Int?,
        val vehicleNumber: String?,
        val location: LatLng?,
        val eta: String?,
        val lastRefreshOn: String?
    )

    suspend fun searchRoutes(query: String): Result<List<RouteSuggestion>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val searchJson = postJson(
                url = "$BASE_URL/SearchRoute_v2",
                body = JSONObject().put("routetext", trimmed)
            )

            val data = (searchJson.opt("data") as? JSONArray) ?: return@withContext Result.success(emptyList())
            if (data.length() == 0) return@withContext Result.success(emptyList())

            val results = mutableListOf<RouteSuggestion>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val routeNo = item.optString("routeno").trim()
                val parentId = item.optInt("routeparentid", -1)
                if (routeNo.isEmpty() || parentId <= 0) continue
                results.add(RouteSuggestion(routeNo = routeNo, routeParentId = parentId))
            }

            Result.success(results.distinctBy { it.routeNo })
        } catch (e: Exception) {
            Log.e(TAG, "Error searching BMTC routes: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchStopsForRoute(route: RouteSuggestion): Result<List<BusStop>> =
        fetchStopsForRouteParentId(routeNo = route.routeNo, routeParentId = route.routeParentId)

    suspend fun fetchRoutePolylines(routeNo: String): Result<List<RoutePolyline>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = routeNo.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val routeParentId = resolveRouteParentId(trimmed) ?: return@withContext Result.success(emptyList())
            val detailsJson = postJson(
                url = "$BASE_URL/SearchByRouteDetails_v4",
                body = JSONObject()
                    .put("routeid", routeParentId)
                    .put("servicetypeid", 0),
                extraHeaders = mapOf("deviceType" to "WEB")
            )

            val upStops = ((detailsJson.optJSONObject("up")?.opt("data")) as? JSONArray) ?: JSONArray()
            val downStops = ((detailsJson.optJSONObject("down")?.opt("data")) as? JSONArray) ?: JSONArray()

            val polylines = mutableListOf<RoutePolyline>()

            val upRouteId = upStops.optJSONObject(0)?.optInt("routeid", -1) ?: -1
            if (upRouteId > 0) {
                val points = fetchRoutePoints(upRouteId)
                if (points.size >= 2) {
                    polylines.add(RoutePolyline(direction = 0, points = points))
                }
            }

            val downRouteId = downStops.optJSONObject(0)?.optInt("routeid", -1) ?: -1
            if (downRouteId > 0) {
                val points = fetchRoutePoints(downRouteId)
                if (points.size >= 2) {
                    polylines.add(RoutePolyline(direction = 1, points = points))
                }
            }

            Result.success(polylines)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching BMTC route polylines: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchRouteStops(routeNo: String): Result<List<BusStop>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = routeNo.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val routeParentId = resolveRouteParentId(trimmed) ?: return@withContext Result.success(emptyList())
            fetchStopsForRouteParentId(routeNo = trimmed, routeParentId = routeParentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching BMTC route stops: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLiveVehicles(routeNo: String): Result<List<LiveVehicle>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = routeNo.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val routeParentId = resolveRouteParentId(trimmed) ?: return@withContext Result.success(emptyList())
            val detailsJson = postJson(
                url = "$BASE_URL/SearchByRouteDetails_v4",
                body = JSONObject()
                    .put("routeid", routeParentId)
                    .put("servicetypeid", 0),
                extraHeaders = mapOf("deviceType" to "WEB")
            )

            val upMap = ((detailsJson.optJSONObject("up")?.opt("mapData")) as? JSONArray) ?: JSONArray()
            val downMap = ((detailsJson.optJSONObject("down")?.opt("mapData")) as? JSONArray) ?: JSONArray()

            val out = ArrayList<LiveVehicle>(upMap.length() + downMap.length())
            out.addAll(parseLiveVehicles(direction = 0, mapData = upMap))
            out.addAll(parseLiveVehicles(direction = 1, mapData = downMap))
            Result.success(out)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching BMTC live vehicles: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchStopsForRouteParentId(routeNo: String, routeParentId: Int): Result<List<BusStop>> =
        withContext(Dispatchers.IO) {
            try {
                val detailsJson = postJson(
                    url = "$BASE_URL/SearchByRouteDetails_v4",
                    body = JSONObject()
                        .put("routeid", routeParentId)
                        .put("servicetypeid", 0),
                    extraHeaders = mapOf("deviceType" to "WEB")
                )

                val upStops = ((detailsJson.optJSONObject("up")?.opt("data")) as? JSONArray) ?: JSONArray()
                val downStops = ((detailsJson.optJSONObject("down")?.opt("data")) as? JSONArray) ?: JSONArray()

                val results = mutableListOf<BusStop>()
                results.addAll(parseStops(direction = 0, routeNo = routeNo, stopsArray = upStops))
                results.addAll(parseStops(direction = 1, routeNo = routeNo, stopsArray = downStops))

                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching BMTC route stops: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun fetchRoutePoints(routeId: Int): List<LatLng> {
        val json = postJson(
            url = "$BASE_URL/RoutePoints",
            body = JSONObject().put("routeid", routeId)
        )

        val data = (json.opt("data") as? JSONArray) ?: return emptyList()
        if (data.length() == 0) return emptyList()

        val points = ArrayList<LatLng>(data.length())
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val latStr = obj.optString("latitude")
            val lngStr = obj.optString("longitude")
            val lat = latStr.toDoubleOrNull() ?: continue
            val lng = lngStr.toDoubleOrNull() ?: continue
            points.add(LatLng(lat, lng))
        }
        return points
    }

    private fun resolveRouteParentId(routeNo: String): Int? {
        val trimmed = routeNo.trim()
        if (trimmed.isEmpty()) return null

        val keysToTry = buildList {
            add(trimmed)
            val firstToken = trimmed.split(Regex("\\s+"), limit = 2).firstOrNull()?.trim().orEmpty()
            if (firstToken.isNotEmpty() && !firstToken.equals(trimmed, ignoreCase = true)) {
                add(firstToken)
            }
        }

        for (key in keysToTry) {
            val resolved = resolveRouteParentIdExact(key)
            if (resolved != null) return resolved
        }

        return null
    }

    private fun resolveRouteParentIdExact(routeNo: String): Int? {
        val searchJson = postJson(
            url = "$BASE_URL/SearchRoute_v2",
            body = JSONObject().put("routetext", routeNo)
        )

        val data = (searchJson.opt("data") as? JSONArray) ?: return null
        if (data.length() == 0) return null

        var first: JSONObject? = null
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            if (first == null) first = item
            val rn = item.optString("routeno").trim()
            if (rn.equals(routeNo, ignoreCase = true)) {
                val parentId = item.optInt("routeparentid", -1)
                return if (parentId > 0) parentId else null
            }
        }

        val fallback = first?.optInt("routeparentid", -1) ?: -1
        return if (fallback > 0) fallback else null
    }

    private fun parseStops(direction: Int, routeNo: String, stopsArray: JSONArray): List<BusStop> {
        val stops = mutableListOf<BusStop>()
        for (i in 0 until stopsArray.length()) {
            val stopObj = stopsArray.optJSONObject(i) ?: continue
            val name = stopObj.optString("stationname").trim()
            val lat = stopObj.optDouble("centerlat", Double.NaN)
            val lng = stopObj.optDouble("centerlong", Double.NaN)

            if (name.isEmpty() || lat.isNaN() || lng.isNaN()) continue

            stops.add(
                BusStop(
                    id = "bmtc:${routeNo}:${direction}:${i + 1}:${name}",
                    name = name,
                    vicinity = "",
                    location = LatLng(lat, lng),
                    placeId = null,
                    rating = null,
                    types = emptyList()
                )
            )
        }
        return stops
    }

    private fun parseLiveVehicles(direction: Int, mapData: JSONArray): List<LiveVehicle> {
        if (mapData.length() == 0) return emptyList()
        val out = ArrayList<LiveVehicle>(mapData.length())
        for (i in 0 until mapData.length()) {
            val obj = mapData.optJSONObject(i) ?: continue
            val vehicleId = obj.optInt("vehicleid").takeIf { it != 0 }
            val vehicleNumber = obj.optString("vehiclenumber").takeIf { it.isNotBlank() }
            val lat = obj.optDouble("centerlat", Double.NaN)
            val lng = obj.optDouble("centerlong", Double.NaN)
            val location = if (!lat.isNaN() && !lng.isNaN()) LatLng(lat, lng) else null
            val eta = obj.optString("eta").takeIf { it.isNotBlank() }
            val last = obj.optString("lastrefreshon").takeIf { it.isNotBlank() }

            out.add(
                LiveVehicle(
                    direction = direction,
                    vehicleId = vehicleId,
                    vehicleNumber = vehicleNumber,
                    location = location,
                    eta = eta,
                    lastRefreshOn = last
                )
            )
        }
        return out
    }

    private fun postJson(url: String, body: JSONObject, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        var connection: HttpURLConnection? = null
        return try {
            val urlObj = URL(url)
            connection = (urlObj.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000

                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("lan", "en")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("deviceType", "WEB")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                for ((k, v) in extraHeaders) {
                    setRequestProperty(k, v)
                }
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    append(line)
                }
            }
            reader.close()

            JSONObject(response)
        } finally {
            connection?.disconnect()
        }
    }
}
