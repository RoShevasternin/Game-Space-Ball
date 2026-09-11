package com.rostislav.spaceball.services.ads

import android.os.Handler
import android.os.Looper
import com.rostislav.spaceball.util.log

/**
 * «Шлагбаум» для екрана завантаження при холодному старті.
 *
 * Проблема: App Open реклама вантажиться 1–3 с, а гра готова швидше — і реклама
 * вилітає вже поверх меню. Просто поставити фіксований delay погано: якщо реклами
 * не буде взагалі, гравець дарма дивиться на спінер.
 *
 * Тому лоадер чекає саме на сигнал: [isResolved] стає true, коли реклама
 * закрилась, не змогла показатись, не завантажилась — або спрацював таймаут.
 */
object AppOpenGate {

    /** Скільки максимум тримаємо лоадер заради реклами. */
    private const val TIMEOUT_MS = 4_000L

    @Volatile
    var isResolved = true
        private set

    private var isArmed = false
    private val handler = Handler(Looper.getMainLooper())

    /** Увімкнути очікування (викликається один раз при старті процесу). */
    fun arm() {
        if (isArmed) return
        isArmed    = true
        isResolved = false

        log("AppOpenGate: armed, timeout ${TIMEOUT_MS}ms")
        handler.postDelayed({ resolve("timeout") }, TIMEOUT_MS)
    }

    /** Можна пускати гравця далі. */
    fun resolve(reason: String) {
        if (isResolved) return
        isResolved = true
        handler.removeCallbacksAndMessages(null)
        log("AppOpenGate: resolved ($reason)")
    }
}