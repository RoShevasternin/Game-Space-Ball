package com.rostislav.spaceball.game

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.rostislav.spaceball.game.manager.MusicManager
import com.rostislav.spaceball.game.manager.NavigationManager
import com.rostislav.spaceball.game.manager.SoundManager
import com.rostislav.spaceball.game.manager.SpriteManager
import com.rostislav.spaceball.game.manager.util.MusicUtil
import com.rostislav.spaceball.game.manager.util.SoundUtil
import com.rostislav.spaceball.game.manager.util.SpriteUtil
import com.rostislav.spaceball.game.screens.SpaceLoaderScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGame
import com.rostislav.spaceball.game.utils.dataStore.StarUtil
import com.rostislav.spaceball.game.utils.disposeAll
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class GdxGame(val activity: com.rostislav.spaceball.MainActivity) : AdvancedGame() {

    lateinit var assetManager     : AssetManager      private set
    lateinit var navigationManager: NavigationManager private set
    lateinit var spriteManager    : SpriteManager     private set
    lateinit var musicManager     : MusicManager      private set
    lateinit var soundManager     : SoundManager      private set

    val musicUtil  by lazy { MusicUtil()  }
    val soundUtil  by lazy { SoundUtil()  }
    val assetsLoaderUtil by lazy { SpriteUtil.LoaderAssets() }
    val assetsAllUtil    by lazy { SpriteUtil.AllAssets() }

    var backgroundColor = Color.BLACK
    val disposableSet   = mutableSetOf<Disposable>()

    val coroutine = CoroutineScope(Dispatchers.Default)

    val starsUtil = StarUtil(coroutine)

    override fun create() {
        navigationManager = NavigationManager(this)
        assetManager      = AssetManager()
        spriteManager     = SpriteManager(assetManager)
        musicManager      = MusicManager(assetManager)
        soundManager      = SoundManager(assetManager)

        navigationManager.navigate(SpaceLoaderScreen::class.java.name)
    }

    override fun render() {
        ScreenUtils.clear(backgroundColor)
        super.render()
    }

    override fun dispose() {
        try {
            log("dispose LibGDXGame")
            disposableSet.disposeAll()
            disposeAll(musicUtil, assetManager)
            super.dispose()
        } catch (e: Exception) { log("exception: ${e.message}") }
    }

}