package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Прогрес проходження: який максимальний рівень відкрито і скільки зірок
 * (0..3) зібрано на кожному рівні в найкращій спробі.
 *
 * Зберігається індекс (0 = відкритий лише перший рівень).
 * Рівень відкривається, коли гравець пройшов попередній.
 */
class LevelUtil(private val coroutine: CoroutineScope) {

    private var storedMax = 0

    /** Індекс останнього відкритого рівня. 0 означає, що доступний лише рівень №1. */
    val maxUnlocked get() = if (DebugFlags.shots) DebugFlags.SHOTS_CURRENT_LEVEL else storedMax

    /** Найкращий результат по рівнях, 0..3. */
    private val ratings = IntArray(LevelGenerator.LEVEL_COUNT)

    /** Зростає на 1 при кожній зміні — екранам зручно помічати, що дані підвантажились. */
    var revision = 0
        private set

    init {
        coroutine.launch {
            val stored = GameDataStoreManager.MaxLevel.get() ?: 0
            GameDataStoreManager.LevelStars.get()?.forEachIndexed { i, c ->
                if (i < ratings.size) ratings[i] = (c - '0').coerceIn(0, 3)
            }
            set(stored)
            log("Store MaxLevel = $storedMax, stars = ${ratings.sum()}")
        }
    }

    fun isUnlocked(index: Int): Boolean = DebugFlags.unlockAllLevels || index <= maxUnlocked

    fun rating(index: Int): Int =
        if (DebugFlags.shots) DebugFlags.shotsRating(index) else ratings.getOrElse(index) { 0 }

    /** Скільки зірок зібрано на планеті (з максимуму 45). */
    fun planetStars(planet: Planet): Int = (planet.firstLevel..planet.lastLevel).sumOf { rating(it) }

    /** Скільки рівнів планети пройдено. */
    fun planetCompleted(planet: Planet): Int = (planet.firstLevel..planet.lastLevel).count { rating(it) > 0 || it < maxUnlocked }

    val totalRatingStars get() = (0 until LevelGenerator.LEVEL_COUNT).sumOf { rating(it) }

    /**
     * Викликати після перемоги на рівні [completedIndex] із [stars] зібраними зірками.
     * Відкриває наступний рівень і запам'ятовує найкращий результат.
     */
    fun complete(completedIndex: Int, stars: Int) {
        if (DebugFlags.shots) return
        val safe = completedIndex.coerceIn(0, LevelGenerator.LEVEL_COUNT - 1)
        val newRating = stars.coerceIn(0, 3)
        if (newRating > ratings[safe]) {
            ratings[safe] = newRating
            revision++
            coroutine.launch {
                val encoded = ratings.joinToString("") { it.toString() }
                GameDataStoreManager.LevelStars.update { encoded }
            }
        }

        if (safe < storedMax) return

        val next = (safe + 1).coerceAtMost(LevelGenerator.LEVEL_COUNT - 1)
        if (next == storedMax) return

        set(next)
        coroutine.launch {
            GameDataStoreManager.MaxLevel.update { storedMax }
            log("Store MaxLevel updated = $storedMax")
        }
    }

    private fun set(value: Int) {
        storedMax = value.coerceIn(0, LevelGenerator.LEVEL_COUNT - 1)
        revision++
    }
}
