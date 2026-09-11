package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.skin.BallSkin
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Магазин скінів: що куплено, що обрано, скільки зірок витрачено.
 * Баланс рахується від [StarUtil.stars], щоб лідерборд не зменшувався від покупок.
 */
class SkinUtil(private val coroutine: CoroutineScope, private val starUtil: StarUtil) {

    private var chosen = BallSkin.CLASSIC

    val selected get() = DebugFlags.skinOverride ?: chosen

    private val owned = mutableSetOf(BallSkin.CLASSIC)

    var spent = 0L
        private set

    var revision = 0
        private set

    val balance get() = if (DebugFlags.shots) DebugFlags.SHOTS_BALANCE else (starUtil.stars - spent).coerceAtLeast(0L)

    init {
        coroutine.launch {
            spent  = GameDataStoreManager.SpentStars.get() ?: 0L
            chosen = BallSkin.of(GameDataStoreManager.Skin.get() ?: 0)
            GameDataStoreManager.OwnedSkins.get()
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.forEach { owned.add(BallSkin.of(it)) }
            owned.add(chosen)
            revision++
            log("Store skins: selected=$chosen owned=${owned.size} spent=$spent")
        }
    }

    fun isOwned(skin: BallSkin) =
        if (DebugFlags.shots) skin in DebugFlags.SHOTS_OWNED || skin == selected else skin in owned

    fun canAfford(skin: BallSkin) = balance >= skin.price

    /** Купує скін, якщо вистачає зірок. Повертає true при успіху. */
    fun buy(skin: BallSkin): Boolean {
        if (isOwned(skin)) return true
        if (!canAfford(skin) || DebugFlags.shots) return false

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
        if (!isOwned(skin) || DebugFlags.shots) return
        chosen = skin
        revision++
        coroutine.launch { GameDataStoreManager.Skin.update { skin.ordinal } }
    }
}
