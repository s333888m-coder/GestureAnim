package com.example.gestureanim

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView

class AppPickerActivity : Activity() {
    private class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private var all: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()
    private lateinit var lv: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.setBackgroundColor(C.BG)
        root.setPadding(dp(16), dp(28), dp(16), dp(8))
        root.addView(text("📱 ऐप चुनें", 22f, C.TEXT, true))
        val search = EditText(this)
        search.hint = "ऐप खोजें…"
        search.setTextColor(C.TEXT)
        search.setHintTextColor(C.SUB)
        search.setSingleLine()
        root.add(search, 8, 8)
        lv = ListView(this)
        lv.divider = null
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter(s.toString())
        })
        lv.setOnItemClickListener { _, _, pos, _ ->
            val it = shown[pos]
            setResult(RESULT_OK, Intent().putExtra("pkg", it.pkg).putExtra("label", it.label))
            finish()
        }

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                all = list
                applyFilter("")
            }
        }.start()
    }

    private fun applyFilter(q: String) {
        shown = if (q.isBlank()) all else all.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val it = shown[p]
                val row = hbox()
                row.setPadding(dp(12), dp(10), dp(12), dp(10))
                row.background = rounded(C.CARD, 14)
                val img = ImageView(this@AppPickerActivity)
                img.setImageDrawable(it.icon)
                row.addView(img, LinearLayout.LayoutParams(dp(40), dp(40)))
                val col = vbox()
                col.addView(text(it.label, 15f, C.TEXT, true))
                col.addView(text(it.pkg, 11f, C.SUB))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.leftMargin = dp(12)
                row.addView(col, lp)
                val wrap = LinearLayout(this@AppPickerActivity)
                wrap.setPadding(0, dp(3), 0, dp(3))
                wrap.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                return wrap
            }
        }
    }
}
