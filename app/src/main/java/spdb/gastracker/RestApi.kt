package spdb.gastracker

import android.app.Activity
import android.content.Context
import android.util.Log
import com.github.kittinunf.fuel.android.core.Json
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson


class RestApi(ctx: Context) {

    /** GET all networks*/
    fun getNetworks(resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        Log.i("restApi", "pobieram stacje")
        "/networks".httpGet().responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("restApi", "RestApi.getNetworks(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("restApi", "RestApi.getNetworks(ERR): ${err}")
                error(err)
            }
        })
    }

    /** GET /cluster_stations?lat=x&lng=y&fuel=z
     * cheapest stations and closest in cluster*/
    fun getClusterStations(position: LatLng, fuel: String, resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        Log.i("restApi", "pobieram stacje z klastra")
        "/cluster_stations".httpGet(listOf("lat" to position.latitude, "lng" to position.longitude, "fuel" to fuel)).responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("restApi", "RestApi.getClusterStations(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("restApi", "RestApi.getClusterStations(ERR): ${err}")
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

    /** GET /clusters?bounding=(Polygon | Circle) */
    fun getClusters(id: Long? = null, bounding: String? = "Polygon", resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        (if (id != null) "/clusters/${id}" else "/clusters").httpGet(
                if (bounding != null) listOf("bounding" to bounding) else listOf()

        ).responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.getClusters(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.getClusters(ERR): ${err}")
                error(err)
            }
        })
    }

    /** GET /prices?station_id=?&LPG=?&ON=?... */
    fun updatePrice(id: Long, prices: Map<String, Any>, resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        var params = listOf<Pair<String, Any>>("station_id" to id)
        params += prices.toList()

        "/prices".httpGet(params).responseJson({ request, response, result ->
            val (data, err) = result

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.updatePrice(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.updatePrice(ERR): ${err}")
                error(err)
            }
        })
    }

    /** GET /route_stations?maxdist=? */
    fun routeStations(route: MutableList<LatLng>, maxdist: Double, resolve: (data: Json?) -> Any, error: (error: FuelError) -> Any) {
        var params = listOf<Pair<String, Any>>("maxdist" to maxdist)

        var routeattr = arrayListOf<Array<Double>>()
        route.forEach { latLng: LatLng? ->
            if (latLng != null) {
                routeattr.add(arrayOf(latLng.latitude, latLng.longitude))
            }
        }

        var body = mapOf("route" to routeattr)

        "/route_stations".httpPost(params).body(Gson().toJson(body)).responseJson({ request, response, result ->
            val (data, err) = result

            Log.i("gastracker", "RestApi.routeStations(request): ${request.parameters}")

            if (err == null) {
                // success
                Log.i("gastracker", "RestApi.routeStations(OK): ${data.toString()}")
                resolve(data)
            } else {
                // error
                Log.e("gastracker", "RestApi.routeStations(ERR): ${err} Body: ${Gson().toJson(body)}")
                error(err)
            }
        })
    }

    init {
        FuelManager.instance.basePath = ctx.resources.getString(R.string.heroku_rest_api_url)
    }
}