package com.example.gestureanim

import android.os.Bundle

class RuleEditActivity : EffectActivity() {
    private lateinit var rule: Rule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: ""
        rule = RuleStore.get(this, id) ?: run { finish(); return }
        cfg = rule.cfg

        val root = scaffold("🎯 Gesture Rule", "कहाँ, कैसे और कब — सब शर्तें यहाँ")

        val c = card()
        c.add(sectionTitle("📐 शर्तें"), 0, 8)
        c.add(toggleRow("रूल चालू", null, rule.enabled) { rule.enabled = it; persist() })
        c.add(choiceRow("कहाँ पर (संदर्भ)", Ctx.values().toList(), Ctx.values().map { it.label }, rule.ctx) {
            rule.ctx = it; persist()
        })
        c.add(choiceRow("स्वाइप कहाँ से शुरू (ज़ोन)", Zone.values().toList(), Zone.values().map { it.label }, rule.zone) {
            rule.zone = it; persist()
        })
        c.add(choiceRow("किस दिशा में", Dir.values().toList(), Dir.values().map { it.label }, rule.dir) {
            rule.dir = it; persist()
        })
        c.add(choiceRow("कब इफेक्ट/साउंड हो (नतीजा)", Outcome.values().toList(), Outcome.values().map { it.label }, rule.outcome) {
            rule.outcome = it; persist()
        })
        c.add(choiceRow("स्वाइप पहचानते ही ऐप खुद क्या करे", Forward.values().toList(), Forward.values().map { it.label }, rule.forward) {
            rule.forward = it; persist()
        })
        val note = text(
            "नोट: ज़ोन की पट्टी उस जगह का टच खुद ले लेती है। ऊपर के कोनों (status bar) पर रूल लगाएँ तो " +
                "\"खुद क्या करे\" में Notification panel / Quick Settings चुनें। बायाँ/दायाँ किनारा और नीचे वाले " +
                "ज़ोन में सिस्टम का अपना जेस्चर चलता रहता है, वहाँ \"कुछ नहीं\" रखें।", 12f, C.WARN)
        c.add(note, 12, 0)
        root.add(c, 0, 12)

        addEffectEditor(root)

        root.add(ghostButton("🗑 यह रूल हटाएँ", C.BAD) {
            RuleStore.delete(this, rule.id)
            finish()
        }, 4, 0)
    }

    override fun persist() {
        rule.cfg = cfg
        RuleStore.upsert(this, rule)
    }

    override fun playPreview() {
        val d = if (rule.dir == Dir.ANY) rule.zone.natural else rule.dir.key
        preview.playRaw("RULE", d, cfg, rule.zone.xf, rule.zone.yf, rule.zone.name, rule.outcome.name)
    }
}
