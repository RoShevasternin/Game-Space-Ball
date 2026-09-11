package com.rostislav.spaceball.game.utils

import com.rostislav.spaceball.BuildConfig

/** Перемикачі для розробки. У release-збірці все вимкнено. */
object DebugFlags {
    /** Усі рівні відкриті — щоб тестувати далекі планети без проходження. */
    val unlockAllLevels = BuildConfig.DEBUG

    /**
     * Одразу відкрити рівень, переданий у intent:
     *   adb shell am start -n com.rostislav.spaceball/.MainActivity --ei level 34
     * (−1 = звичайний запуск у меню). Тільки для debug-збірки.
     */
    fun startLevel(activity: android.app.Activity): Int =
        if (BuildConfig.DEBUG) activity.intent?.getIntExtra("level", -1) ?: -1 else -1

    /** `--ez win true`: автоматично виграти рівень через 2 с (перевірка екрана перемоги). */
    fun autoWin(activity: android.app.Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("win", false) ?: false)

    /** `--ez die true`: загинути через 2 с (перевірка оверлею «Continue»). */
    fun autoDie(activity: android.app.Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("die", false) ?: false)

    /** `--ez tut true`: показати туторіал, навіть якщо рівень уже пройдено. */
    fun forceTutorial(activity: android.app.Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("tut", false) ?: false)

    /**
     * `--es play "D0,R0.6,D2.1,L2.3"`: скриптовані натискання для тестів фізики
     * (U — короткий стрибок, D — високий, L/R — вбік; число — секунди від старту рівня).
     * Через adb `input tap` затримка 0.4–1 с, тож руками відтворити стрибки неможливо.
     */
    fun playScript(activity: android.app.Activity): List<Pair<Char, Float>> {
        if (!BuildConfig.DEBUG) return emptyList()
        val raw = activity.intent?.getStringExtra("play") ?: return emptyList()
        return raw.split(',').mapNotNull { token ->
            val t = token.trim()
            if (t.length < 2) null else t[0].uppercaseChar() to (t.substring(1).toFloatOrNull() ?: return@mapNotNull null)
        }
    }
}
