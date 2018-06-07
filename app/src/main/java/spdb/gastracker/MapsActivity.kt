package spdb.gastracker

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
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import spdb.gastracker.utils.DialogForm
import spdb.gastracker.widgets.PricePicker
import org.json.JSONObject
import spdb.gastracker.tasks.CheapestClosestTask
import spdb.gastracker.tasks.NewRouteTask
import spdb.gastracker.tasks.ShowClustersTask
import spdb.gastracker.tasks.ShowStationsTask
import spdb.gastracker.widgets.StationInfoWindowAdapter


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var lastLocation = Location("kappa")
    lateinit var locationCallback: LocationCallback
    lateinit var locationRequest: LocationRequest
    var locationUpdateState = false

    var mOptionsMenu: Menu? = null

    private lateinit var form: DialogForm

    var gasNetworks: HashMap<Int, GasNetwork> = hashMapOf()

    private lateinit var rest: RestApi

    private val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)


    /* activity tasks refactored*/
    private lateinit var cheapestClosestTask: CheapestClosestTask
    private lateinit var showStationsTask: ShowStationsTask
    private lateinit var showClustersTask: ShowClustersTask
    private lateinit var newRouteTask: NewRouteTask


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loader("on")
        Log.i("onCreate", "Odpala sie")

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
            }
        }


        // init rest api instance
        rest = RestApi(this@MapsActivity)

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
            this@MapsActivity.loader("off"); snackbar(message=e.message)
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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
                showStationsTask.start()

            } catch (e: SecurityException) {
                snackbar(message = e.message)
            }
            true
        }

        R.id.action_clusters -> {
            if (!item.isChecked) {
                showClustersTask.start()
                item.isChecked = true
            } else if (item.isChecked) {
                showClustersTask.clean()
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
            newRouteTask.start()
            true
        }

        R.id.action_fuelType -> {
            cheapestClosestTask.openFuelDialog()
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

                    val mapData = HashMap<String, Any>()
                    mapData["network_id"] = mData["network_id"]
                    mapData["hasPB95"] = mPrice.get("PB95") != null
                    mapData["hasON"] = mPrice.get("ON") != null
                    mapData["hasLPG"] = mPrice.get("LPG") != null

                    mapData["PB95"] = mPrice.getDouble("PB95")
                    mapData["ON"] = mPrice.getDouble("ON")
                    mapData["LPG"] = mPrice.getDouble("LPG")

                    form.open(mapData, { data ->
                        val filtered = data!!.filterKeys { s: String -> s in arrayListOf("LPG", "ON", "PB95")  }
                        rest.updatePrice(mData.getLong("station_id"), filtered, { response ->
                            if (response != null) {
                                val jsondata = response.obj()
                                mData.putOpt("PB95", jsondata.optDouble("PB95"))
                                mData.putOpt("ON", jsondata.optDouble("ON"))
                                mData.putOpt("LPG", jsondata.optDouble("LPG"))
                            }

                            snackbar(type="ok", message="Prices updated successfully")
                        }, { err ->
                            snackbar(message="Error when updating price: ${err.message}")
                        })
                    })
                } catch (e: Exception) {
                    snackbar(type="error", message=e.message)
                    Log.w("gastracker", e.message)
                }
            }
        }

        // init tasks
        cheapestClosestTask = CheapestClosestTask(this@MapsActivity, mMap, rest)
        cheapestClosestTask.prepare()
        cheapestClosestTask.openFuelDialog()
        newRouteTask = NewRouteTask(this@MapsActivity, mMap, rest)
        newRouteTask.prepare()
        showClustersTask = ShowClustersTask(this@MapsActivity, mMap, rest)
        showClustersTask.prepare()
        showStationsTask = ShowStationsTask(this@MapsActivity, mMap, rest)
        showStationsTask.prepare()

        // init location handling
        lastLocation.latitude = 52.23
        lastLocation.longitude = 21.01
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                lastLocation = p0?.lastLocation ?: lastLocation
                val currentPosition = LatLng(lastLocation.latitude, lastLocation.longitude)
                cheapestClosestTask.start(currentPosition)
            }
        }
        createLocationRequest()
        startLocationUpdates()

        if (mOptionsMenu != null && mOptionsMenu!!.findItem(R.id.action_clusters).isChecked)
            showStationsTask.start()
        else showClustersTask.clean()
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

    fun snackbar(type: String = "error", message: String?) {

        val snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                if (!(message is String)) "No message" else message,
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
        val color = if(type == "error") R.color.errorSnackbarColor else if(type == "ok") R.color.okSnackbarColor else 0
        snack_root_view.setBackgroundColor(ResourcesCompat.getColor(getResources(), color, null))

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

    fun startLocationUpdates() {
        checkPermission()
        if (!locationUpdateState) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            locationUpdateState = true
        }
    }

    fun stopLocationUpdates() {
        if (locationUpdateState) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdateState = false
        }
    }
}
