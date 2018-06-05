package spdb.gastracker.tasks

import android.location.Location
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import spdb.gastracker.MapsActivity
import spdb.gastracker.R
import spdb.gastracker.RestApi
import spdb.gastracker.utils.GasTrackerTask

/**
 * Created by raqu on 6/5/18.
 */
class ShowStationsTask(override var activity: MapsActivity, override var mMap: GoogleMap, override var rest: RestApi) : GasTrackerTask{

    var stationMarkers: MutableList<Marker> = mutableListOf()


    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        mMap.clear()
        val llbuilder = LatLngBounds.Builder()

        try {
            activity.loader("on")

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
                        val nname = activity.gasNetworks.get(network_id)
                        station.put("network_name", if(nname == null) "None" else nname.network_name)

                        llbuilder.include(coords)
                        val m = mMap.addMarker(MarkerOptions().position(coords))
                        m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_blue))
                        m.tag = station
                        stationMarkers.add(m)
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100))
                }
                activity.loader("off")
            }, { e -> activity.loader("off"); activity.snackbar(message=e.message) })

        } catch (e: Exception) {
            activity.loader("off")
            activity.snackbar(message=e.message)
        }
    }

    override fun clean(p0: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

}