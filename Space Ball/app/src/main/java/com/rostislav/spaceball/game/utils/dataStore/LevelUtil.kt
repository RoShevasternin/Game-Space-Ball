package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Прогрес проходження: який максимальний рівень відкрито.
 *
 * Зберігається індекс (0 = відкритий лише перший рівень).
 * Рівень відкривається, коли гравець пройшов попередній.
 */
class LevelUtil(private val coroutine: CoroutineScope) {

    /** Індекс останнього відкритого рівня. 0 означає, що доступний лише рівень №1. */
    var maxUnlocked = 0
        private set

    /** Зростає на 1 при кожній зміні — екранам зручно помічати, що дані підвантажились. */
    var revision = 0
        private set

    init {
        coroutine.launch {
            val stored = GameDataStoreManager.MaxLevel.get() ?: 0
            set(stored)
            log("Store MaxLevel = $maxUnlocked")
        }
    }

    fun isUnlocked(index: Int): Boolean = index <= maxUnlocked

    /**
     * Викликати після перемоги на рівні [completedIndex].
     * Відкриває наступний рівень, якщо гравець дійшов до межі свого прогресу.
     */
    fun complete(completedIndex: Int) {
        if (completedIndex < maxUnlocked) return

        val next = (completedIndex + 1).coerceAtMost(LevelGenerator.LEVEL_COUNT - 1)
        if (next == maxUnlocked) return

        set(next)
        coroutine.launch {
            GameDataStoreManager.MaxLevel.update { maxUnlocked }
            log("Store MaxLevel updated = $maxUnlocked")
        }
    }

    private fun set(value: Int) {
        maxUnlocked = value.coerceIn(0, LevelGenerator.LEVEL_COUNT - 1)
        revision++
    }
}