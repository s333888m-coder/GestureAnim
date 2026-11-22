package com.example.gestureanim

import android.app.Activity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner

fun Activity.slider(title: String, lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit): View {
    val box = vbox()
    box.setPadding(0, dp(8), 0, dp(4))
    val tv = text("$title: $value", 13f, C.SUB)
    val sb = SeekBar(this)
    sb.max = hi - lo
    sb.progress = value - lo
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            val v = p + lo
            tv.text = "$title: $v"
            if (fromUser) onChange(v)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    box.addView(tv)
    box.addView(sb)
    return box
}

fun <T> Activity.choiceRow(
    title: String, values: List<T>, labels: List<String>, selected: T, onPick: (T) -> Unit
): View {
    val box = vbox()
    box.setPadding(0, dp(8), 0, dp(4))
    val tv = text(title, 13f, C.SUB)
    val sp = Spinner(this)
    sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    sp.setSelection(values.indexOf(selected).coerceAtLeast(0))
    var first = true
    sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
            if (first) { first = false; return }
            onPick(values[pos])
        }
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
    box.addView(tv)
    box.addView(sp)
    return box
}
