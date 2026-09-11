package com.rostislav.spaceball.services.tiktok

import android.app.Application
import com.rostislav.spaceball.BuildConfig
import com.rostislav.spaceball.game.utils.gdxGame
import com.rostislav.spaceball.util.log
import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.TTBaseEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TikTok Business SDK — атрибуція встановлень із реклами в TikTok.
 *
 * App ID та секрет беруться з BuildConfig, куди Gradle підставляє їх із
 * gradle.properties (`tiktok.app.id`, `tiktok.app.secret`) — міняти ключі там, а не в коді.
 */
object TikTokManager {

    private val once = AtomicBoolean(true)

    fun initialize(app: Application) {
        if (!once.getAndSet(false)) return

        val ttAppIds  = BuildConfig.TIKTOK_APP_ID
        val appSecret = BuildConfig.TIKTOK_APP_SECRET
        if (ttAppIds.isBlank() || appSecret.isBlank()) {
            log("TikTok SDK: немає ключів у gradle.properties — ініціалізацію пропущено")
            return
        }

        val config = TikTokBusinessSdk.TTConfig(app, appSecret)
            .setAppId(app.packageName)
            .setTTAppId(ttAppIds)
            .setLogLevel(
                if (BuildConfig.DEBUG) TikTokBusinessSdk.LogLevel.DEBUG
                else TikTokBusinessSdk.LogLevel.NONE
            )
            .apply { if (BuildConfig.DEBUG) openDebugMode() }

        TikTokBusinessSdk.initializeSdk(config, object : TikTokBusinessSdk.TTInitCallback {
            override fun success() {
                log("TikTok SDK initialized SUCCESSFULLY, ids=$ttAppIds")
            }

            override fun fail(code: Int, msg: String) {
                log("TikTok SDK FAILED: $code $msg")
            }
        })
    }

    fun sendTestEvent() {
        gdxGame.activity.runOnUiThread {
            val event = TTBaseEvent.newBuilder("test_event")
                .addProperty("ts", System.currentTimeMillis())
                .build()
            TikTokBusinessSdk.trackTTEvent(event)
            TikTokBusinessSdk.flush()   // ← форсимо негайну відправку
            log("TikTok test event sent")
        }
    }
}
