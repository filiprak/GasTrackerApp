package spdb.gastracker.tasks

import android.content.Context
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import spdb.gastracker.RestApi
import spdb.gastracker.utils.GasTrackerTask
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import spdb.gastracker.MapsActivity
import spdb.gastracker.R

/**
 * Created by raqu on 6/5/18.
 */
class CheapestClosestTask(override var activity: MapsActivity, override var mMap: GoogleMap, override var rest: RestApi) : GasTrackerTask {

    var fuel: String = "LPG"
    var stationMarkers: MutableList<Marker> = mutableListOf()


    override fun prepare(p0: Any?, p1: Any?, p2: Any?) {

    }

    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        val currentLocation: LatLng = p0 as LatLng

        mMap.clear()
        val llbuilder = LatLngBounds.Builder()

        activity.mOptionsMenu!!.findItem(R.id.action_clusters).isChecked = false
        activity.startLocationUpdates()

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
                        val nname = activity.gasNetworks.get(network_id)
                        station.put("network_name", if(nname == null) "None" else nname.network_name)

                        llbuilder.include(coords)
                        val m = mMap.addMarker(MarkerOptions().position(coords))
                        m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_green))
                        m.tag = station
                        stationMarkers.add(m)
                    }

                    val station = stations.getJSONObject("closest_station")
                    val coords = LatLng(station["lat"] as Double, station["lng"] as Double)
                    val station_id = station["station_id"] as Int
                    val network_id = station["network_id"] as Int
                    val nname = activity.gasNetworks.get(network_id)
                    station.put("network_name", if(nname == null) "None" else nname.network_name)

                    llbuilder.include(coords)
                    val m = mMap.addMarker(MarkerOptions().position(coords))
                    m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red))
                    m.tag = station
                    stationMarkers.add(m)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }

            }, { e -> activity.loader("off"); activity.snackbar(message=e.message) })

        } catch (e: Exception) {
            activity.loader("off")
            activity.snackbar(type = "error", message = e.message)
        }
    }

    override fun clean(p0: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}