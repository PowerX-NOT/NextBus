package com.android.nextbus.ui.theme

import com.google.android.gms.maps.model.MapStyleOptions

object MapStyles {
    val darkMapStyle = MapStyleOptions(
        """
        [
          {
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#1a1a1a"
              }
            ]
          },
          {
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ffffff"
              }
            ]
          },
          {
            "elementType": "labels.text.stroke",
            "stylers": [
              {
                "color": "#1a1a1a"
              }
            ]
          },
          {
            "featureType": "administrative",
            "elementType": "geometry.stroke",
            "stylers": [
              {
                "color": "#4a4a4a"
              }
            ]
          },
          {
            "featureType": "administrative.country",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ffffff"
              }
            ]
          },
          {
            "featureType": "administrative.locality",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ffffff"
              }
            ]
          },
          {
            "featureType": "administrative.neighborhood",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#cccccc"
              }
            ]
          },
          {
            "featureType": "landscape",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#2a2a2a"
              }
            ]
          },
          {
            "featureType": "poi",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#333333"
              }
            ]
          },
          {
            "featureType": "poi",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#cccccc"
              }
            ]
          },
          {
            "featureType": "poi.business",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#3a3a3a"
              }
            ]
          },
          {
            "featureType": "poi.government",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#4a4a4a"
              }
            ]
          },
          {
            "featureType": "poi.medical",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#4a2a2a"
              }
            ]
          },
          {
            "featureType": "poi.park",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#2a4a2a"
              }
            ]
          },
          {
            "featureType": "poi.park",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#88cc88"
              }
            ]
          },
          {
            "featureType": "poi.school",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#4a4a2a"
              }
            ]
          },
          {
            "featureType": "poi.sports_complex",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#2a4a4a"
              }
            ]
          },
          {
            "featureType": "road",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#4a4a4a"
              }
            ]
          },
          {
            "featureType": "road",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ffffff"
              }
            ]
          },
          {
            "featureType": "road.arterial",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#5a5a5a"
              }
            ]
          },
          {
            "featureType": "road.highway",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#6a6a6a"
              }
            ]
          },
          {
            "featureType": "road.highway",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ffffff"
              }
            ]
          },
          {
            "featureType": "road.local",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#3a3a3a"
              }
            ]
          },
          {
            "featureType": "road.local",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#cccccc"
              }
            ]
          },
          {
            "featureType": "transit",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#4a4a6a"
              }
            ]
          },
          {
            "featureType": "transit",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#aaaaff"
              }
            ]
          },
          {
            "featureType": "transit.line",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#6a6aaa"
              }
            ]
          },
          {
            "featureType": "transit.station",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#5a5a8a"
              }
            ]
          },
          {
            "featureType": "transit.station",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#ccccff"
              }
            ]
          },
          {
            "featureType": "water",
            "elementType": "geometry",
            "stylers": [
              {
                "color": "#1a2a3a"
              }
            ]
          },
          {
            "featureType": "water",
            "elementType": "labels.text.fill",
            "stylers": [
              {
                "color": "#6699cc"
              }
            ]
          }
        ]
        """.trimIndent()
    )
}
