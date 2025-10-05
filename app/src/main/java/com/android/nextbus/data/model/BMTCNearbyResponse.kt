package com.android.nextbus.data.model

import com.google.gson.annotations.SerializedName

/**
 * BMTC API response model for NearbyStations_v2 endpoint
 * Based on the official BMTC mobile app implementation
 * Note: The actual API response format is different - data is directly returned as array
 */
data class BMTCNearbyResponse(
    @SerializedName("issuccess")
    val isSuccess: Boolean? = null,
    
    @SerializedName("responsecode")
    val responseCode: Int? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: List<BMTCStationData>? = null
)

data class BMTCStationData(
    @SerializedName("rowno")
    val rowNo: Int,
    
    @SerializedName("geofenceid")
    val geofenceId: Int,
    
    @SerializedName("geofencename")
    val geofenceName: String,
    
    @SerializedName("center_lat")
    val centerLat: Double,
    
    @SerializedName("center_lon")
    val centerLon: Double,
    
    @SerializedName("distance")
    val distance: Double,
    
    @SerializedName("totalminute")
    val totalMinute: String?,
    
    @SerializedName("towards")
    val towards: String?,
    
    @SerializedName("radiuskm")
    val radiusKm: Int,
    
    @SerializedName("geofencetypeid")
    val geofenceTypeId: String?,
    
    @SerializedName("responsecode")
    val responseCode: Int
)

/**
 * BMTC API request model for NearbyStations_v2 endpoint
 * Field names must match exactly what BMTC API expects
 */
data class BMTCNearbyRequest(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("stationId")
    val stationId: Int = 0,
    
    @SerializedName("stationflag")
    val stationFlag: Int = 1 // 1 for BMTC, 2 for Charted, 163 for Metro, 164 for KSRTC
)
