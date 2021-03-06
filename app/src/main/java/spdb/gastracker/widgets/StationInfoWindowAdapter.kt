package spdb.gastracker.widgets

import android.content.Context
import android.view.View
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import android.app.Activity
import android.util.Log
import android.widget.TextView
import org.json.JSONObject
import spdb.gastracker.R
import java.text.SimpleDateFormat
import java.util.*


class StationInfoWindowAdapter(context: Context) : GoogleMap.InfoWindowAdapter {

    val ctx: Context

    override fun getInfoContents(p0: Marker?): View? {
        val view = (ctx as Activity).layoutInflater
                .inflate(R.layout.station_info_window, null)

        val mData = if(p0 != null && p0.tag is JSONObject) p0.tag as JSONObject else null
        if (mData != null) {
            val nname = mData["network_name"] as String
            val timestamp = mData.optDouble("updated", -1.0)
            Log.i("timestamp:gastracker", "hastimestamp: ${timestamp > -1.0}, timestamp: ${mData["updated"]}")
            val nid = mData["station_id"] as Int
            val price = mData["price"] as JSONObject
            view.findViewById<TextView>(R.id.title).text = "${nname} (id: ${nid})"
            view.findViewById<TextView>(R.id.pb_price).text = "%.2f PLN".format(price["PB95"])
            view.findViewById<TextView>(R.id.on_price).text = "%.2f PLN".format(price["ON"])
            view.findViewById<TextView>(R.id.lpg_price).text = "%.2f PLN".format(price["LPG"])

            view.findViewById<TextView>(R.id.date).text = if(timestamp > 0.0) {
                SimpleDateFormat("d MMM yyyy\nHH:mm:ss", Locale.getDefault()).format(Date((1000.0 * timestamp).toLong()))
            } else {
                "never"
            }
            return view
        }
        return null
    }

    override fun getInfoWindow(p0: Marker?): View? {
        return null
    }

    init {
        ctx = context
    }
}