package com.android.nextbus.data.model

import com.google.android.gms.maps.model.LatLng

data class BusStop(
    val id: String,
    val name: String,
    val vicinity: String,
    val location: LatLng,
    val reference: String? = null,
    val placeId: String? = null,
    val rating: Float? = null,
    val types: List<String> = emptyList()
)

data class PlacesApiResponse(
    val results: List<PlaceResult>,
    val status: String,
    val errorMessage: String? = null,
    val nextPageToken: String? = null
)

data class PlaceResult(
    val placeId: String,
    val name: String,
    val vicinity: String? = null,
    val geometry: Geometry,
    val reference: String? = null,
    val rating: Float? = null,
    val types: List<String> = emptyList(),
    val openingHours: OpeningHours? = null
)

data class Geometry(
    val location: LocationData
)

data class LocationData(
    val lat: Double,
    val lng: Double
)

data class OpeningHours(
    val openNow: Boolean
)
