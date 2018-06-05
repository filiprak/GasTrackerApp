package spdb.gastracker.tasks

import android.app.AlertDialog
import android.support.v4.content.res.ResourcesCompat
import android.util.Log
import android.view.View
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment
import com.google.android.gms.location.places.ui.PlaceSelectionListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import spdb.gastracker.MapsActivity
import spdb.gastracker.R
import spdb.gastracker.RestApi
import spdb.gastracker.utils.DialogForm
import spdb.gastracker.utils.GasTrackerTask

/**
 * Created by raqu on 6/5/18.
 */
class NewRouteTask(override var activity: MapsActivity, override var mMap: GoogleMap, override var rest: RestApi) : GasTrackerTask {

    private lateinit var routeForm: DialogForm
    var geoApiContext: GeoApiContext? = null

    var routeOrigin: Place? = null
    var routeDest: Place? = null
    private var routeMarkers: MutableList<Marker> = mutableListOf<Marker>()
    private var routePolyline: MutableList<Polyline> = mutableListOf<Polyline>()

    override fun prepare(p0: Any?, p1: Any?, p2: Any?) {
        // route form
        routeForm = object : DialogForm(activity, R.layout.route_form, "New route", mapOf()) {
            override fun success(data: Map<String, Any>) {
                if (routeOrigin == null || routeDest == null) {
                    activity.snackbar(message="Please enter route origin and destination")
                    return
                }

                activity.loader("on")
                if (geoApiContext == null) geoApiContext = createGeoApiContext()
                DirectionsApi.newRequest(geoApiContext)
                        .mode(TravelMode.DRIVING)
                        .origin(com.google.maps.model.LatLng(routeOrigin!!.latLng.latitude, routeOrigin!!.latLng.longitude))
                        .destination(com.google.maps.model.LatLng(routeDest!!.latLng.latitude, routeDest!!.latLng.longitude))
                        .setCallback(object : com.google.maps.PendingResult.Callback<DirectionsResult> {
                            override fun onFailure(e: Throwable?) {
                                activity.runOnUiThread {
                                    activity.snackbar(message="Failed to create route: ${e?.message}")
                                    activity.loader("off")
                                }
                            }

                            override fun onResult(result: DirectionsResult?) {
                                if (result != null) {
                                    // draw route
                                    activity.runOnUiThread {
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
                                        activity.loader("off")
                                    }
                                }
                            }

                        })

                Log.i("gastracker", "Route: origin: ${routeOrigin?.address}, dest: ${routeDest?.address}")
            }

            override fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {
                val originplace = activity.fragmentManager.findFragmentById(R.id.origin_fragment) as PlaceAutocompleteFragment
                val destplace = activity.fragmentManager.findFragmentById(R.id.dest_fragment) as PlaceAutocompleteFragment

                originplace.setOnPlaceSelectedListener(object : PlaceSelectionListener {
                    override fun onPlaceSelected(p: Place?) {
                        if (p != null) {
                            routeOrigin = p
                        }
                    }

                    override fun onError(p0: Status?) {
                        activity.snackbar(message="Place selection error: ${p0}")
                    }
                })
                destplace.setOnPlaceSelectedListener(object : PlaceSelectionListener {
                    override fun onPlaceSelected(p: Place?) {
                        if (p != null) {
                            routeDest = p
                        }
                    }

                    override fun onError(p0: Status?) {
                        activity.snackbar(message="Place selection error: ${p0}")
                    }
                })
            }
        }
    }

    override fun start(p0: Any?, p1: Any?, p2: Any?) {
        pickRoute()
    }

    override fun clean(p0: Any?) {
        routePolyline.forEach { polyline: Polyline -> polyline.remove() }
        routeMarkers.forEach { marker: Marker -> marker.remove() }
        routePolyline.clear()
        routeMarkers.clear()
    }

    fun drawRoute(results: DirectionsResult) {
        this.clean()
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
                    .color(ResourcesCompat.getColor(activity.getResources(), R.color.routeLine, null))
                    .jointType(JointType.ROUND)
                    .width(20.0f)

            decodedPath.forEach { latLng: com.google.maps.model.LatLng? ->
                if (latLng != null)
                    popts.add(LatLng(latLng.lat, latLng.lng))
            }
            routePolyline.add(mMap.addPolyline(popts))
        } catch (e: Exception) {
            activity.snackbar(message="Cannot draw route: missing data")
        }
    }

    private fun createGeoApiContext(): GeoApiContext {
        return GeoApiContext.Builder()
                .apiKey(activity.getString(R.string.google_maps_key))
                .build()
    }

    fun pickRoute() {
        routeForm.open(null)
    }

}