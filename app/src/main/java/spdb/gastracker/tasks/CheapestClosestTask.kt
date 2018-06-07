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
import com.google.gson.Gson
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
    var markers = mutableMapOf<Long, Pair<Marker, SType>>()
    lateinit var fuelTypeDialog: DialogForm

    enum class SType { CLOSEST, CHEAPEST, SHARED }


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

    private fun updateMarkers(cheapest: JSONArray, closest: JSONArray) {
        var swapMarkers = mutableMapOf<Long, Pair<Marker, SType>>()

        val shared = sharedStations(cheapest, closest)

        for (i in 0..(cheapest.length() - 1)) {
            val station = cheapest.getJSONObject(i)
            val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
            val station_id = station.optLong("station_id")
            val network_id = station["network_id"] as Int
            val updated = station.optLong("updated", 0)
            val nname = activity.gasNetworks.get(network_id)
            station.put("network_name", if (nname == null) "None" else nname.network_name)

            var stype = if (shared.contains(station_id)) SType.SHARED else SType.CHEAPEST

            // find exising station
            val existing = markers.get(station_id)
            if (existing == null
                    || updated > (existing.first.tag as JSONObject).optLong("updated", 0)
                    || existing.second != stype) {
                //replace marker
                val m = mMap.addMarker(MarkerOptions().position(coords))
                val iconid = if(stype == SType.SHARED) R.drawable.marker_shared else R.drawable.marker_green
                m.setIcon(BitmapDescriptorFactory.fromResource(iconid))
                m.tag = station

                // remove old marker if changed
                if (existing != null) existing.first.remove()
                swapMarkers.set(station_id, Pair(m, stype))
            } else {
                swapMarkers.set(station_id, existing)
            }
        }

        for (i in 0..(closest.length() - 1)) {
            val station = closest.getJSONObject(i)
            val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
            val station_id = station.optLong("station_id")
            val network_id = station["network_id"] as Int
            val updated = station.optLong("updated", 0)
            val nname = activity.gasNetworks.get(network_id)
            station.put("network_name", if (nname == null) "None" else nname.network_name)

            var stype = if (shared.contains(station_id)) SType.SHARED else SType.CLOSEST

            // avoid duplicating shared markers
            if (stype == SType.SHARED) continue

            // find exising station
            val existing = markers.get(station_id)
            if (existing == null || updated > (existing.first.tag as JSONObject).optLong("updated", 0) || existing.second != stype) {
                //replace marker
                val m = mMap.addMarker(MarkerOptions().position(coords))
                val iconid = if(stype == SType.SHARED) R.drawable.marker_shared else R.drawable.marker_red
                m.setIcon(BitmapDescriptorFactory.fromResource(iconid))
                m.tag = station

                // remove old marker if changed
                if (existing != null) existing.first.remove()
                swapMarkers.set(station_id, Pair(m, stype))
            } else {
                swapMarkers.set(station_id, existing)
            }
        }

        Log.i("markers", "markers____: ${Gson().toJson(markers.keys)}")
        Log.i("markers", "swapMarkers: ${Gson().toJson(swapMarkers.keys)}")

        for ((id, pair) in markers) {
            if (swapMarkers.get(id) == null) pair.first.remove()
        }

        markers = swapMarkers
    }

    private fun sharedStations(a: JSONArray, b: JSONArray): MutableList<Long> {
        val result: MutableList<Long> = mutableListOf()
        val aids: MutableList<Long> = mutableListOf()

        for (i in 0..a.length() - 1) {
            val station = a.optJSONObject(i)
            aids.add(i, station.optLong("station_id"))
        }
        for (i in 0..b.length() - 1) {
            val station = b.optJSONObject(i)
            val id = station.optLong("station_id")
            if (aids.contains(id)) result.add(id)
        }
        return result
    }

    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        val currentLocation: LatLng = p0 as LatLng

        activity.mOptionsMenu!!.findItem(R.id.action_clusters).isChecked = false

        try {

            rest.getClusterStations(currentLocation, fuelType, { data ->
                if (data != null) {
                    val stations = data.obj()
                    val cheapestStations = stations.getJSONArray("cheapest_stations")
                    val closestStations = stations.getJSONArray("closest_stations")

                    updateMarkers(cheapestStations, closestStations)

                    val llbuilder = LatLngBounds.Builder()
                    for ((id, pair) in markers) {
                        llbuilder.include(pair.first.position)
                    }
                    llbuilder.include(currentLocation)

                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 200))

                } else activity.snackbar(message = "No data provided in network response")

            }, { e -> activity.snackbar(message = e.message) })

        } catch (e: Exception) {
            activity.snackbar(type = "error", message = e.message)
        }
    }

    override fun clean(p0: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}