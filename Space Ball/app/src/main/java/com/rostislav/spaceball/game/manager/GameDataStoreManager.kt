package com.rostislav.spaceball.game.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rostislav.spaceball.appContext
import kotlinx.coroutines.flow.first

object GameDataStoreManager: AbstractDataStore() {
    override val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "GAME_DATA_STORE")

    /** Зірки за весь час (лідерборд). Ніколи не зменшується. */
    object Stars: AbstractDataStore.DataStoreElement<Long>() {
        override val key = longPreferencesKey("stars")
    }

    /** Скільки зірок витрачено в магазині. Баланс = Stars − SpentStars. */
    object SpentStars: AbstractDataStore.DataStoreElement<Long>() {
        override val key = longPreferencesKey("spent_stars")
    }

    /** Індекс максимального відкритого рівня (0 = відкритий лише перший). */
    object MaxLevel: AbstractDataStore.DataStoreElement<Int>() {
        override val key = intPreferencesKey("max_level")
    }

    /** Найкращий результат по рівнях: рядок із цифр 0..3, по символу на рівень. */
    object LevelStars: AbstractDataStore.DataStoreElement<String>() {
        override val key = stringPreferencesKey("level_stars")
    }

    /** Обраний скін м'яча (ordinal [com.rostislav.spaceball.game.utils.skin.BallSkin]). */
    object Skin: AbstractDataStore.DataStoreElement<Int>() {
        override val key = intPreferencesKey("skin")
    }

    /** Куплені скіни: ordinal-и через кому. */
    object OwnedSkins: AbstractDataStore.DataStoreElement<String>() {
        override val key = stringPreferencesKey("owned_skins")
    }

    /** День (epoch day), за який уже отримано бонус щоденного рівня. */
    object DailyDone: AbstractDataStore.DataStoreElement<Long>() {
        override val key = longPreferencesKey("daily_done")
    }

    /** Ідентифікатори показаних підказок туторіалу, через кому. */
    object SeenHints: AbstractDataStore.DataStoreElement<String>() {
        override val key = stringPreferencesKey("seen_hints")
    }

}

abstract class AbstractDataStore {
    abstract val Context.dataStore: DataStore<Preferences>



    abstract inner class DataStoreElement<T> {
        abstract val key: Preferences.Key<T>

        open suspend fun collect(block: suspend (T?) -> Unit) {
            appContext.dataStore.data.collect { block(it[key]) }
        }

        open suspend fun update(block: suspend (T?) -> T) {
            appContext.dataStore.edit { it[key] = block(it[key]) }
        }

        open suspend fun get(): T? {
            return appContext.dataStore.data.first()[key]
        }
    }
}
