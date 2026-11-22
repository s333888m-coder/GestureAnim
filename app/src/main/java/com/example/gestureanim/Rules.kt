package com.example.gestureanim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Ctx(val label: String) {
    ANY("कहीं भी (होम + ऐप)"),
    HOME("सिर्फ होम स्क्रीन पर"),
    APP("सिर्फ ऐप के अंदर")
}

enum class Zone(val label: String, val xf: Float, val yf: Float, val natural: String) {
    TOP_LEFT("ऊपर-बायाँ कोना", .1f, .02f, "down"),
    TOP_RIGHT("ऊपर-दायाँ कोना", .9f, .02f, "down"),
    LEFT_EDGE("बायाँ किनारा", .02f, .5f, "right"),
    RIGHT_EDGE("दायाँ किनारा", .98f, .5f, "left"),
    BOTTOM_LEFT("नीचे-बायाँ", .17f, .98f, "up"),
    BOTTOM_CENTER("नीचे-बीच", .5f, .98f, "up"),
    BOTTOM_RIGHT("नीचे-दायाँ", .83f, .98f, "up")
}

enum class Dir(val label: String, val key: String) {
    ANY("किसी भी दिशा में", "any"),
    UP("ऊपर", "up"),
    DOWN("नीचे", "down"),
    LEFT("बाएँ", "left"),
    RIGHT("दाएँ", "right")
}

/** स्वाइप के बाद क्या हुआ — उस नतीजे पर इफेक्ट दिखेगा */
enum class Outcome(val label: String) {
    ANY("तुरंत — स्वाइप पहचानते ही"),
    RECENTS("Recents खुलने पर"),
    HOME("Home पहुँचने पर"),
    BACK("Back होने पर (अंदाज़ा)"),
    APP_OPEN("ऐप खुलने / बदलने पर"),
    SHADE("Notification / Quick Settings खुलने पर (अंदाज़ा)"),
    WINDOW("कोई भी नई विंडो खुलने पर (जैसे ऐप ड्रावर)")
}

/** स्वाइप पहचानने के बाद ऐप खुद कौन सा सिस्टम एक्शन करे */
enum class Forward(val label: String) {
    NONE("कुछ नहीं (सिर्फ इफेक्ट)"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recents"),
    NOTIFICATIONS("Notification panel"),
    QUICK_SETTINGS("Quick Settings")
}

data class Rule(
    var id: String,
    var enabled: Boolean,
    var ctx: Ctx,
    var zone: Zone,
    var dir: Dir,
    var outcome: Outcome,
    var forward: Forward,
    var cfg: EventConfig
) {
    fun title(): String =
        "${if (enabled) "✅" else "⏸"} ${zone.label} • ${dir.label} → ${outcome.label}\n     [${ctx.label}]"
}

inline fun <reified T : Enum<T>> parseEnum(s: String?, def: T): T =
    try { enumValueOf<T>(s ?: "") } catch (e: Exception) { def }

object RuleStore {
    private const val KEY = "rules_json"
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun newRule() = Rule(
        UUID.randomUUID().toString(), true, Ctx.HOME, Zone.TOP_RIGHT, Dir.DOWN, Outcome.ANY, Forward.NONE,
        EventConfig(true, "builtin:edge_glow", 100, 900, "#00E5FF", 90)
    )

    fun load(c: Context): MutableList<Rule> {
        val s = sp(c).getString(KEY, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) { fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun get(c: Context, id: String): Rule? = load(c).firstOrNull { it.id == id }

    fun saveAll(c: Context, list: List<Rule>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        sp(c).edit().putString(KEY, a.toString()).apply()
    }

    fun upsert(c: Context, r: Rule) {
        val l = load(c)
        val i = l.indexOfFirst { it.id == r.id }
        if (i >= 0) l[i] = r else l.add(r)
        saveAll(c, l)
    }

    fun delete(c: Context, id: String) = saveAll(c, load(c).filter { it.id != id })

    private fun toJson(r: Rule) = JSONObject()
        .put("id", r.id).put("on", r.enabled)
        .put("ctx", r.ctx.name).put("zone", r.zone.name).put("dir", r.dir.name)
        .put("out", r.outcome.name).put("fwd", r.forward.name)
        .put("tpl", r.cfg.template).put("size", r.cfg.size).put("dur", r.cfg.duration)
        .put("color", r.cfg.color).put("op", r.cfg.opacity)
        .put("vis", r.cfg.visual).put("snd", Sounds.toJson(r.cfg.snd))

    private fun fromJson(o: JSONObject) = Rule(
        o.getString("id"), o.optBoolean("on", true),
        parseEnum(o.optString("ctx"), Ctx.HOME),
        parseEnum(o.optString("zone"), Zone.TOP_RIGHT),
        parseEnum(o.optString("dir"), Dir.ANY),
        parseEnum(o.optString("out"), Outcome.ANY),
        parseEnum(o.optString("fwd"), Forward.NONE),
        EventConfig(
            true, o.optString("tpl", "builtin:edge_glow"), o.optInt("size", 100),
            o.optInt("dur", 900), o.optString("color", "#00E5FF"), o.optInt("op", 90),
            o.optBoolean("vis", true), Sounds.fromJson(o.optJSONObject("snd"))
        )
    )
}

object Tuning {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun stripDp(c: Context) = sp(c).getInt("strip_dp", 24)
    fun sensDp(c: Context) = sp(c).getInt("sens_dp", 16)
    fun setStripDp(c: Context, v: Int) = sp(c).edit().putInt("strip_dp", v).apply()
    fun setSensDp(c: Context, v: Int) = sp(c).edit().putInt("sens_dp", v).apply()
    fun masterOn(c: Context) = sp(c).getBoolean("master_on", true)
    fun setMasterOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("master_on", v).apply()
    fun masterVol(c: Context) = sp(c).getInt("master_vol", 100)
    fun setMasterVol(c: Context, v: Int) = sp(c).edit().putInt("master_vol", v).apply()
}
