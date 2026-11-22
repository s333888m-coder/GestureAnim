package com.example.gestureanim

import android.os.Bundle

class EventSettingsActivity : EffectActivity() {
    private lateinit var type: GestureType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = GestureType.valueOf(intent.getStringExtra("type") ?: "TOUCH")
        cfg = Prefs.get(this, type)

        val root = scaffold("${gestureIcon(type)}  ${type.label}", type.hint)
        val c = card()
        c.add(toggleRow("इवेंट चालू", "बंद करने पर एनिमेशन और साउंड दोनों बंद", cfg.enabled) {
            cfg.enabled = it
            persist()
        })
        root.add(c, 0, 12)
        addEffectEditor(root)
    }

    override fun persist() = Prefs.save(this, type, cfg)

    override fun playPreview() = preview.play(type, cfg, type.xf, type.yf)
}
