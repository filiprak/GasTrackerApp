package spdb.gastracker.widgets

import android.content.Context
import android.view.View
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import android.app.Activity
import android.widget.TextView
import org.json.JSONObject
import spdb.gastracker.R


class StationInfoWindowAdapter(context: Context) : GoogleMap.InfoWindowAdapter {

    val ctx: Context


    override fun getInfoContents(p0: Marker?): View {
        val view = (ctx as Activity).layoutInflater
                .inflate(R.layout.station_info_window, null)

        val mData = if(p0 != null) p0.tag as JSONObject else null
        if (mData != null) {
            val nname = mData["network_name"] as String
            val nid = mData["station_id"] as Int
            val price = mData["price"] as JSONObject
            view.findViewById<TextView>(R.id.title).text = "${nname} (id: ${nid})"
            view.findViewById<TextView>(R.id.pb_price).text = "%.2f PLN".format(price["PB95"])
            view.findViewById<TextView>(R.id.on_price).text = "%.2f PLN".format(price["ON"])
            view.findViewById<TextView>(R.id.lpg_price).text = "%.2f PLN".format(price["LPG"])
        }
        return view
    }

    override fun getInfoWindow(p0: Marker?): View? {
        return null
    }

    init {
        ctx = context
    }
}