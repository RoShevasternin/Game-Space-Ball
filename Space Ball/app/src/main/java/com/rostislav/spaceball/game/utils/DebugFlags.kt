package com.rostislav.spaceball.game.utils

import android.app.Activity
import com.rostislav.spaceball.BuildConfig
import com.rostislav.spaceball.game.utils.skin.BallSkin

/** Перемикачі для розробки. У release-збірці все вимкнено. */
object DebugFlags {

    /**
     * Режим зйомки скріншотів для маркету (`--ez shots true`): без банера й App Open реклами,
     * без підказок, із «гарним» прогресом (рейтинги, зірки, куплені скіни). Нічого не зберігає.
     */
    var shots = false
        private set

    /** Підмінити обраний скін без покупки (`--es skin NEBULA`). */
    var skinOverride: BallSkin? = null
        private set

    /**
     * `--ez unlock true`: відкрити всі рівні — для тестування далеких планет через adb.
     * Без прапорця debug-збірка (зокрема Run з Android Studio) поводиться як release.
     */
    var unlock = false
        private set

    /**
     * `--es progress "v2:333.3..."`: підмінити прогрес рівнів у пам'яті (не зберігається) —
     * щоб перевіряти правила відкриття, не стираючи дані гри на телефоні.
     */
    var progress: String? = null
        private set

    /** Викликати на самому початку MainActivity.onCreate. */
    fun init(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val intent = activity.intent ?: return
        shots = intent.getBooleanExtra("shots", false)
        unlock = intent.getBooleanExtra("unlock", false)
        progress = intent.getStringExtra("progress")
        skinOverride = intent.getStringExtra("skin")?.let { name ->
            BallSkin.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    /** Усі рівні відкриті — лише з явним `--ez unlock true`. */
    val unlockAllLevels get() = BuildConfig.DEBUG && unlock && !shots

    /**
     * Одразу відкрити рівень, переданий у intent:
     *   adb shell am start -n com.rostislav.spaceball/.MainActivity --ei level 34
     * (−1 = звичайний запуск у меню). Тільки для debug-збірки.
     */
    fun startLevel(activity: Activity): Int =
        if (BuildConfig.DEBUG) activity.intent?.getIntExtra("level", -1) ?: -1 else -1

    /** `--es screen shop|levels|menu`: після лоадера відкрити цей екран (для скріншотів). */
    fun startScreen(activity: Activity): String? =
        if (BuildConfig.DEBUG) activity.intent?.getStringExtra("screen") else null

    /** `--ez win true`: автоматично виграти рівень через 2 с (перевірка екрана перемоги). */
    fun autoWin(activity: Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("win", false) ?: false)

    /** `--ez die true`: загинути через 2 с (перевірка оверлею «Continue»). */
    fun autoDie(activity: Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("die", false) ?: false)

    /** `--ez tut true`: показати туторіал, навіть якщо рівень уже пройдено. */
    fun forceTutorial(activity: Activity): Boolean =
        BuildConfig.DEBUG && (activity.intent?.getBooleanExtra("tut", false) ?: false)

    /**
     * `--ef freeze 4.2`: через N с після старту рівня зупинити фізику й частинки —
     * кадр «застигає», і його можна спокійно зняти скріншотом.
     */
    fun freezeAt(activity: Activity): Float =
        if (BuildConfig.DEBUG) activity.intent?.getFloatExtra("freeze", -1f) ?: -1f else -1f

    /**
     * `--es play "D0.0,R0.6,D2.1,L2.3"`: скриптовані натискання для тестів фізики
     * (U — короткий стрибок, D — високий, L/R — вбік; число — секунди від старту рівня).
     * Через adb `input tap` затримка 0.4–1 с, тож руками відтворити стрибки неможливо.
     */
    fun playScript(activity: Activity): List<Pair<Char, Float>> {
        if (!BuildConfig.DEBUG) return emptyList()
        val raw = activity.intent?.getStringExtra("play") ?: return emptyList()
        return raw.split(',').mapNotNull { token ->
            val t = token.trim()
            if (t.length < 2) null else t[0].uppercaseChar() to (t.substring(1).toFloatOrNull() ?: return@mapNotNull null)
        }
    }

    // ------------------------------------------------------------------------
    // Фейковий прогрес для скріншотів
    // ------------------------------------------------------------------------

    /** Поточний (перший не пройдений) рівень у режимі зйомки — 23-й. */
    const val SHOTS_CURRENT_LEVEL = 22
    const val SHOTS_STARS         = 186L
    const val SHOTS_BALANCE       = 120L
    val SHOTS_OWNED = setOf(BallSkin.CLASSIC, BallSkin.NEBULA, BallSkin.VERDANT, BallSkin.MAGMA)

    fun shotsRating(index: Int): Int = when {
        index >= SHOTS_CURRENT_LEVEL -> 0
        index % 7 == 3 || index % 11 == 5 -> 2
        else -> 3
    }
}
