package spdb.gastracker.utils

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import com.google.gson.Gson


open class DialogForm(context: Context, layout: Int, title: String, schema: Map<String, Int>) {

    val dialogView: View
    val builder: AlertDialog.Builder

    val sch: Map<String, Int>


    fun open(data: Map<String, Any>?) {
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
                }
            }
        }

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
            }
        }
        return data;
    }

    open fun success(data: Map<String, Any>) {
        Log.w("gastracker", "Warning: Form result: ${Gson().toJson(data)}");
    }

    init {
        sch = schema
        builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        dialogView = LayoutInflater.from(context).inflate(layout, null)
        builder.setView(dialogView)
        builder.setPositiveButton("Ok") { dialog, p1 ->
            // return form value
            success(getData())
        }
        builder.setNegativeButton("Cancel") { dialog, p1 -> dialog.dismiss() }
    }
}