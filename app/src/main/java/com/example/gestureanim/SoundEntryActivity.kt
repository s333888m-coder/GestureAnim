package com.example.gestureanim

import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class SoundEntryActivity : BaseActivity() {
    private lateinit var rule: SoundRule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: ""
        rule = SoundStore.get(this, id) ?: run { finish(); return }

        val kindText = when (rule.kind) {
            SKind.APP -> "ऐप की नोटिफिकेशन का साउंड"
            SKind.CATEGORY -> "नोटिफिकेशन टाइप का साउंड"
            SKind.POPUP -> "पॉपअप / डायलॉग का साउंड"
            SKind.ANY -> "हर नोटिफिकेशन का डिफ़ॉल्ट साउंड"
        }
        val root = scaffold("${SoundStore.icon(rule)}  ${rule.label}", kindText)

        val c = card()
        c.add(sectionTitle("🔊 साउंड सेटिंग"), 0, 8)
        addSoundEditor(c, rule.snd) { SoundStore.upsert(this, rule) }
        root.add(c, 0, 12)

        val info = card()
        info.add(sectionTitle("ℹ️ ज़रूरी जानकारी"), 0, 8)
        when (rule.kind) {
            SKind.APP -> {
                info.add(text(
                    "इस ऐप की हर नई नोटिफिकेशन आते ही यह साउंड बजेगा। ऐप का अपना original साउंड भी बजता रहेगा — " +
                        "उसे बंद करना हो तो उस ऐप की Notification सेटिंग में साउंड Silent कर दें।", 13f, C.SUB))
                info.add(ghostButton("🔕 उस ऐप की Notification सेटिंग खोलें") {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, rule.key)
                    )
                }, 10, 0)
            }
            SKind.CATEGORY -> {
                if (rule.key == "CALL") {
                    info.add(text(
                        "इनकमिंग कॉल की नोटिफिकेशन दिखते ही यह साउंड लूप में बजेगा और कॉल उठाने/काटने पर रुक जाएगा " +
                            "(अधिकतम 45 सेकंड)। यह सिस्टम रिंगटोन की जगह नहीं लेता — उसे फोन की Sound सेटिंग से बदलें " +
                            "या Silent रखें। कुछ डायलर ऐप में पहचान सटीक न भी हो सकती है।", 13f, C.SUB))
                    info.add(ghostButton("📞 फोन की Sound / Ringtone सेटिंग खोलें") {
                        startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                    }, 10, 0)
                } else {
                    info.add(text(
                        "ऐप जब नोटिफिकेशन को इस टाइप का बताती है, तब यह साउंड बजेगा। ऐप-वार साउंड हो तो वह इससे ऊपर माना जाएगा।",
                        13f, C.SUB))
                }
            }
            SKind.POPUP -> info.add(text(
                "किसी ऐप में डायलॉग / पॉपअप खुलते ही यह साउंड बजेगा (Accessibility Service से पहचान, " +
                    "इसलिए कुछ ऐप के कस्टम पॉपअप छूट सकते हैं)।", 13f, C.SUB))
            SKind.ANY -> info.add(text(
                "जिस नोटिफिकेशन का ऐप-वार या टाइप-वार साउंड सेट नहीं है, उस पर यह साउंड बजेगा।", 13f, C.SUB))
        }
        root.add(info, 0, 12)

        root.add(ghostButton("🗑 यह साउंड रूल हटाएँ", C.BAD) {
            SoundStore.delete(this, rule.id)
            finish()
        }, 4, 0)
    }
}
