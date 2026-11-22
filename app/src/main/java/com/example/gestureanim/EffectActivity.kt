package com.example.gestureanim

import android.content.Intent
import android.graphics.Color
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import java.io.File

/** एनिमेशन + साउंड + प्रीव्यू वाला साझा एडिटर */
abstract class EffectActivity : BaseActivity() {
    protected lateinit var cfg: EventConfig
    protected lateinit var preview: EffectView
    private var spinner: Spinner? = null
    private var keys: List<String> = emptyList()
    private var suppress = false

    protected abstract fun persist()
    protected abstract fun playPreview()

    protected fun addEffectEditor(root: LinearLayout) {
        // ---- एनिमेशन कार्ड
        val a = card()
        a.add(sectionTitle("🎨 एनिमेशन"), 0, 8)
        a.add(toggleRow("एनिमेशन दिखाएँ", "बंद करने पर सिर्फ आवाज़ बजेगी", cfg.visual) { cfg.visual = it; persist() }, 0, 8)
        a.add(text("HTML Template", 13f, C.SUB), 4, 0)
        val sp = Spinner(this)
        spinner = sp
        a.add(sp, 0, 6)
        refreshSpinner()
        a.add(ghostButton("📄 अपनी HTML फाइल चुनें") { pick() }, 0, 4)
        a.add(slider("Size (%)", 20, 300, cfg.size) { cfg.size = it; persist() })
        a.add(slider("Duration (ms)", 200, 3000, cfg.duration) { cfg.duration = it; persist() })
        a.add(slider("Opacity (%)", 10, 100, cfg.opacity) { cfg.opacity = it; persist() })
        a.add(text("Color (#RRGGBB)", 13f, C.SUB), 10, 0)
        val hex = EditText(this)
        hex.setText(cfg.color)
        hex.setSingleLine()
        hex.setTextColor(C.TEXT)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) {}
            override fun onTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s.toString()
                if (Regex("#[0-9A-Fa-f]{6}").matches(t)) { cfg.color = t; persist() }
            }
        })
        a.add(hex, 0, 6)
        val row = hbox()
        listOf("#00E5FF", "#FF4081", "#76FF03", "#FFEA00", "#FFFFFF", "#7C4DFF").forEach { col ->
            val dot = text("●", 22f, Color.parseColor(col))
            dot.gravity = android.view.Gravity.CENTER
            dot.background = rounded(C.CARD2, 12)
            dot.setOnClickListener { hex.setText(col) }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.rightMargin = dp(6)
            row.addView(dot, lp)
        }
        a.add(row)
        root.add(a, 0, 12)

        // ---- साउंड कार्ड
        val s = card()
        s.add(sectionTitle("🔊 साउंड", "इस इवेंट के साथ बजने वाली आवाज़"), 0, 8)
        addSoundEditor(s, cfg.snd) { persist() }
        root.add(s, 0, 12)

        // ---- प्रीव्यू कार्ड
        val p = card()
        p.add(sectionTitle("👁 प्रीव्यू"), 0, 8)
        val frame = FrameLayout(this)
        frame.background = rounded(0xFF0A0D1A.toInt(), 14)
        preview = EffectView(this)
        frame.addView(preview, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        p.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)))
        p.add(primaryButton("▶ Test (एनिमेशन + साउंड)") {
            if (cfg.visual) playPreview()
            SoundPlayer.play(this, cfg.snd)
        }, 12, 0)
        root.add(p, 0, 12)
    }

    private fun refreshSpinner() {
        val sp = spinner ?: return
        keys = Templates.keys(this)
        suppress = true
        sp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, keys.map { Templates.label(it) })
        sp.setSelection(keys.indexOf(cfg.template).coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppress) { suppress = false; return }
                cfg.template = keys[pos]
                persist()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun pick() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        var name = "custom.html"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i)
            }
        }
        name = File(name).name
        if (!name.endsWith(".html", true) && !name.endsWith(".htm", true)) name += ".html"
        contentResolver.openInputStream(uri)?.use { input ->
            File(Templates.dir(this), name).outputStream().use { out -> input.copyTo(out) }
        }
        cfg.template = "custom:$name"
        persist()
        refreshSpinner()
        Toast.makeText(this, "$name जुड़ गई", Toast.LENGTH_SHORT).show()
    }
}
