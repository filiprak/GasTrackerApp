package spdb.gastracker.tasks

import android.support.v4.content.res.ResourcesCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import org.json.JSONArray
import org.json.JSONObject
import spdb.gastracker.MapsActivity
import spdb.gastracker.R
import spdb.gastracker.RestApi
import spdb.gastracker.utils.GasTrackerTask

/**
 * Created by raqu on 6/5/18.
 */
class ShowClustersTask(override var activity: MapsActivity, override var mMap: GoogleMap, override var rest: RestApi) : GasTrackerTask {
    // map data cache
    private var clusterPolygons: MutableList<Polygon> = mutableListOf<Polygon>()
    private var cluster_centerMarkers: MutableList<Marker> = mutableListOf<Marker>()

    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        activity.loader("on")
        activity.mOptionsMenu!!.findItem(R.id.action_update).isChecked = false
        activity.stopLocationUpdates()


        rest.getClusters(bounding = (if (p0 != null) p0 as String else "Polygon"), resolve = { data ->
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
                            .fillColor(ResourcesCompat.getColor(activity.getResources(), R.color.clusterFill, null))
                            .strokeColor(ResourcesCompat.getColor(activity.getResources(), R.color.clusterBound, null))
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
            activity.loader("off")
        }, error = { e ->
            activity.loader("off")
            activity.snackbar(message=e.message)
        })
    }

    override fun clean(p0: Any?) {
        clusterPolygons.forEach { polygon: Polygon -> polygon.remove() }
        cluster_centerMarkers.forEach { marker: Marker -> marker.remove() }
    }

}