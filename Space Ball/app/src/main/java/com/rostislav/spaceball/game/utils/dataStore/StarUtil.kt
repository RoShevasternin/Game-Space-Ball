package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class StarUtil(val coroutine: CoroutineScope) {

    var stars = 0L
        private set

    init {
        coroutine.launch {
            stars = GameDataStoreManager.Stars.get() ?: 0
            log("Store Stars = $stars")
        }
    }

    fun update(result: Long) {
        coroutine.launch {
            stars = result
            GameDataStoreManager.Stars.update { stars }
        }
    }

}