package com.android.nextbus.data.network

import android.util.Log
import com.android.nextbus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BusStopRouteService {

    companion object {
        private const val TAG = "BusStopRouteService"
        private const val PLACE_DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json"
        private const val ENTITY_DETAILS_URL = "https://maps.googleapis.com/maps/api/js/jsonp/ApplicationService.GetEntityDetails"
    }

    suspend fun getRoutesForPlace(placeId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val placeUrl = fetchPlaceUrl(placeId)
            if (placeUrl.isNullOrEmpty()) {
                Log.e(TAG, "No place URL for placeId=$placeId")
                return@withContext Result.success(emptyList())
            }

            val cid = extractCid(placeUrl)
            if (cid.isNullOrEmpty()) {
                Log.e(TAG, "No CID found in place URL: $placeUrl")
                return@withContext Result.success(emptyList())
            }

            val jsonp = fetchEntityDetails(cid)
            if (jsonp.isNullOrEmpty()) {
                Log.e(TAG, "Empty entity details response for cid=$cid")
                return@withContext Result.success(emptyList())
            }

            val routes = extractRoutesFromJsonp(jsonp)
            Result.success(routes.distinct())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching routes: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchPlaceUrl(placeId: String): String? {
        val urlString = "$PLACE_DETAILS_URL?place_id=$placeId&fields=url&key=${BuildConfig.GOOGLE_API_KEY}"
        val jsonString = downloadUrl(urlString) ?: return null
        return try {
            val json = JSONObject(jsonString)
            json.optJSONObject("result")?.optString("url", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing place details JSON: ${e.message}", e)
            null
        }
    }

    private fun extractCid(placeUrl: String): String? {
        val regex = "cid=([0-9]+)".toRegex()
        val match = regex.find(placeUrl)
        return match?.groupValues?.getOrNull(1)
    }

    private fun fetchEntityDetails(cid: String): String? {
        val urlString = "$ENTITY_DETAILS_URL?pb=!1m2!1m1!4s${cid}!2m2!1sen-IN!2sUS&callback=cb&key=${BuildConfig.GOOGLE_API_KEY}"
        return downloadUrl(urlString)
    }

    private fun extractRoutesFromJsonp(jsonp: String): List<String> {
        val content = jsonp.removePrefix("cb(").removeSuffix(");").trim()
        val regex = "\\[\\[5,\\s*\\[\"([^\"]+)\"".toRegex()
        val matches = regex.findAll(content)
        return matches.mapNotNull { match ->
            match.groupValues.getOrNull(1)
        }.toList()
    }

    private fun downloadUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val builder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            reader.close()
            builder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading URL $urlString: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
