package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Зірки за весь час гри. Це і рахунок у лідерборді, і «валюта» магазину
 * (баланс = [stars] − витрачені, див. [SkinUtil]).
 */
class StarUtil(val coroutine: CoroutineScope) {

    private var value = 0L

    val stars get() = if (DebugFlags.shots) DebugFlags.SHOTS_STARS else value

    /** Зростає при кожній зміні — щоб екрани помічали оновлення. */
    var revision = 0
        private set

    init {
        coroutine.launch {
            value = GameDataStoreManager.Stars.get() ?: 0
            revision++
            log("Store Stars = $value")
        }
    }

    fun update(result: Long) {
        if (DebugFlags.shots) return
        value = result
        revision++
        coroutine.launch {
            GameDataStoreManager.Stars.update { result }
        }
    }

    fun add(amount: Long) = update(stars + amount)

}
