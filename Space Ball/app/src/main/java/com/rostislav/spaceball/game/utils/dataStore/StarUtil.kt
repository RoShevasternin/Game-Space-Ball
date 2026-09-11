package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Зірки за весь час гри. Це і рахунок у лідерборді, і «валюта» магазину
 * (баланс = [stars] − витрачені, див. [SkinUtil]).
 */
class StarUtil(val coroutine: CoroutineScope) {

    var stars = 0L
        private set

    /** Зростає при кожній зміні — щоб екрани помічали оновлення. */
    var revision = 0
        private set

    init {
        coroutine.launch {
            stars = GameDataStoreManager.Stars.get() ?: 0
            revision++
            log("Store Stars = $stars")
        }
    }

    fun update(result: Long) {
        stars = result
        revision++
        coroutine.launch {
            GameDataStoreManager.Stars.update { result }
        }
    }

    fun add(amount: Long) = update(stars + amount)

}
