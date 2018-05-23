package spdb.gastracker

import android.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import spdb.gastracker.utils.DialogForm
import spdb.gastracker.widgets.PricePicker
import com.google.android.gms.maps.model.LatLngBounds
import spdb.gastracker.widgets.StationInfoWindowAdapter


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
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

        // init form
        form = object: DialogForm(this@MapsActivity, R.layout.station_form, "Add station", mapOf(
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
        }, {})
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.custom_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            // User chose the "Settings" item, show the app settings UI...
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

        mMap.setInfoWindowAdapter(StationInfoWindowAdapter(this@MapsActivity))

        val llbuilder = LatLngBounds.Builder()

        val sampleStations = 1..10
        for (id in sampleStations) {
            rest.getStation(id.toLong(), { data ->
                if (data != null) {
                    val json = data.obj()
                    val coords = LatLng(json["lat"] as Double, json["lng"] as Double)
                    val station_id = json["station_id"] as Int
                    val network_id = json["network_id"] as Int
                    val nname = gasNetworks.get(network_id)

                    llbuilder.include(coords)
                    val m = mMap.addMarker(MarkerOptions().position(coords))
                    m.tag = json
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }
            }, {})
        }

    }
}
