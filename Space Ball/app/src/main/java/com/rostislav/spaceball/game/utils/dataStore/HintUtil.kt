package com.rostislav.spaceball.game.utils.dataStore

import com.rostislav.spaceball.game.manager.GameDataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Які підказки туторіалу гравець уже бачив — щоб не показувати їх повторно. */
class HintUtil(private val coroutine: CoroutineScope) {

    private val seen = mutableSetOf<String>()

    init {
        coroutine.launch {
            GameDataStoreManager.SeenHints.get()?.split(',')?.filter { it.isNotBlank() }?.let { seen.addAll(it) }
        }
    }

    fun isSeen(id: String) = id in seen

    fun markSeen(id: String) {
        if (!seen.add(id)) return
        val encoded = seen.joinToString(",")
        coroutine.launch { GameDataStoreManager.SeenHints.update { encoded } }
    }
}
