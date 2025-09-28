package com.android.nextbus.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {
    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "walking",
        @Query("key") apiKey: String
    ): Response<DirectionsResponse>
}

data class DirectionsResponse(
    val routes: List<Route>,
    val status: String
)

data class Route(
    val legs: List<Leg>,
    val overview_polyline: OverviewPolyline
)

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val steps: List<Step>
)

data class Step(
    val distance: Distance,
    val duration: Duration,
    val html_instructions: String,
    val polyline: OverviewPolyline,
    val start_location: Location,
    val end_location: Location
)

data class Distance(
    val text: String,
    val value: Int
)

data class Duration(
    val text: String,
    val value: Int
)

data class OverviewPolyline(
    val points: String
)

data class Location(
    val lat: Double,
    val lng: Double
)
