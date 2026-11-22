package com.example.gestureanim

import android.app.Activity
import android.content.Intent
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import java.io.File

abstract class BaseActivity : Activity() {
    private var audioCb: ((String) -> Unit)? = null

    fun pickAudio(cb: (String) -> Unit) {
        audioCb = cb
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), 8)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 8 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        var name = "sound_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i)
            }
        }
        name = File(name).name
        contentResolver.openInputStream(uri)?.use { input ->
            File(Sounds.dir(this), name).outputStream().use { out -> input.copyTo(out) }
        }
        audioCb?.invoke(name)
    }

    /** साउंड चुनने, वॉल्यूम और चैनल सेट करने वाला हिस्सा */
    fun addSoundEditor(host: LinearLayout, snd: SoundCfg, onChange: () -> Unit) {
        host.add(toggleRow("आवाज़ चालू", "इवेंट होते ही यह साउंड बजेगा", snd.enabled) { snd.enabled = it; onChange() }, 0, 8)
        host.add(text("साउंड चुनें", 13f, C.SUB), 4, 0)

        val sp = Spinner(this)
        var keys: List<String> = emptyList()
        var first = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                snd.source = keys[pos]
                onChange()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        fun fill() {
            keys = Sounds.keys(this)
            first = true
            sp.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, keys.map { Sounds.label(this, it) })
            sp.setSelection(keys.indexOf(snd.source).coerceAtLeast(0))
        }
        fill()
        host.add(sp, 0, 6)

        host.add(ghostButton("📂 अपनी ऑडियो फाइल चुनें") {
            pickAudio { name ->
                snd.source = "custom:$name"
                onChange()
                fill()
            }
        }, 0, 6)

        host.add(slider("वॉल्यूम (%)", 0, 100, snd.volume) { snd.volume = it; onChange() })
        host.add(choiceRow("किस वॉल्यूम चैनल पर बजे", listOf(0, 1, 2, 3), Sounds.streamLabels, snd.stream) {
            snd.stream = it
            onChange()
        })
        host.add(ghostButton("▶ साउंड सुनें", C.OK) {
            SoundPlayer.play(this, snd, null, true)
        }, 10, 0)
    }
}
