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
    val types: List<String> = emptyList(),
    val distance: Double = 0.0, // Distance from user location in meters
    val towards: String? = null // Direction/towards information from BMTC
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