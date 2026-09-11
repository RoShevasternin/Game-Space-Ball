package com.rostislav.spaceball.services.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ініціалізує AdMob SDK один раз (на фоновому потоці, як рекомендує Google)
 * і виконує відкладені колбеки, коли SDK готовий.
 */
object MobileAdsInitializer {

    @Volatile
    var isInitialized = false
        private set

    private val pending = mutableListOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false

    fun initialize(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(context.applicationContext) {
                log("AdMob initialized")
                val toRun: List<() -> Unit>
                synchronized(this@MobileAdsInitializer) {
                    isInitialized = true
                    toRun = pending.toList()
                    pending.clear()
                }
                toRun.forEach { block -> mainHandler.post(block) }
            }
        }
    }

    /** Виконує block на main-потоці, коли SDK ініціалізовано (або одразу, якщо вже готовий). */
    fun onReady(block: () -> Unit) {
        val runNow: Boolean
        synchronized(this) {
            runNow = isInitialized
            if (!runNow) pending.add(block)
        }
        if (runNow) mainHandler.post(block)
    }
}