package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Прогрес проходження: які рівні пройдено (і з якою кількістю зірок 0..3) та що відкрито.
 *
 * Правила відкриття:
 *  - Туторіал (Lumina 1–2) — строго по черзі.
 *  - У відкритій планеті доступні всі пройдені рівні і [WINDOW] найближчих не пройдених —
 *    складний рівень можна обійти й повернутися пізніше.
 *  - Наступна планета відкривається, коли на попередній пройдено [PLANET_UNLOCK] рівнів.
 *  - Пропуск рівня за rewarded-рекламу ([skip]) рахується як проходження без зірок.
 */
class LevelUtil(private val coroutine: CoroutineScope) {

    companion object {
        /** Скільки не пройдених рівнів планети відкрито одночасно. */
        const val WINDOW = 3
        /** Скільки рівнів попередньої планети треба пройти, щоб відкрити наступну. */
        const val PLANET_UNLOCK = 10

        private const val TUTORIAL_LEVELS = 2
        private const val NOT_DONE = -1
        /** Префікс нового формату: '.' — не пройдено, '0'..'3' — пройдено з N зірками. */
        private const val FORMAT_V2 = "v2:"
    }

    private val results = IntArray(LevelGenerator.LEVEL_COUNT) { NOT_DONE }

    /** Зростає на 1 при кожній зміні — екранам зручно помічати, що дані підвантажились. */
    var revision = 0
        private set

    init {
        coroutine.launch {
            val encoded   = GameDataStoreManager.LevelStars.get()
            val legacyMax = GameDataStoreManager.MaxLevel.get() ?: 0

            val debugProgress = DebugFlags.progress
            when {
                debugProgress != null && debugProgress.startsWith(FORMAT_V2) -> parseV2(debugProgress)
                encoded != null && encoded.startsWith(FORMAT_V2) -> parseV2(encoded)
                encoded != null ->
                    // Старий формат: лише цифри, «0» означав і «без зірок», і «не пройдено».
                    // Рівні нижче за старий максимум точно пройдені.
                    encoded.forEachIndexed { i, c ->
                        if (i < results.size) {
                            val stars = (c - '0').coerceIn(0, 3)
                            results[i] = if (stars > 0 || i < legacyMax) stars else NOT_DONE
                        }
                    }
                else ->
                    // Версія гри до зірок на рівнях зберігала тільки максимальний відкритий рівень
                    for (i in 0 until legacyMax.coerceAtMost(results.size)) results[i] = 0
            }
            revision++
            log("Store levels: completed=${results.count { it >= 0 }}, stars=${results.sumOf { maxOf(it, 0) }}")
        }
    }

    private fun parseV2(encoded: String) {
        results.fill(NOT_DONE)
        encoded.removePrefix(FORMAT_V2).forEachIndexed { i, c ->
            if (i < results.size) results[i] = if (c in '0'..'3') c - '0' else NOT_DONE
        }
    }

    // ------------------------------------------------------------------------
    // Стан
    // ------------------------------------------------------------------------

    fun isCompleted(index: Int): Boolean =
        if (DebugFlags.shots) index < DebugFlags.SHOTS_CURRENT_LEVEL else results.getOrElse(index) { NOT_DONE } >= 0

    fun rating(index: Int): Int =
        if (DebugFlags.shots) DebugFlags.shotsRating(index) else maxOf(0, results.getOrElse(index) { NOT_DONE })

    /** Скільки рівнів планети пройдено. */
    fun planetCompleted(planet: Planet): Int = (planet.firstLevel..planet.lastLevel).count { isCompleted(it) }

    /** Скільки зірок зібрано на планеті (з максимуму 45). */
    fun planetStars(planet: Planet): Int = (planet.firstLevel..planet.lastLevel).sumOf { rating(it) }

    val totalRatingStars get() = (0 until LevelGenerator.LEVEL_COUNT).sumOf { rating(it) }

    fun isPlanetUnlocked(planet: Planet): Boolean =
        DebugFlags.unlockAllLevels || planet.ordinal == 0 ||
            planetCompleted(Planet.entries[planet.ordinal - 1]) >= PLANET_UNLOCK

    fun isUnlocked(index: Int): Boolean {
        if (DebugFlags.unlockAllLevels) return true
        if (index !in 0 until LevelGenerator.LEVEL_COUNT) return false
        val planet = Planet.of(index)
        if (!isPlanetUnlocked(planet)) return false
        if (isCompleted(index)) return true

        // Туторіал — по черзі
        if (planet == Planet.LUMINA && (0 until minOf(index, TUTORIAL_LEVELS)).any { !isCompleted(it) }) return false

        // Вікно: відкриті перші WINDOW не пройдених рівнів планети
        val notDoneBefore = (planet.firstLevel until index).count { !isCompleted(it) }
        return notDoneBefore < WINDOW
    }

    /** Поточний рівень — перший відкритий і не пройдений (підсвітка й автоскрол у списку). */
    val currentLevel: Int
        get() = if (DebugFlags.shots) DebugFlags.SHOTS_CURRENT_LEVEL
        else (0 until LevelGenerator.LEVEL_COUNT).firstOrNull { isUnlocked(it) && !isCompleted(it) }
            ?: (LevelGenerator.LEVEL_COUNT - 1)

    /** Куди веде «NEXT»: наступний відкритий не пройдений рівень, інакше просто наступний. */
    fun nextLevelAfter(index: Int): Int {
        val count = LevelGenerator.LEVEL_COUNT
        val candidates = ((index + 1) until count) + (0..index)
        return candidates.firstOrNull { isUnlocked(it) && !isCompleted(it) }
            ?: ((index + 1) % count).takeIf { isUnlocked(it) }
            ?: currentLevel
    }

    // ------------------------------------------------------------------------
    // Зміни
    // ------------------------------------------------------------------------

    /** Викликати після перемоги на рівні [index] із [stars] зібраними зірками. */
    fun complete(index: Int, stars: Int) {
        if (DebugFlags.shots || index !in results.indices) return
        val s = stars.coerceIn(0, 3)
        if (s > results[index]) {
            results[index] = s
            save()
        }
    }

    /** Пропуск рівня за rewarded-рекламу: рахується пройденим без зірок. */
    fun skip(index: Int) {
        if (DebugFlags.shots || index !in results.indices) return
        if (results[index] < 0) {
            results[index] = 0
            save()
        }
    }

    private fun save() {
        revision++
        if (DebugFlags.progress != null) return   // тестовий прогрес з intent не зберігаємо
        val encoded = FORMAT_V2 + results.joinToString("") { if (it < 0) "." else it.toString() }
        coroutine.launch {
            GameDataStoreManager.LevelStars.update { encoded }
            log("Store levels updated: $encoded")
        }
    }
}
