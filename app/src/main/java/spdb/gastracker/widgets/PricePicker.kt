package spdb.gastracker.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import spdb.gastracker.R
import kotlin.math.roundToInt


class PricePicker @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyle, defStyleRes) {

    val smallPrice: NumberPicker
    val bigPrice: NumberPicker

    init {
        LayoutInflater.from(context).inflate(R.layout.price_picker, this, true)

        orientation = VERTICAL

        smallPrice = findViewById<NumberPicker>(R.id.price_small)
        bigPrice = findViewById<NumberPicker>(R.id.price_big)

        smallPrice.minValue = 0
        smallPrice.maxValue = 99
        smallPrice.setFormatter { i ->  "${i}".padStart(2, '0')}

        bigPrice.minValue = 0
        bigPrice.maxValue = 99
    }

    fun getPrice(): Float {
        return bigPrice.value.toFloat() + smallPrice.value.toFloat() / 100.0f
    }

    fun setPrice(price: Float) {
        var big = price.toInt()
        var sm = ((price - big) * 100.0f).roundToInt()

        if (big > bigPrice.maxValue) big = bigPrice.maxValue
        if (big < bigPrice.minValue) big = bigPrice.minValue
        if (sm > smallPrice.maxValue) sm = smallPrice.maxValue
        if (sm < smallPrice.minValue) sm = smallPrice.minValue

        Log.i("prices", "Original price: ${price}, big: ${big}, small: ${sm}, diff: ${(price - big) * 100.0f}")

        smallPrice.value = sm
        bigPrice.value = big
    }

    override fun setEnabled(b: Boolean) {
        val alpha = if(b) 1.0f else 0.3f
        smallPrice.alpha = alpha
        bigPrice.alpha = alpha
        smallPrice.isEnabled = b
        bigPrice.isEnabled = b
    }
}