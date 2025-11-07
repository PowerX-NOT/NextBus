package com.android.nextbus.data.model

import com.google.android.gms.maps.model.LatLng

data class BusStop(
    val id: String,
    val name: String,
    val vicinity: String,
    val location: LatLng,
    val reference: String? = null,
    val placeId: String? = null,
    val rating: Double? = null,
    val types: List<String> = emptyList()
)

data class PlacesApiResponse(
    val results: List<PlaceResult>,
    val status: String,
    val error_message: String? = null
)

data class PlaceResult(
    val place_id: String,
    val name: String,
    val vicinity: String? = null,
    val geometry: Geometry,
    val reference: String? = null,
    val rating: Double? = null,
    val types: List<String> = emptyList()
)

data class Geometry(
    val location: LocationData
)

data class LocationData(
    val lat: Double,
    val lng: Double
)

data class OpeningHours(
    val open_now: Boolean
)

// Direction and Route models
data class DirectionsResponse(
    val routes: List<Route> = emptyList(),
    val status: String = ""
)

data class Route(
    val polyline: RoutePolyline? = null,
    val legs: List<RouteLeg> = emptyList(),
    val summary: String? = null
)

data class RoutePolyline(
    val encodedPolyline: String
)

data class RouteLeg(
    val steps: List<RouteStep> = emptyList(),
    val duration: String? = null,
    val distance: String? = null
)

data class RouteStep(
    val polyline: RoutePolyline? = null,
    val transitDetails: TransitDetails? = null
)

data class TransitDetails(
    val transitLine: TransitLine? = null
)

data class TransitLine(
    val nameShort: String? = null
)