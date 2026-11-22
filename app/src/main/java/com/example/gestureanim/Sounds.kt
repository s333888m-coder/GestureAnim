package com.example.gestureanim

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** stream: 0=Media, 1=Notification, 2=Ringtone, 3=Alarm */
data class SoundCfg(
    var enabled: Boolean = false,
    var source: String = "builtin:pop",
    var volume: Int = 80,
    var stream: Int = 0
)

object Sounds {
    val builtin = listOf(
        "pop", "tick", "ding", "chime", "whoosh", "swipe_up", "swipe_down",
        "bubble", "beep", "blip", "sparkle", "notify", "ring", "alarm"
    )
    val streamLabels = listOf("Media (संगीत वाला वॉल्यूम)", "Notification", "Ringtone (रिंग वॉल्यूम)", "Alarm")

    fun dir(c: Context): File = File(c.filesDir, "sounds").apply { mkdirs() }

    private var sysCache: List<Pair<String, String>>? = null

    /** फोन के अपने साउंड (title, uri) */
    fun system(c: Context): List<Pair<String, String>> {
        sysCache?.let { return it }
        val out = ArrayList<Pair<String, String>>()
        try {
            val rm = RingtoneManager(c)
            rm.setType(RingtoneManager.TYPE_ALL)
            val cur = rm.cursor
            var i = 0
            while (cur.moveToNext() && i < 80) {
                val title = cur.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cur.position)
                if (uri != null) out.add(title to uri.toString())
                i++
            }
            cur.close()
        } catch (e: Exception) {
        }
        sysCache = out
        return out
    }

    fun keys(c: Context): List<String> {
        val custom = dir(c).listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.map { "custom:${it.name}" }.orEmpty()
        return builtin.map { "builtin:$it" } + custom + system(c).map { "system:${it.second}" }
    }

    fun label(c: Context, key: String): String = when {
        key.startsWith("builtin:") -> "★ " + key.removePrefix("builtin:")
        key.startsWith("custom:") -> "📄 " + key.removePrefix("custom:")
        key.startsWith("system:") -> "🔔 " + (system(c).firstOrNull { it.second == key.removePrefix("system:") }?.first ?: "System sound")
        else -> "—"
    }

    fun toJson(s: SoundCfg): JSONObject =
        JSONObject().put("on", s.enabled).put("src", s.source).put("vol", s.volume).put("str", s.stream)

    fun fromJson(o: JSONObject?): SoundCfg =
        if (o == null) SoundCfg()
        else SoundCfg(o.optBoolean("on", false), o.optString("src", "builtin:pop"), o.optInt("vol", 80), o.optInt("str", 0))
}

object SoundPlayer {
    private val active = ArrayList<MediaPlayer>()
    private val loops = HashMap<String, MediaPlayer>()

    private fun usage(stream: Int) = when (stream) {
        1 -> AudioAttributes.USAGE_NOTIFICATION
        2 -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        3 -> AudioAttributes.USAGE_ALARM
        else -> AudioAttributes.USAGE_MEDIA
    }

    private fun release(mp: MediaPlayer) {
        active.remove(mp)
        loops.entries.removeAll { it.value === mp }
        runCatching { mp.release() }
    }

    fun stopLoop(key: String) {
        loops[key]?.let { release(it) }
    }

    /** force = true: मास्टर स्विच और चालू/बंद की परवाह किए बिना बजाओ (सुनने वाले बटन के लिए) */
    fun play(ctx: Context, s: SoundCfg, loopKey: String? = null, force: Boolean = false) {
        val c = ctx.applicationContext
        if (!force) {
            if (!s.enabled) return
            if (!Tuning.masterOn(c)) return
        }
        val vol = (s.volume / 100f) * (Tuning.masterVol(c) / 100f)
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage(s.stream))
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            when {
                s.source.startsWith("builtin:") -> {
                    val afd = c.assets.openFd("sounds/${s.source.removePrefix("builtin:")}.wav")
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                s.source.startsWith("custom:") ->
                    mp.setDataSource(File(Sounds.dir(c), File(s.source.removePrefix("custom:")).name).absolutePath)
                s.source.startsWith("system:") ->
                    mp.setDataSource(c, Uri.parse(s.source.removePrefix("system:")))
                else -> {
                    mp.release()
                    return
                }
            }
            mp.setVolume(vol, vol)
            mp.isLooping = loopKey != null
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { release(it) }
            mp.setOnErrorListener { m, _, _ -> release(m); true }
            if (loopKey != null) {
                loops[loopKey]?.let { release(it) }
                loops[loopKey] = mp
            } else {
                while (active.size >= 8) release(active[0])
                active.add(mp)
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            runCatching { mp.release() }
        }
    }
}

enum class SKind { APP, CATEGORY, POPUP, ANY }

data class SoundRule(var id: String, var kind: SKind, var key: String, var label: String, var snd: SoundCfg)

object SoundStore {
    private const val KEY = "sound_rules"
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** (key, emoji, नाम) */
    val categories = listOf(
        Triple("CALL", "📞", "कॉल / रिंगटोन (इनकमिंग)"),
        Triple("MESSAGE", "💬", "मैसेज / SMS"),
        Triple("EMAIL", "📧", "ईमेल"),
        Triple("ALARM", "⏰", "अलार्म"),
        Triple("REMINDER", "🔔", "रिमाइंडर / कैलेंडर"),
        Triple("SOCIAL", "👥", "सोशल"),
        Triple("PROMO", "🏷️", "प्रमोशन")
    )

    fun icon(r: SoundRule): String = when (r.kind) {
        SKind.APP -> "📱"
        SKind.CATEGORY -> categories.firstOrNull { it.first == r.key }?.second ?: "🔔"
        SKind.POPUP -> "💭"
        SKind.ANY -> "🔔"
    }

    fun load(c: Context): MutableList<SoundRule> {
        val s = sp(c).getString(KEY, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) {
                val o = a.getJSONObject(it)
                SoundRule(
                    o.getString("id"), parseEnum(o.optString("kind"), SKind.APP),
                    o.optString("key"), o.optString("label"), Sounds.fromJson(o.optJSONObject("snd"))
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(c: Context, list: List<SoundRule>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("kind", it.kind.name).put("key", it.key)
                    .put("label", it.label).put("snd", Sounds.toJson(it.snd))
            )
        }
        sp(c).edit().putString(KEY, a.toString()).apply()
    }

    fun get(c: Context, id: String): SoundRule? = load(c).firstOrNull { it.id == id }
    fun find(c: Context, kind: SKind, key: String): SoundRule? =
        load(c).firstOrNull { it.kind == kind && it.key == key }

    fun upsert(c: Context, r: SoundRule) {
        val l = load(c)
        val i = l.indexOfFirst { it.id == r.id }
        if (i >= 0) l[i] = r else l.add(r)
        saveAll(c, l)
    }

    fun delete(c: Context, id: String) = saveAll(c, load(c).filter { it.id != id })

    fun create(c: Context, kind: SKind, key: String, label: String, enabled: Boolean): SoundRule {
        val src = when {
            kind == SKind.CATEGORY && key == "CALL" -> "builtin:ring"
            kind == SKind.CATEGORY && key == "ALARM" -> "builtin:alarm"
            kind == SKind.POPUP -> "builtin:pop"
            else -> "builtin:notify"
        }
        val stream = when {
            kind == SKind.CATEGORY && key == "CALL" -> 2
            kind == SKind.CATEGORY && key == "ALARM" -> 3
            kind == SKind.POPUP -> 0
            else -> 1
        }
        val r = SoundRule(UUID.randomUUID().toString(), kind, key, label, SoundCfg(enabled, src, 90, stream))
        upsert(c, r)
        return r
    }
}
