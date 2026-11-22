package com.example.gestureanim

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** दूसरे ऐप की नोटिफिकेशन आने पर हमारा चुना हुआ साउंड बजाता है */
class NotifService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val lastPlay = HashMap<String, Long>()

    private fun catKey(cat: String?): String? = when (cat) {
        Notification.CATEGORY_CALL -> "CALL"
        Notification.CATEGORY_MESSAGE -> "MESSAGE"
        Notification.CATEGORY_EMAIL -> "EMAIL"
        Notification.CATEGORY_ALARM -> "ALARM"
        Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT -> "REMINDER"
        Notification.CATEGORY_SOCIAL -> "SOCIAL"
        Notification.CATEGORY_PROMO -> "PROMO"
        else -> null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isCall = n.category == Notification.CATEGORY_CALL
        val now = SystemClock.uptimeMillis()

        if (isCall) {
            // इनकमिंग कॉल: fullScreenIntent हो या ongoing न हो; वरना (चालू कॉल) लूप रोक दो
            val incoming = n.fullScreenIntent != null || !sbn.isOngoing
            if (!incoming) {
                SoundPlayer.stopLoop(sbn.key)
                return
            }
        } else {
            if (sbn.isOngoing) return
            if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && lastPlay.containsKey(sbn.key)) return
            if (now - (lastPlay[sbn.key] ?: 0L) < 3000L) return
        }
        if (lastPlay.size > 200) lastPlay.clear()
        lastPlay[sbn.key] = now

        val rules = SoundStore.load(this).filter { it.snd.enabled }
        val ck = catKey(n.category)
        val r = rules.firstOrNull { it.kind == SKind.APP && it.key == sbn.packageName }
            ?: rules.firstOrNull { it.kind == SKind.CATEGORY && it.key == ck }
            ?: rules.firstOrNull { it.kind == SKind.ANY }
            ?: return

        if (isCall) {
            SoundPlayer.play(this, r.snd, sbn.key)
            handler.postDelayed({ SoundPlayer.stopLoop(sbn.key) }, 45000)
        } else {
            SoundPlayer.play(this, r.snd)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        SoundPlayer.stopLoop(sbn.key)
    }
}
