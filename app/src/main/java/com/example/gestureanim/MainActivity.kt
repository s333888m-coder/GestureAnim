package com.example.gestureanim

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private val tabViews = ArrayList<TextView>()
    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C.BG)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(C.CARD)
        bar.setPadding(0, dp(8), 0, dp(8))
        listOf("🏠" to "होम", "✨" to "इफेक्ट्स", "🔊" to "साउंड्स", "⚙️" to "सेटिंग्स").forEachIndexed { i, p ->
            val t = text("${p.first}\n${p.second}", 11f, C.SUB, true)
            t.gravity = Gravity.CENTER
            t.setOnClickListener { showTab(i) }
            tabViews.add(t)
            bar.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        showTab(tab)
    }

    private fun showTab(i: Int) {
        tab = i
        tabViews.forEachIndexed { idx, v -> v.setTextColor(if (idx == i) C.ACCENT else C.SUB) }
        content.removeAllViews()
        val sv = ScrollView(this)
        val body = vbox()
        body.setPadding(dp(16), dp(28), dp(16), dp(24))
        sv.addView(body)
        when (i) {
            0 -> buildHome(body)
            1 -> buildEffects(body)
            2 -> buildSounds(body)
            else -> buildSettings(body)
        }
        content.addView(sv)
    }

    private fun serviceOn() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true

    private fun notifAccessOn() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun openAppInfo() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        }
    }

    private fun openAutostart() {
        try {
            startActivity(Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        } catch (e: Exception) {
            openAppInfo()
        }
    }

    private fun openBattery() {
        try {
            startActivity(Intent().setClassName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", packageName)
                .putExtra("package_label", getString(R.string.app_name)))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                openAppInfo()
            }
        }
    }

    // ------------------------------------------------------------------ होम
    private fun buildHome(b: LinearLayout) {
        b.add(banner("Gesture Animator", "हर स्वाइप, टच और नोटिफिकेशन पर एनिमेशन + साउंड"), 0, 14)

        val st = card()
        st.add(sectionTitle("🔌 अनुमतियाँ / सर्विस"), 0, 10)
        val on1 = serviceOn()
        val r1 = hbox()
        r1.addView(text("Accessibility Service", 14f, C.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r1.addView(chip(if (on1) "ON" else "OFF", if (on1) C.OK else C.BAD))
        st.add(r1, 0, 4)
        st.add(text("एनिमेशन, जेस्चर रूल और पॉपअप साउंड के लिए", 12f, C.SUB), 0, 6)
        if (!on1) st.add(primaryButton("Accessibility चालू करें") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, 0, 12)
        val on2 = notifAccessOn()
        val r2 = hbox()
        r2.addView(text("Notification Access", 14f, C.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r2.addView(chip(if (on2) "ON" else "OFF", if (on2) C.OK else C.BAD))
        st.add(r2, 4, 4)
        st.add(text("Gmail, मैसेज, कॉल आदि नोटिफिकेशन साउंड के लिए", 12f, C.SUB), 0, 6)
        if (!on2) st.add(primaryButton("Notification Access चालू करें") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, 0, 0)
        b.add(st, 0, 12)

        val mi = card()
        mi.add(sectionTitle("📱 Xiaomi / Redmi / HyperOS के लिए ज़रूरी सेटिंग"), 0, 8)
        mi.add(text(
            "HyperOS सर्विस को बैकग्राउंड में बंद कर देता है और sideload ऐप की अनुमतियाँ रोकता है। " +
                "नीचे के बटन क्रम से करें:\n" +
                "1. App info खोलें → ऊपर दाएँ ⋮ → \"Allow restricted settings\" → फिर Accessibility / Notification Access चालू करें।\n" +
                "2. Autostart चालू करें।\n" +
                "3. Battery saver → \"No restrictions\" चुनें।\n" +
                "4. Recents में इस ऐप के कार्ड को लॉक करें (कार्ड दबाकर रखें → 🔒)।", 12f, C.SUB), 0, 10)
        mi.add(ghostButton("1️⃣ App info खोलें") { openAppInfo() }, 0, 8)
        mi.add(ghostButton("2️⃣ Autostart सेटिंग खोलें") { openAutostart() }, 0, 8)
        mi.add(ghostButton("3️⃣ Battery सेटिंग खोलें") { openBattery() }, 0, 0)
        b.add(mi, 0, 12)

        val events = GestureType.values().map { Prefs.get(this, it) }
        val rules = RuleStore.load(this)
        val sounds = SoundStore.load(this)
        val soundCount = events.count { it.snd.enabled } + rules.count { it.cfg.snd.enabled } + sounds.count { it.snd.enabled }
        val sm = card()
        sm.add(sectionTitle("📊 अभी का हाल"), 0, 10)
        val row = hbox()
        val c1 = chip("${events.count { it.enabled }}/${events.size} इवेंट", C.ACCENT)
        val c2 = chip("${rules.count { it.enabled }} रूल", C.ACCENT2)
        val c3 = chip("$soundCount साउंड", C.WARN)
        row.addView(c1)
        row.addView(c2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.leftMargin = dp(8) })
        row.addView(c3, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.leftMargin = dp(8) })
        sm.add(row)
        b.add(sm, 0, 12)

        val tips = card()
        tips.add(sectionTitle("💡 शुरुआत कैसे करें"), 0, 8)
        tips.add(text(
            "1. ऊपर Accessibility (और ज़रूरत हो तो Notification Access) चालू करें।\n" +
                "2. ✨ इफेक्ट्स टैब में हर इवेंट या Gesture Rule का डिज़ाइन और साउंड चुनें।\n" +
                "3. 🔊 साउंड्स टैब में Gmail, कॉल, पॉपअप जैसे नोटिफिकेशन के अलग साउंड लगाएँ।\n" +
                "4. ⚙️ सेटिंग्स में मास्टर वॉल्यूम और जेस्चर ज़ोन ट्यून करें।", 13f, C.SUB))
        b.add(tips)
    }

    // ------------------------------------------------------------------ इफेक्ट्स
    private fun buildEffects(b: LinearLayout) {
        b.add(banner("इफेक्ट्स", "हर इवेंट और जेस्चर का अपना डिज़ाइन + साउंड"), 0, 14)

        b.add(sectionTitle("⚡ सामान्य इवेंट", "टच, स्वाइप, बैक, होम, रीसेंट्स…"), 4, 8)
        GestureType.values().forEach { t ->
            val cfg = Prefs.get(this, t)
            val sub = Templates.label(cfg.template) +
                (if (cfg.snd.enabled) "  •  🔊 ${Sounds.label(this, cfg.snd.source)}" else "")
            b.add(listItem(gestureIcon(t), t.label, sub, if (cfg.enabled) "ON" else "OFF", if (cfg.enabled) C.OK else C.SUB) {
                startActivity(Intent(this, EventSettingsActivity::class.java).putExtra("type", t.name))
            }, 0, 8)
        }

        b.add(sectionTitle("🎯 Gesture Rules", "पोज़िशन + संदर्भ + नतीजे के हिसाब से"), 18, 8)
        val rules = RuleStore.load(this)
        if (rules.isEmpty()) b.add(text("अभी कोई रूल नहीं है।", 13f, C.SUB), 0, 8)
        rules.forEach { r ->
            val sub = "${r.ctx.label} → ${r.outcome.label}" +
                (if (r.cfg.snd.enabled) "  •  🔊" else "")
            b.add(listItem("🎯", "${r.zone.label} • ${r.dir.label}", sub,
                if (r.enabled) "ON" else "OFF", if (r.enabled) C.OK else C.SUB) {
                startActivity(Intent(this, RuleEditActivity::class.java).putExtra("id", r.id))
            }, 0, 8)
        }
        b.add(primaryButton("➕ नया रूल जोड़ें") {
            val r = RuleStore.newRule()
            RuleStore.upsert(this, r)
            startActivity(Intent(this, RuleEditActivity::class.java).putExtra("id", r.id))
        }, 4, 0)
    }

    // ------------------------------------------------------------------ साउंड्स
    private fun openEntry(kind: SKind, key: String, label: String, enabledOnCreate: Boolean) {
        val r = SoundStore.find(this, kind, key) ?: SoundStore.create(this, kind, key, label, enabledOnCreate)
        startActivity(Intent(this, SoundEntryActivity::class.java).putExtra("id", r.id))
    }

    private fun soundSub(r: SoundRule?): String =
        if (r == null) "सेट नहीं — टैप करके चुनें"
        else Sounds.label(this, r.snd.source) + "  •  ${r.snd.volume}%"

    private fun buildSounds(b: LinearLayout) {
        b.add(banner("साउंड्स", "नोटिफिकेशन, कॉल, पॉपअप — सबके अलग साउंड"), 0, 14)
        val rules = SoundStore.load(this)

        if (!notifAccessOn()) {
            val a = card()
            a.add(sectionTitle("🔔 Notification Access ज़रूरी है"), 0, 6)
            a.add(text("ऐप-वार और टाइप-वार साउंड के लिए यह अनुमति चालू करें।", 12f, C.SUB), 0, 10)
            a.add(primaryButton("चालू करें") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            b.add(a, 0, 14)
        }

        // --- ऐप-वार
        b.add(sectionTitle("📱 ऐप-वार साउंड", "जैसे Gmail का अलग, WhatsApp का अलग"), 4, 8)
        rules.filter { it.kind == SKind.APP }.forEach { r ->
            b.add(listItem(SoundStore.icon(r), r.label, soundSub(r),
                if (r.snd.enabled) "ON" else "OFF", if (r.snd.enabled) C.OK else C.SUB) {
                startActivity(Intent(this, SoundEntryActivity::class.java).putExtra("id", r.id))
            }, 0, 8)
        }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val chips = hbox()
        listOf(
            Triple("com.google.android.gm", "📧", "Gmail"),
            Triple("com.whatsapp", "💬", "WhatsApp"),
            Triple("org.telegram.messenger", "✈️", "Telegram"),
            Triple("com.instagram.android", "📸", "Instagram"),
            Triple("com.google.android.apps.messaging", "💌", "Messages"),
            Triple("com.facebook.katana", "👍", "Facebook")
        ).forEach { p ->
            val g = ghostButton("${p.second} ${p.third}") { openEntry(SKind.APP, p.first, p.third, true) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            chips.addView(g, lp)
        }
        hs.addView(chips)
        b.add(hs, 4, 8)
        b.add(primaryButton("➕ कोई भी ऐप चुनें") {
            startActivityForResult(Intent(this, AppPickerActivity::class.java), 21)
        }, 0, 0)

        // --- नोटिफिकेशन टाइप
        b.add(sectionTitle("📂 नोटिफिकेशन टाइप", "कॉल/रिंगटोन, मैसेज, ईमेल, अलार्म…"), 20, 8)
        SoundStore.categories.forEach { cat ->
            val r = rules.firstOrNull { it.kind == SKind.CATEGORY && it.key == cat.first }
            b.add(listItem(cat.second, cat.third, soundSub(r),
                if (r == null) "＋" else if (r.snd.enabled) "ON" else "OFF",
                if (r != null && r.snd.enabled) C.OK else C.SUB) {
                openEntry(SKind.CATEGORY, cat.first, cat.third, false)
            }, 0, 8)
        }

        // --- खास
        b.add(sectionTitle("✨ खास"), 20, 8)
        val popup = rules.firstOrNull { it.kind == SKind.POPUP }
        b.add(listItem("💭", "पॉपअप / डायलॉग", soundSub(popup),
            if (popup == null) "＋" else if (popup.snd.enabled) "ON" else "OFF",
            if (popup != null && popup.snd.enabled) C.OK else C.SUB) {
            openEntry(SKind.POPUP, "popup", "पॉपअप / डायलॉग", false)
        }, 0, 8)
        val any = rules.firstOrNull { it.kind == SKind.ANY }
        b.add(listItem("🔔", "हर नोटिफिकेशन (डिफ़ॉल्ट)", soundSub(any),
            if (any == null) "＋" else if (any.snd.enabled) "ON" else "OFF",
            if (any != null && any.snd.enabled) C.OK else C.SUB) {
            openEntry(SKind.ANY, "any", "हर नोटिफिकेशन", false)
        }, 0, 12)

        val note = card()
        note.add(text(
            "ℹ️ टच, बैक, रीसेंट्स आदि इवेंट के साउंड ✨ इफेक्ट्स टैब में हर इवेंट/रूल के अंदर सेट होते हैं।",
            12f, C.SUB))
        b.add(note)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == RESULT_OK && data != null) {
            val pkg = data.getStringExtra("pkg") ?: return
            val label = data.getStringExtra("label") ?: pkg
            openEntry(SKind.APP, pkg, label, true)
        }
    }

    // ------------------------------------------------------------------ सेटिंग्स
    private fun buildSettings(b: LinearLayout) {
        b.add(banner("सेटिंग्स", "मास्टर साउंड और जेस्चर ज़ोन की ट्यूनिंग"), 0, 14)

        val m = card()
        m.add(sectionTitle("🔊 मास्टर साउंड"), 0, 8)
        m.add(toggleRow("सभी आवाज़ें चालू", "बंद करने पर कोई भी साउंड नहीं बजेगा", Tuning.masterOn(this)) {
            Tuning.setMasterOn(this, it)
        })
        m.add(slider("मास्टर वॉल्यूम (%)", 0, 100, Tuning.masterVol(this)) { Tuning.setMasterVol(this, it) })
        b.add(m, 0, 12)

        val g = card()
        g.add(sectionTitle("🎯 जेस्चर ज़ोन"), 0, 8)
        g.add(slider("पट्टी की मोटाई (dp)", 12, 48, Tuning.stripDp(this)) { Tuning.setStripDp(this, it) })
        g.add(slider("स्वाइप संवेदनशीलता — कम = जल्दी पहचान (dp)", 8, 80, Tuning.sensDp(this)) {
            Tuning.setSensDp(this, it)
        })
        b.add(g, 0, 12)

        val a = card()
        a.add(sectionTitle("🔧 सिस्टम सेटिंग"), 0, 10)
        a.add(ghostButton("Accessibility सेटिंग खोलें") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, 0, 8)
        a.add(ghostButton("Notification Access सेटिंग खोलें") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        b.add(a)
    }
}
