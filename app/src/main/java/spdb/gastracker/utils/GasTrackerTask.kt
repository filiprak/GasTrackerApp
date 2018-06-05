package spdb.gastracker.utils

import com.google.android.gms.maps.GoogleMap
import spdb.gastracker.MapsActivity
import spdb.gastracker.RestApi

/**
 * Created by raqu on 6/5/18.
 */
interface GasTrackerTask {
    var mMap: GoogleMap
    var activity: MapsActivity
    var rest: RestApi

    fun prepare(p0: Any? = null, p1: Any? = null, p2: Any? = null) { }

    fun start(p0: Any? = null, p1: Any? = null, p2: Any? = null)
    fun clean(p0: Any? = null)
}