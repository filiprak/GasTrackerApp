package spdb.gastracker

import android.util.Log
import com.github.kittinunf.fuel.android.core.Json
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import com.google.android.gms.maps.model.LatLng


class RestApi() {

    val serverBaseUrl = "https://gas-tracker-app.herokuapp.com"

    /** GET all networks*/
    fun getNetworks(resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        "/networks".httpGet().responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.getNetworks(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.getNetworks(ERR): ${err}")
                error(err)
            }
        })
    }

    /** GET /station/<id> */
    fun getStation(id: Long, resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        "/stations/${id}".httpGet().responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.getStation(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.getStation(ERR): ${err}")
                error(err)
            }
        })
    }

    /** GET /stations?radius=xxx&lat=a.aaa&lng=b.bbb */
    fun getStationsFromRadius(radius: Double, middle: LatLng, resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        "/stations".httpGet(listOf(
            "radius" to radius, "lat" to middle.latitude, "lng" to middle.longitude
        )).responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.getStationsFromRadius(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.getStationsFromRadius(ERR): ${err}")
                error(err)
            }
        })
    }

    init {
        FuelManager.instance.basePath = serverBaseUrl
    }
}