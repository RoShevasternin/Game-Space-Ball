package com.rostislav.spaceball.game.manager

import com.badlogic.gdx.Gdx
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.screens.*
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.runGDX

class NavigationManager(val game: GdxGame) {

    private val backStack = mutableListOf<String>()
    var key: Int? = null
        private set

    fun navigate(toScreenName: String, fromScreenName: String? = null, key: Int? = null) = runGDX {
        this.key = key

        game.updateScreen(getScreenByName(toScreenName))
        backStack.filter { name -> name == toScreenName }.onEach { name -> backStack.remove(name) }
        fromScreenName?.let { fromName ->
            backStack.filter { name -> name == fromName }.onEach { name -> backStack.remove(name) }
            backStack.add(fromName)
        }
    }

    fun back(key: Int? = null) = runGDX {
        this.key = key

        if (isBackStackEmpty()) exit() else game.updateScreen(getScreenByName(backStack.removeAt(backStack.lastIndex)))
    }


    fun exit() = runGDX { Gdx.app.exit() }


    fun isBackStackEmpty() = backStack.isEmpty()

    private fun getScreenByName(name: String): AdvancedScreen = when(name) {
        SpaceLoaderScreen   ::class.java.name -> SpaceLoaderScreen(game)
        SpaceMenuScreen     ::class.java.name -> SpaceMenuScreen(game)
        SpaceLevelsScreen   ::class.java.name -> SpaceLevelsScreen(game)
        AbstractGameScreen  ::class.java.name -> AbstractGameScreen(game)
        SpaceWinScreen      ::class.java.name -> SpaceWinScreen(game)

        else -> SpaceLevelsScreen(game)
    }

}