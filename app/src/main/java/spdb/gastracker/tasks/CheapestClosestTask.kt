package spdb.gastracker.tasks

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.gms.maps.CameraUpdateFactory
import spdb.gastracker.RestApi
import spdb.gastracker.utils.GasTrackerTask
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import org.json.JSONArray
import org.json.JSONObject
import spdb.gastracker.MapsActivity
import spdb.gastracker.R
import spdb.gastracker.utils.DialogForm
import java.util.function.Consumer

/**
 * Created by raqu on 6/5/18.
 */
class CheapestClosestTask(override var activity: MapsActivity, override var mMap: GoogleMap, override var rest: RestApi) : GasTrackerTask {

    var fuelType = "PB95"
    var cheapestMarkers: MutableList<Marker> = mutableListOf()
    var closestMarkers: MutableList<Marker> = mutableListOf()
    lateinit var fuelTypeDialog: DialogForm
    var previousHashCheap: Int = 0
    var previousHashClose: Int = 0
    var isFirst: Boolean = true

    override fun prepare(p0: Any?, p1: Any?, p2: Any?) {

        fuelTypeDialog = object : DialogForm(activity, R.layout.fuel_type, "Select fuel type", mapOf(
                "PB95" to R.id.pb95_check,
                "ON" to R.id.on_check,
                "LPG" to R.id.lpg_check
        )) {
            override fun success(data: Map<String, Any>) {
                Log.i("fuelType", "Success")
                val radioGroup = dialogView.findViewById<RadioGroup>(R.id.fuel_radio)
                val checkedID = radioGroup.checkedRadioButtonId
                val radioView = radioGroup.findViewById<View>(checkedID)
                val radio = radioGroup.getChildAt(radioGroup.indexOfChild(radioView)) as RadioButton
                fuelType = radio.text.toString()
            }

            override fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {

            }
        }
    }

    public fun openFuelDialog() {
        fuelTypeDialog.open(emptyMap())
    }


    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        val currentLocation: LatLng = p0 as LatLng

        val llbuilder = LatLngBounds.Builder()

        activity.mOptionsMenu!!.findItem(R.id.action_clusters).isChecked = false
        //activity.startLocationUpdates()

        try {
            // loader("on")

            rest.getClusterStations(currentLocation, fuelType, { data ->
                if (data != null) {
                    val stations = data.obj()
                    val cheapestStations = stations.getJSONArray("cheapest_stations")
                    val closestStations = stations.getJSONArray("closest_stations")

                    if (previousHashCheap != cheapestStations.toString().hashCode()) {
                        mMap.clear()
                        previousHashCheap = cheapestStations.toString().hashCode()
                        Log.i("Hash", previousHashCheap.toString())

                        var cheapMarker: Marker? = null
                        if (isFirst == false)
                            cheapMarker = cheapestMarkers.lastOrNull()

                        cheapestMarkers.clear()

                        for (i in 0..(cheapestStations.length() - 1)) {
                            val station = cheapestStations.getJSONObject(i)
                            val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
                            val station_id = station["station_id"] as Int
                            val network_id = station["network_id"] as Int
                            val nname = activity.gasNetworks.get(network_id)
                            station.put("network_name", if (nname == null) "None" else nname.network_name)

                            val m = mMap.addMarker(MarkerOptions().position(coords))
                            m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_green))
                            m.tag = station
                            cheapestMarkers.add(m)
                        }
                        if (cheapMarker != null) cheapestMarkers.add(cheapMarker)
                    }
                        if (previousHashClose != closestStations.toString().hashCode()) {

<<<<<<< HEAD
                            previousHashClose = closestStations.toString().hashCode()
                            if (isFirst == false) {
                                closestMarkers.forEach { marker: Marker -> marker.remove() }
                                closestMarkers.clear()
                            }

                            for (i in 0..(closestStations.length() - 1)) {
                                val closestStation = closestStations.getJSONObject(i)
                                val coords = LatLng(closestStation["lat"] as Double, closestStation["lng"] as Double)
                                val station_id = closestStation["station_id"] as Int
                                val network_id = closestStation["network_id"] as Int
                                val nname = activity.gasNetworks.get(network_id)
                                closestStation.put("network_name", if (nname == null) "None" else nname.network_name)

                                val m = mMap.addMarker(MarkerOptions().position(coords))
                                m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red))
                                m.tag = closestStation
                                closestMarkers.add(m)
                            }
                        }
                        isFirst = false
                        closestMarkers.forEach { marker: Marker -> llbuilder.include(marker.position) }
                        llbuilder.include(currentLocation)
=======
                    if (previousHashClose != closestStation.toString().hashCode()) {

                        previousHashClose = closestStation.toString().hashCode()
                        if (isFirst == false) {
                            stationMarkers.last().remove()
                            stationMarkers.removeAt(stationMarkers.lastIndex)
                        }

                        val coords = LatLng(closestStation["lat"] as Double, closestStation["lng"] as Double)
                        val station_id = closestStation["station_id"] as Int
                        val network_id = closestStation["network_id"] as Int
                        val nname = activity.gasNetworks.get(network_id)
                        closestStation.put("network_name", if (nname == null) "None" else nname.network_name)

                        val m = mMap.addMarker(MarkerOptions().position(coords))
                        m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red))
                        m.tag = closestStation
                        stationMarkers.add(m)
                    }
                    isFirst = false
                    stationMarkers.forEach { marker: Marker -> llbuilder.include(marker.position) }
                    llbuilder.include(currentLocation)
>>>>>>> cluster stations change

                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))


                }

            }, { e -> activity.loader("off"); activity.snackbar(message = e.message) })

        } catch (e: Exception) {
            activity.loader("off")
            activity.snackbar(type = "error", message = e.message)
        }
    }

    override fun clean(p0: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    public fun reset() {
        previousHashClose = 0
        previousHashCheap = 0
        isFirst = true
    }

}