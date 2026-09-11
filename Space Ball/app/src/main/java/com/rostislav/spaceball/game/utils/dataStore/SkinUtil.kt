package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.skin.BallSkin
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Магазин скінів: що куплено, що обрано, скільки зірок витрачено.
 * Баланс рахується від [StarUtil.stars], щоб лідерборд не зменшувався від покупок.
 */
class SkinUtil(private val coroutine: CoroutineScope, private val starUtil: StarUtil) {

    var selected = BallSkin.CLASSIC
        private set

    private val owned = mutableSetOf(BallSkin.CLASSIC)

    var spent = 0L
        private set

    var revision = 0
        private set

    val balance get() = (starUtil.stars - spent).coerceAtLeast(0L)

    init {
        coroutine.launch {
            spent    = GameDataStoreManager.SpentStars.get() ?: 0L
            selected = BallSkin.of(GameDataStoreManager.Skin.get() ?: 0)
            GameDataStoreManager.OwnedSkins.get()
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.forEach { owned.add(BallSkin.of(it)) }
            owned.add(selected)
            revision++
            log("Store skins: selected=$selected owned=${owned.size} spent=$spent")
        }
    }

    fun isOwned(skin: BallSkin) = skin in owned

    fun canAfford(skin: BallSkin) = balance >= skin.price

    /** Купує скін, якщо вистачає зірок. Повертає true при успіху. */
    fun buy(skin: BallSkin): Boolean {
        if (isOwned(skin)) return true
        if (!canAfford(skin)) return false

        spent += skin.price
        owned.add(skin)
        revision++
        coroutine.launch {
            GameDataStoreManager.SpentStars.update { spent }
            GameDataStoreManager.OwnedSkins.update { owned.joinToString(",") { it.ordinal.toString() } }
        }
        return true
    }

    fun select(skin: BallSkin) {
        if (!isOwned(skin)) return
        selected = skin
        revision++
        coroutine.launch { GameDataStoreManager.Skin.update { skin.ordinal } }
    }
}
