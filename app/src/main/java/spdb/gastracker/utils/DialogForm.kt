package spdb.gastracker.utils

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.NumberPicker
import com.google.gson.Gson
import spdb.gastracker.widgets.PricePicker
import android.view.ViewGroup
import android.widget.Spinner
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment


open class DialogForm(context: Context, layout: Int, title: String, schema: Map<String, Int>) {

    val dialogView: View
    val builder: AlertDialog.Builder

    val sch: Map<String, Int>

    var successCallback: ((data: Map<String, Any>?)->Any)? = null


    fun open(data: Map<String, Any>?, callback: (data: Map<String, Any>?)->Any = {}) {
        if (data != null) {
            for ((attr, value) in data) {
                val cview = dialogView.findViewById<View>(sch.get(attr)!!)
                if (cview is EditText) {
                    when ((cview as EditText).inputType) {
                        InputType.TYPE_CLASS_TEXT -> {
                            (cview as EditText).setText(value.toString())
                        }
                        InputType.TYPE_CLASS_NUMBER -> {
                            (cview as EditText).setText(value.toString())
                        }
                        InputType.TYPE_CLASS_DATETIME -> {
                            (cview as EditText).setText(value.toString())
                        }
                    }

                } else if (cview is CheckBox) {
                    (cview as CheckBox).isChecked = value as Boolean
                } else if (cview is NumberPicker) {
                    (cview as NumberPicker).value = value as Int
                } else if (cview is PricePicker) {
                    (cview as PricePicker).setPrice((value as Number).toFloat())
                } else if (cview is Spinner) {
                    val spinner = (cview as Spinner)
                    for (i in 0..(spinner.count - 1)) {
                        Log.i("spinner:debug", "${value} ==? ${spinner.getItemIdAtPosition(i)}")
                        if (value.toString() == (spinner.getItemIdAtPosition(i)).toString()) {
                            spinner.setSelection(i)
                            break
                        }
                    }
                }
            }
        }

        successCallback = callback
        builder.show();
    }

    fun getData(): Map<String, Any> {
        val data = HashMap<String, Any>()

        for ((attr, control) in sch) {
            val cview = dialogView.findViewById<View>(control)
            if (cview is EditText) {
                when ((cview as EditText).inputType) {
                    InputType.TYPE_CLASS_TEXT -> {
                        data.put(attr, (cview as EditText).text.toString())
                    }
                    InputType.TYPE_CLASS_NUMBER -> {
                        data.put(attr, (cview as EditText).text.toString())
                    }
                    InputType.TYPE_CLASS_DATETIME -> {
                        data.put(attr, (cview as EditText).text.toString())
                    }
                    else -> {
                        data.put(attr, (cview as EditText).text.toString())
                    }
                }

            } else if (cview is CheckBox) {
                data.put(attr, (cview as CheckBox).isChecked)
            } else if (cview is NumberPicker) {
                data.put(attr, (cview as NumberPicker).value)
            } else if (cview is PricePicker) {
                data.put(attr, (cview as PricePicker).getPrice())
            } else if (cview is Spinner) {
                data.put(attr, (cview as Spinner).selectedItemId)
            }
        }
        return data;
    }

    open fun success(data: Map<String, Any>) {
        Log.w("gastracker", "Warning: Form result: ${Gson().toJson(data)}")
    }

    open fun initialise(builder: AlertDialog.Builder, view: View, schema: Map<String, Int>) {}

    init {
        sch = schema
        builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        dialogView = LayoutInflater.from(context).inflate(layout, null)
        builder.setView(dialogView)
        builder.setPositiveButton("Ok") { dialog, p1 ->
            // return form value
            val data = getData()
            success(data)
            if (successCallback != null) {
                successCallback!!(data)
                successCallback = null
            }
        }
        builder.setNegativeButton("Cancel") { dialog, p1 ->
            dialog.dismiss()
            successCallback = null
        }

        builder.setOnDismissListener { dialogInterface ->
            (dialogView.getParent() as ViewGroup).removeView(dialogView)
            successCallback = null
        }

        initialise(builder, dialogView, sch)
    }
}