# NextBus

A modern Android application for tracking BMTC (Bangalore Metropolitan Transport Corporation) buses in real-time. Find nearby bus stops, search for routes, and track live bus locations on an interactive map.

## Features

- **Real-time Bus Tracking**: Track live BMTC buses on the map with automatic updates
- **Nearby Bus Stops**: Discover bus stops near your current location
- **Route Search**: Search for bus routes and view all stops along the route
- **Interactive Map**: Google Maps integration with custom markers and route visualization
- **Route Visualization**: View complete route paths with animated polylines
- **Direction Toggle**: Switch between up and down route directions
- **Bus Stop Details**: View detailed information about bus stops including available routes
- **Live Vehicle Markers**: See real-time bus positions with last refresh timestamps
- **Dark Mode Support**: Automatic dark theme for maps and UI
- **Location Services**: Built-in location tracking with permission handling

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Maps**: Google Maps SDK for Android
- **Location**: Google Play Services Location
- **Places API**: Google Places API for bus stop data
- **State Management**: Kotlin Coroutines + Flow
- **Permissions**: Accompanist Permissions

## Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 24 or higher
- Google Maps API Key
- JDK 11 or higher

## Setup Instructions

### 1. Clone the Repository

```bash
git clone <repository-url>
cd NextBus
```

### 2. Configure Google Maps API Key

1. Create a `.env` file in the project root directory:
   ```
   GOOGLE_API_KEY=your_api_key_here
   ```

2. Obtain a Google Maps API Key:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select an existing one
   - Enable the following APIs:
     - Maps SDK for Android
     - Places API
     - Geocoding API
   - Create credentials (API Key)
   - Copy the API key to your `.env` file

3. The API key will be automatically injected into the build during compilation

### 3. Build and Run

```bash
./gradlew assembleDebug
```

Or run directly from Android Studio.

## Project Structure

```
app/src/main/java/com/android/nextbus/
├── data/
│   ├── model/              # Data models (BusStop, etc.)
│   └── network/            # Network services
│       ├── BmtcRouteService.kt       # BMTC API integration
│       ├── NearbyBusStopService.kt   # Places API integration
│       └── BusStopRouteService.kt    # Route discovery
├── ui/
│   ├── components/         # Reusable UI components
│   │   └── SearchCard.kt   # Bottom search interface
│   ├── maps/              # Map-related UI
│   │   ├── GoogleMapScreen.kt  # Main map screen
│   │   └── MapViewModel.kt     # Map state management
│   └── theme/             # App theming
│       ├── Color.kt
│       ├── Theme.kt
│       ├── Type.kt
│       └── MapStyles.kt   # Dark mode map styling
└── MainActivity.kt        # Entry point
```

## Architecture

The app follows the MVVM (Model-View-ViewModel) architecture pattern:

- **Models**: Data classes representing bus stops, routes, and live vehicles
- **Services**: Network layer for fetching data from BMTC APIs and Google Places
- **ViewModel**: `MapViewModel` manages app state and business logic
- **Views**: Composable functions for UI rendering

### State Management

The app uses Kotlin `StateFlow` for reactive state management:
- Bus stops, routes, and live vehicles are exposed as `StateFlow`
- UI automatically reacts to state changes
- Coroutines handle asynchronous operations

## Key Components

### BmtcRouteService
Interfaces with BMTC's official API to:
- Search for route suggestions
- Fetch stops for specific routes
- Retrieve route polylines
- Get live vehicle positions

### NearbyBusStopService
Uses Google Places API to:
- Find nearby bus stops based on user location
- Resolve BMTC stop names to Google Places
- Match stops to route polylines for accuracy

### MapViewModel
Manages application state including:
- User location tracking
- Bus stop selection and display
- Route search and visualization
- Live vehicle polling and updates
- Search card expansion state

## Features in Detail

### Live Bus Tracking
- Polls BMTC API every second for latest vehicle positions
- Smoothly animates bus markers between positions
- Snaps buses to route polylines for accuracy
- Displays last refresh timestamp

### Route Visualization
- Animated polyline drawing effect
- Color-coded routes (blue for up, red for down)
- Densified polylines for smooth curves
- Direction toggle controls

### Bus Stop Discovery
- Searches within 1km radius
- Resolves BMTC stops to Google Places for accurate coordinates
- Shows custom markers (yellow for selected, red for unselected)
- Displays available routes at each stop

## Permissions

The app requires the following permissions:
- `ACCESS_FINE_LOCATION`: For user location tracking
- `ACCESS_COARSE_LOCATION`: For approximate location
- `INTERNET`: For API calls and map tiles

## API Integration

### BMTC Mobile API
Base URL: `https://bmtcmobileapi.karnataka.gov.in/WebAPI`

Endpoints used:
- `/SearchRoute_v2`: Route search
- `/SearchByRouteDetails_v4`: Route details and stops
- `/RoutePoints`: Polyline coordinates

### Google Maps Platform
- Maps SDK for Android: Map display
- Places API: Bus stop discovery
- Geocoding API: Location services

## Known Limitations

- Live tracking is specific to BMTC (Bangalore) buses only
- Route data depends on BMTC API availability
- Some bus stop coordinates from BMTC may be inaccurate (resolved via Google Places)
- Live vehicle positions may experience delays based on GPS updates

## Future Enhancements

- Favorites management for frequent routes and stops
- Arrival time predictions
- Route planning between locations
- Offline mode with cached data
- Push notifications for bus arrivals
- Support for other cities/transit systems

## Acknowledgments

- BMTC for providing public API access
- Google Maps Platform for mapping services
- Material Design 3 for UI components
