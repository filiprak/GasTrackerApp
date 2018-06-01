package spdb.gastracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.res.ResourcesCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.github.kittinunf.fuel.Fuel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment
import com.google.android.gms.location.places.ui.PlaceSelectionListener

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import org.json.JSONArray
import spdb.gastracker.utils.DialogForm
import spdb.gastracker.widgets.PricePicker
import org.json.JSONObject
import spdb.gastracker.widgets.StationInfoWindowAdapter


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation = Location("kappa")
    lateinit var locationCallback: LocationCallback
    lateinit var locationRequest: LocationRequest
    var locationUpdateState = false

    private var mOptionsMenu: Menu? = null

    private lateinit var form: DialogForm
    private lateinit var routeForm: DialogForm

    private var gasNetworks: HashMap<Int, GasNetwork> = hashMapOf()

    private lateinit var rest: RestApi

    // map data cache
    private var stationMarkers: MutableList<Marker> = mutableListOf<Marker>()
    private var clusterPolygons: MutableList<Polygon> = mutableListOf<Polygon>()
    private var cluster_centerMarkers: MutableList<Marker> = mutableListOf<Marker>()

    // route endpoints
    var geoApiContext: GeoApiContext? = null
    var routeOrigin: Place? = null
    var routeDest: Place? = null
    private var routeMarkers: MutableList<Marker> = mutableListOf<Marker>()
    private var routePolyline: MutableList<Polyline> = mutableListOf<Polyline>()

    private val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loader("on")
        Log.i("onCreate", "Odpala sie")


        lastLocation.latitude = 52.23
        lastLocation.longitude = 21.01
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                lastLocation = p0?.lastLocation ?: lastLocation
                val currentPosition = LatLng(lastLocation.latitude, lastLocation.longitude)
                showClusterStations(currentPosition, "PB95")
            }
        }
        createLocationRequest()


        // init form
        form = object : DialogForm(this@MapsActivity, R.layout.station_form, "Gas station", mapOf(
                "network_id" to R.id.station_network,
                "PB95" to R.id.pb_price,
                "ON" to R.id.on_price,
                "LPG" to R.id.lpg_price,

                "hasPB95" to R.id.pb_checkBox,
                "hasON" to R.id.on_checkBox,
                "hasLPG" to R.id.lpg_checkBox
        )) {
            override fun success(data: Map<String, Any>) {
                Log.i("gastracker", Gson().toJson(data))
            }

            override fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {
                val pb_control = view.findViewById<PricePicker>(R.id.pb_price)
                val on_control = view.findViewById<PricePicker>(R.id.on_price)
                val lpg_control = view.findViewById<PricePicker>(R.id.lpg_price)

                pb_control.isEnabled = false
                on_control.isEnabled = false
                lpg_control.isEnabled = false

                view.findViewById<CheckBox>(R.id.pb_checkBox).setOnCheckedChangeListener({ compoundButton, b ->
                    pb_control.isEnabled = b
                })
                view.findViewById<CheckBox>(R.id.on_checkBox).setOnCheckedChangeListener({ compoundButton, b ->
                    on_control.isEnabled = b
                })
                view.findViewById<CheckBox>(R.id.lpg_checkBox).setOnCheckedChangeListener({ compoundButton, b ->
                    lpg_control.isEnabled = b
                })

                // add networks
                val netw_spinner = view.findViewById<Spinner>(R.id.station_network)

            }
        }
        // route form
        routeForm = object : DialogForm(this@MapsActivity, R.layout.route_form, "New route", mapOf()) {
            override fun success(data: Map<String, Any>) {
                if (routeOrigin == null || routeDest == null) {
                    errorSnackbar("Please enter route origin and destination")
                    return
                }

                loader("on")
                if (geoApiContext == null) geoApiContext = createGeoApiContext()
                DirectionsApi.newRequest(geoApiContext)
                        .mode(TravelMode.DRIVING)
                        .origin(com.google.maps.model.LatLng(routeOrigin!!.latLng.latitude, routeOrigin!!.latLng.longitude))
                        .destination(com.google.maps.model.LatLng(routeDest!!.latLng.latitude, routeDest!!.latLng.longitude))
                        .setCallback(object : com.google.maps.PendingResult.Callback<DirectionsResult> {
                            override fun onFailure(e: Throwable?) {
                                runOnUiThread {
                                    errorSnackbar("Failed to create route: ${e?.message}")
                                    this@MapsActivity.loader("off")
                                }
                            }

                            override fun onResult(result: DirectionsResult?) {
                                if (result != null) {
                                    // draw route
                                    runOnUiThread {
                                        drawRoute(result)
                                        val llbuilder = LatLngBounds.Builder()
                                        var empty = true
                                        routePolyline.forEach { polyline: Polyline ->
                                            polyline.points.forEach { latLng: LatLng? ->
                                                if (latLng != null) llbuilder.include(latLng)
                                                empty = false
                                            }
                                        }
                                        if (!empty)
                                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                                        this@MapsActivity.loader("off")
                                    }
                                }
                            }

                        })

                Log.i("gastracker", "Route: origin: ${routeOrigin?.address}, dest: ${routeDest?.address}")
            }

            override fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {
                val originplace = fragmentManager.findFragmentById(R.id.origin_fragment) as PlaceAutocompleteFragment
                val destplace = fragmentManager.findFragmentById(R.id.dest_fragment) as PlaceAutocompleteFragment

                originplace.setOnPlaceSelectedListener(object : PlaceSelectionListener {
                    override fun onPlaceSelected(p: Place?) {
                        if (p != null) {
                            routeOrigin = p
                        }
                    }

                    override fun onError(p0: Status?) {
                        errorSnackbar("Place selection error: ${p0}")
                    }
                })
                destplace.setOnPlaceSelectedListener(object : PlaceSelectionListener {
                    override fun onPlaceSelected(p: Place?) {
                        if (p != null) {
                            routeDest = p
                        }
                    }

                    override fun onError(p0: Status?) {
                        errorSnackbar("Place selection error: ${p0}")
                    }
                })
            }
        }

        // init rest api instance
        rest = RestApi()

        // fetch networks
        rest.getNetworks({ data ->
            if (data != null) {
                val data_array = data.array()
                for (i in 0..(data_array.length() - 1)) {
                    val item = data_array.getJSONObject(i)
                    val id = item["network_id"] as Int
                    val name = item["network_name"] as String
                    gasNetworks.put(id, GasNetwork(id, name))
                }

                val netw_spinner = form.dialogView.findViewById<Spinner>(R.id.station_network)
                val adapter = object : ArrayAdapter<GasNetwork>(this@MapsActivity, R.layout.spinner_item,
                        gasNetworks.values.toList()) {
                    override fun getItemId(position: Int): Long {
                        return (getItem(position).network_id).toLong()
                    }
                }
                netw_spinner.adapter = adapter
            }
            this@MapsActivity.loader("off")
        }, { e ->
            Log.e("restApi", "getNetworksError: " + e.message)
            this@MapsActivity.loader("off"); errorSnackbar(e.message)
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    }

    private fun createGeoApiContext(): GeoApiContext {
        return GeoApiContext.Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.custom_menu, menu)
        mOptionsMenu = menu
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_stations -> {
            // User chose the "Settings" item, show the app settings UI...
            try {
                getLastKnownLocation()
                val msg = if (lastLocation == null) "none" else "${lastLocation.latitude}, ${lastLocation.longitude}"
                Toast.makeText(this@MapsActivity,
                        msg,
                        Toast.LENGTH_LONG).show()
                showStationsOnScreen()
            } catch (e: SecurityException) {
                Toast.makeText(this@MapsActivity,
                        e.message,
                        Toast.LENGTH_SHORT).show()
            }
            true
        }

        R.id.action_clusters -> {
            if (!item.isChecked) {
                showClusters()
                item.isChecked = true
            } else if (item.isChecked) {
                clearClusters()
                item.isChecked = false
            }
            true
        }

        R.id.action_update -> {
            if (item.isChecked) {
                stopLocationUpdates()
                item.isChecked = false
            } else if (!item.isChecked) {
                startLocationUpdates()
                item.isChecked = true
            }
            true
        }

        R.id.action_route -> {
            pickRoute()
            true
        }

        R.id.action_settings -> {

            true
        }

        R.id.action_clear -> {
            mMap.clear()
            true
        }

        R.id.action_new_station -> {
            // add new station
            form.open(null)
            true
        }

        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        checkPermission()
        mMap = googleMap

        try {
            mMap.isMyLocationEnabled = true

        } catch (e: SecurityException) {
            Toast.makeText(this@MapsActivity, e.message, Toast.LENGTH_SHORT).show()
        }

        mMap.setInfoWindowAdapter(StationInfoWindowAdapter(this@MapsActivity))
        mMap.setOnInfoWindowClickListener { marker: Marker? ->
            if (marker != null) {
                try {
                    val mData: JSONObject = marker.tag as JSONObject
                    val mPrice: JSONObject = mData["price"] as JSONObject

                    Log.i("gastracker", mData.toString())

                    val mapData = HashMap<String, Any>()
                    mapData["network_id"] = mData["network_id"]
                    mapData["hasPB95"] = mPrice.get("PB95") != null
                    mapData["hasON"] = mPrice.get("ON") != null
                    mapData["hasLPG"] = mPrice.get("LPG") != null

                    mapData["PB95"] = mPrice.getDouble("PB95")
                    mapData["ON"] = mPrice.getDouble("ON")
                    mapData["LPG"] = mPrice.getDouble("LPG")

                    form.open(mapData)
                } catch (e: Exception) {
                    Log.w("gastracker", e.message)
                }
            }
        }

        startLocationUpdates()



        if (mOptionsMenu != null && mOptionsMenu!!.findItem(R.id.action_clusters).isChecked)
            showClusters()
        else clearClusters()
    }

    fun showClusters(type: String = "Polygon") {
        loader("on")
        mOptionsMenu!!.findItem(R.id.action_update).isChecked = false
        stopLocationUpdates()


        rest.getClusters(bounding = type, resolve = { data ->
            if (data != null) {
                val llbuilder = LatLngBounds.Builder()
                val clusters = data.array()

                for (i in 0..(clusters.length() - 1)) {
                    val cluster = clusters.getJSONObject(i)
                    val coords = LatLng(cluster["lat"] as Double, cluster["lng"] as Double)
                    val bounding = cluster["bounding"] as JSONObject
                    val bcoordinates = bounding["coordinates"] as JSONArray
                    val btype = bounding["type"] as String
                    val bcoordsarray = (if (btype == "Polygon") {
                        bcoordinates.getJSONArray(0)
                    } else if (btype == "LineString") {
                        bcoordinates
                    } else if (btype == "Point") {
                        JSONArray().put(bcoordinates)
                    } else JSONArray())

                    val cluster_id = cluster["cluster_id"] as Int

                    llbuilder.include(coords)
                    val popts = PolygonOptions()
                            .fillColor(ResourcesCompat.getColor(getResources(), R.color.clusterFill, null))
                            .strokeColor(ResourcesCompat.getColor(getResources(), R.color.clusterBound, null))
                            .strokeWidth(3.0f)

                    for (i in 0..(bcoordsarray.length() - 1)) {
                        val pcoords = bcoordsarray.getJSONArray(i)
                        val latlng = LatLng(pcoords.getDouble(0), pcoords.getDouble(1))
                        popts.add(latlng)
                    }
                    val p = mMap.addPolygon(popts)
                    clusterPolygons.add(p)
                    val m = mMap.addMarker(MarkerOptions().position(coords))
                    cluster_centerMarkers.add(m)
                    m.setAnchor(0.5f, 0.5f)
                    m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.target_red))
                    p.tag = cluster
                }
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
            }
            this@MapsActivity.loader("off")
        }, error = { e ->
            this@MapsActivity.loader("off")
            errorSnackbar(e.message)
        })
    }

    fun clearClusters() {
        clusterPolygons.forEach { polygon: Polygon -> polygon.remove() }
        cluster_centerMarkers.forEach { marker: Marker -> marker.remove() }
    }

    fun pickRoute() {
        routeForm.open(null)
    }

    fun drawRoute(results: DirectionsResult) {
        clearRoute()
        try {
            routeMarkers.add(mMap.addMarker(MarkerOptions()
                    .position(LatLng(results.routes[0].legs[0].startLocation.lat, results.routes[0].legs[0].startLocation.lng))
                    .title(results.routes[0].legs[0].startAddress)))
            routeMarkers.add(mMap.addMarker(MarkerOptions()
                    .position(LatLng(results.routes[0].legs[0].endLocation.lat, results.routes[0].legs[0].endLocation.lng))
                    .title(results.routes[0].legs[0].startAddress)
                    .snippet("Time : ${results.routes[0].legs[0].duration.humanReadable}" +
                            " Distance : ${results.routes[0].legs[0].distance.humanReadable}")))
            val decodedPath = results.routes[0].overviewPolyline.decodePath()
            val popts = PolylineOptions()
                    .color(ResourcesCompat.getColor(getResources(), R.color.routeLine, null))
                    .jointType(JointType.ROUND)
                    .width(20.0f)

            decodedPath.forEach { latLng: com.google.maps.model.LatLng? ->
                if (latLng != null)
                    popts.add(LatLng(latLng.lat, latLng.lng))
            }
            routePolyline.add(mMap.addPolyline(popts))
        } catch (e: Exception) {
            errorSnackbar("Cannot draw route: missing data")
        }
    }

    fun clearRoute() {
        routePolyline.forEach { polyline: Polyline -> polyline.remove() }
        routeMarkers.forEach { marker: Marker -> marker.remove() }
        routePolyline.clear()
        routeMarkers.clear()
    }

    private var pendingLoader = 0
    @Synchronized
    fun loader(cmd: String) {
        if (cmd == "on") {
            this.findViewById<FrameLayout>(R.id.map_layer).alpha = 0.25f
            this.findViewById<ProgressBar>(R.id.loader).visibility = View.VISIBLE
            pendingLoader += 1

        } else if (cmd == "off") {
            if (pendingLoader < 2) {
                this.findViewById<FrameLayout>(R.id.map_layer).alpha = 1.0f
                this.findViewById<ProgressBar>(R.id.loader).visibility = View.INVISIBLE
            }
            pendingLoader -= 1
        }
        Log.i("gastracker_loader", "Toggle loader(${cmd}) [${pendingLoader}]: thread: ${Thread.currentThread()}")
    }


    fun errorSnackbar(message: String?) {

        val snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                if (!(message is String)) "Unknown error" else message,
                Snackbar.LENGTH_INDEFINITE
        )

        // Get the snack bar root view
        val snack_root_view = snackbar.view

        // Get the snack bar text view
        val snack_text_view = snack_root_view
                .findViewById<TextView>(android.support.design.R.id.snackbar_text)
        snack_text_view.maxLines = 10

        // Get the snack bar action view
        val snack_action_view = snack_root_view
                .findViewById<Button>(android.support.design.R.id.snackbar_action)

        // Change the snack bar root view background color
        snack_root_view.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.errorSnackbarColor, null))

        // Change the snack bar text view text color
        snack_text_view.setTextColor(Color.WHITE)

        // Change the snack bar action button text color
        snack_action_view.setTextColor(Color.WHITE)

        // Set an action for snack bar
        snackbar.setAction("Hide", {
            // Hide the snack bar
            snackbar.dismiss()
        })

        // Finally, display the snack bar
        snackbar.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            0 -> {
                if (grantResults.isNotEmpty())
                    Log.i("gasTracker", "Permissions granted")
            }
        }
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 0)
            return
        }
    }

    private fun getLastKnownLocation() {
        checkPermission()
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastLocation = location
            }

        }
    }

    private fun showClusterStations(currentLocation: LatLng, fuel: String) {
        mMap.clear()
        val llbuilder = LatLngBounds.Builder()

        mOptionsMenu!!.findItem(R.id.action_clusters).isChecked = false
        startLocationUpdates()

        try {
            // loader("on")

            llbuilder.include(currentLocation)

            rest.getClusterStations(currentLocation, fuel, { data ->
                if (data != null) {
                    val stations = data.obj()
                    val cheapestStations = stations.getJSONArray("cheapest_stations")

                    for (i in 0..(cheapestStations.length() - 1)) {
                        val station = cheapestStations.getJSONObject(i)
                        val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
                        val station_id = station["station_id"] as Int
                        val network_id = station["network_id"] as Int
                        val nname = gasNetworks.get(network_id)

                        llbuilder.include(coords)
                        val m = mMap.addMarker(MarkerOptions().position(coords))
                        m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                        m.tag = station
                        stationMarkers.add(m)
                    }

                    val station = stations.getJSONObject("closest_station")
                    val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
                    val station_id = station["station_id"] as Int
                    val network_id = station["network_id"] as Int
                    val nname = gasNetworks.get(network_id)

                    llbuilder.include(coords)
                    val m = mMap.addMarker(MarkerOptions().position(coords))
                    m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                    m.tag = station
                    stationMarkers.add(m)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }
                // this@MapsActivity.loader("off")
            }, { e -> this@MapsActivity.loader("off"); errorSnackbar(e.message) })

        } catch (e: Exception) {
            Toast.makeText(this@MapsActivity,
                    e.message,
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStationsOnScreen() {
        mMap.clear()
        val llbuilder = LatLngBounds.Builder()

        try {
            loader("on")

            val visibleRegion = mMap.projection.visibleRegion
            val radius = calculateVisibleRadius(visibleRegion)
            llbuilder.include(visibleRegion.latLngBounds.center)

            rest.getStationsFromRadius(radius, visibleRegion.latLngBounds.center, { data ->
                if (data != null) {
                    val stations = data.array()
                    for (i in 0..(stations.length() - 1)) {
                        val station = stations.getJSONObject(i)
                        val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
                        val station_id = station["station_id"] as Int
                        val network_id = station["network_id"] as Int
                        val nname = gasNetworks.get(network_id)

                        llbuilder.include(coords)
                        val m = mMap.addMarker(MarkerOptions().position(coords))
                        m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                        m.tag = station
                        stationMarkers.add(m)
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }
                this@MapsActivity.loader("off")
            }, { e -> this@MapsActivity.loader("off"); errorSnackbar(e.message) })

        } catch (e: Exception) {
            Toast.makeText(this@MapsActivity,
                    e.message,
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateVisibleRadius(visibleRegion: VisibleRegion): Double {
        var distanceWidth = FloatArray(1)
        var distanceHeight = FloatArray(1)

        val farRight: LatLng = visibleRegion.farRight
        val farLeft: LatLng = visibleRegion.farLeft
        val nearRight: LatLng = visibleRegion.nearRight
        val nearLeft: LatLng = visibleRegion.nearLeft

        //calculate the distance width (left <-> right of map on screen)
        Location.distanceBetween(
                (farLeft.latitude + nearLeft.latitude) / 2,
                farLeft.longitude,
                (farRight.latitude + nearRight.latitude) / 2,
                farRight.longitude,
                distanceWidth
        )

        //calculate the distance height (top <-> bottom of map on screen)
        Location.distanceBetween(
                farRight.latitude,
                (farRight.longitude + farLeft.longitude) / 2,
                nearRight.latitude,
                (nearRight.longitude + nearLeft.longitude) / 2,
                distanceHeight
        )

        //visible radius is (smaller distance) / 2:
        if (distanceHeight[0] < distanceWidth[0])
            return (distanceHeight[0] / 2).toDouble()
        else return (distanceWidth[0] / 2).toDouble()
    }

    private fun startLocationUpdates() {
        checkPermission()
        if (!locationUpdateState) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            locationUpdateState = true
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        var builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            startLocationUpdates()
            Log.i("locationUpdate", "Location updates started")
        }

        task.addOnFailureListener { e ->
            Log.e("locationUpdate", "Error while starting location updates: " + e.message)
        }
    }

    private fun stopLocationUpdates() {
        if (locationUpdateState) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdateState = false
        }
    }
}
