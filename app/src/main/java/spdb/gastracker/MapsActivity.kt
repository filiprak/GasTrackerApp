package spdb.gastracker

import android.app.AlertDialog
import android.content.Context
import android.location.LocationManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import spdb.gastracker.utils.DialogForm
import spdb.gastracker.widgets.PricePicker
import org.json.JSONObject
import spdb.gastracker.widgets.StationInfoWindowAdapter


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var locManager: LocationManager

    private lateinit var form: DialogForm

    private var gasNetworks: HashMap<Int, GasNetwork> = hashMapOf()

    private lateinit var rest: RestApi


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loader("on")

        locManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // init form
        form = object: DialogForm(this@MapsActivity, R.layout.station_form, "Gas station", mapOf(
                "network_id" to R.id.station_network,
                "PB95" to R.id.pb_price,
                "ON" to R.id.on_price,
                "LPG" to R.id.lpg_price,

                "hasPB95" to R.id.pb_checkBox,
                "hasON" to R.id.on_checkBox,
                "hasLPG" to R.id.lpg_checkBox
        )) {
            override fun success(data: Map<String, Any>) {
                Log.i("gastracker", Gson().toJson(data));
            }
            override fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {
                val pb_control = view.findViewById<PricePicker>(R.id.pb_price)
                val on_control = view.findViewById<PricePicker>(R.id.on_price);
                val lpg_control = view.findViewById<PricePicker>(R.id.lpg_price);

                pb_control.isEnabled = false
                on_control.isEnabled = false
                lpg_control.isEnabled = false

                view.findViewById<CheckBox>(R.id.pb_checkBox).setOnCheckedChangeListener({ compoundButton, b ->
                    pb_control.isEnabled = b
                })
                view.findViewById<CheckBox>(R.id.on_checkBox).setOnCheckedChangeListener({
                    compoundButton, b -> on_control.isEnabled = b
                })
                view.findViewById<CheckBox>(R.id.lpg_checkBox).setOnCheckedChangeListener({
                    compoundButton, b -> lpg_control.isEnabled = b
                })

                // add networks
                val netw_spinner = view.findViewById<Spinner>(R.id.station_network)

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
                val adapter = object: ArrayAdapter<GasNetwork>(this@MapsActivity, R.layout.spinner_item,
                        gasNetworks.values.toList()) {
                    override fun getItemId(position: Int): Long {
                        return (getItem(position).network_id).toLong()
                    }
                }
                netw_spinner.adapter = adapter
            }
            this@MapsActivity.loader("off")
        }, { this@MapsActivity.loader("off") })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.custom_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_latlng -> {
            // User chose the "Settings" item, show the app settings UI...
            try {
                val loc = locManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                val msg = if(loc == null) "none" else "${loc.latitude}, ${loc.longitude}"
                Toast.makeText(this@MapsActivity,
                        msg,
                        Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                Toast.makeText(this@MapsActivity,
                        e.message,
                        Toast.LENGTH_SHORT).show()
            }
            true
        }

        R.id.action_settings -> {

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
        mMap = googleMap

        try {
            mMap.setMyLocationEnabled(true)

        } catch (e: SecurityException) {
            Toast.makeText(this@MapsActivity, e.message, Toast.LENGTH_SHORT).show()
        }

        mMap.setInfoWindowAdapter(StationInfoWindowAdapter(this@MapsActivity))
        mMap.setOnInfoWindowClickListener { marker: Marker? ->
            if (marker != null) {
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
            }
        }

        val llbuilder = LatLngBounds.Builder()

        try {
            loader("on")
            val loc = locManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            rest.getStationsFromRadius(10000.0, LatLng(loc.latitude, loc.longitude), { data ->
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
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }
                this@MapsActivity.loader("off")
            }, { this@MapsActivity.loader("off") })

        } catch (e: SecurityException) {
            Toast.makeText(this@MapsActivity,
                    e.message,
                    Toast.LENGTH_SHORT).show()
        }

    }

    private var pendingLoader = 0
    @Synchronized fun loader(cmd: String) {
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
}
