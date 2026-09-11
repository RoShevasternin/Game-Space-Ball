package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Щоденний рівень: бонус видається один раз на добу (за UTC-днем). */
class DailyUtil(private val coroutine: CoroutineScope) {

    companion object {
        const val BONUS_STARS = 5

        fun todayEpochDay(): Long = System.currentTimeMillis() / 86_400_000L
    }

    private var doneDay = -1L

    var revision = 0
        private set

    init {
        coroutine.launch {
            doneDay = GameDataStoreManager.DailyDone.get() ?: -1L
            revision++
        }
    }

    fun isDoneToday(): Boolean = doneDay == todayEpochDay()

    fun markDone() {
        doneDay = todayEpochDay()
        revision++
        coroutine.launch { GameDataStoreManager.DailyDone.update { doneDay } }
    }
}
