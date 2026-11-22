package com.example.gestureanim

import android.content.Context
import java.io.File

/** dir = उंगली/मूवमेंट की दिशा: up, down, left, right, none */
enum class GestureType(
    val label: String,
    val hint: String,
    val defTemplate: String,
    val xf: Float,
    val yf: Float,
    val dir: String
) {
    TOUCH("Touch / Tap", "किसी बटन या आइकन पर टैप करने पर", "builtin:ripple", .5f, .5f, "none"),
    SWIPE_UP("Swipe Up", "स्क्रीन ऊपर की तरफ स्वाइप/स्क्रोल करने पर", "builtin:trail", .5f, .6f, "up"),
    SWIPE_DOWN("Swipe Down", "स्क्रीन नीचे की तरफ स्वाइप/स्क्रोल करने पर", "builtin:trail", .5f, .4f, "down"),
    SWIPE_LEFT("Swipe Left", "बाएँ तरफ स्वाइप करने पर", "builtin:trail", .6f, .5f, "left"),
    SWIPE_RIGHT("Swipe Right", "दाएँ तरफ स्वाइप करने पर", "builtin:trail", .4f, .5f, "right"),
    BACK("Back", "बैक करने पर", "builtin:edge_glow", .02f, .5f, "right"),
    HOME("Home", "होम स्क्रीन पर जाने पर", "builtin:edge_glow", .5f, .98f, "up"),
    RECENTS("Recents", "रीसेंट ऐप्स खोलने पर", "builtin:edge_glow", .5f, .98f, "up"),
    APP_OPEN("App Open / Switch", "कोई ऐप खोलने या ऐप बदलने पर", "builtin:burst", .5f, .5f, "none")
}

data class EventConfig(
    var enabled: Boolean,
    var template: String,
    var size: Int,      // percent
    var duration: Int,  // ms
    var color: String,  // #RRGGBB
    var opacity: Int,   // percent
    var visual: Boolean = true,      // एनिमेशन दिखाना है या नहीं
    var snd: SoundCfg = SoundCfg()   // इसके साथ बजने वाला साउंड
)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun defSound(t: GestureType) = when (t) {
        GestureType.TOUCH -> "builtin:tick"
        GestureType.SWIPE_UP -> "builtin:swipe_up"
        GestureType.SWIPE_DOWN -> "builtin:swipe_down"
        GestureType.SWIPE_LEFT, GestureType.SWIPE_RIGHT -> "builtin:whoosh"
        GestureType.BACK -> "builtin:pop"
        GestureType.HOME -> "builtin:ding"
        GestureType.RECENTS -> "builtin:bubble"
        GestureType.APP_OPEN -> "builtin:chime"
    }

    fun get(c: Context, t: GestureType): EventConfig {
        val s = sp(c)
        val k = t.name
        val ds = defSound(t)
        return EventConfig(
            s.getBoolean("$k.on", true),
            s.getString("$k.tpl", t.defTemplate) ?: t.defTemplate,
            s.getInt("$k.size", 100),
            s.getInt("$k.dur", 900),
            s.getString("$k.color", "#00E5FF") ?: "#00E5FF",
            s.getInt("$k.op", 90),
            s.getBoolean("$k.vis", true),
            SoundCfg(
                s.getBoolean("$k.snd.on", false),
                s.getString("$k.snd.src", ds) ?: ds,
                s.getInt("$k.snd.vol", 80),
                s.getInt("$k.snd.str", 0)
            )
        )
    }

    fun save(c: Context, t: GestureType, cfg: EventConfig) {
        val k = t.name
        sp(c).edit()
            .putBoolean("$k.on", cfg.enabled)
            .putString("$k.tpl", cfg.template)
            .putInt("$k.size", cfg.size)
            .putInt("$k.dur", cfg.duration)
            .putString("$k.color", cfg.color)
            .putInt("$k.op", cfg.opacity)
            .putBoolean("$k.vis", cfg.visual)
            .putBoolean("$k.snd.on", cfg.snd.enabled)
            .putString("$k.snd.src", cfg.snd.source)
            .putInt("$k.snd.vol", cfg.snd.volume)
            .putInt("$k.snd.str", cfg.snd.stream)
            .apply()
    }
}

object Templates {
    val builtin = listOf("ripple", "edge_glow", "trail", "burst")

    fun dir(c: Context): File = File(c.filesDir, "templates").apply { mkdirs() }

    fun keys(c: Context): List<String> {
        val custom = dir(c).listFiles()
            ?.filter { it.extension.equals("html", true) || it.extension.equals("htm", true) }
            ?.map { "custom:${it.name}" }
            .orEmpty()
        return builtin.map { "builtin:$it" } + custom
    }

    fun label(key: String): String =
        if (key.startsWith("builtin:")) "★ " + key.removePrefix("builtin:") else "📄 " + key.removePrefix("custom:")

    fun load(c: Context, key: String): String = try {
        if (key.startsWith("builtin:")) {
            c.assets.open("templates/${key.removePrefix("builtin:")}.html").bufferedReader().use { it.readText() }
        } else {
            File(dir(c), File(key.removePrefix("custom:")).name).readText()
        }
    } catch (e: Exception) {
        "<html><body></body></html>"
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
