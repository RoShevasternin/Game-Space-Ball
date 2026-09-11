package com.rostislav.spaceball.game.screens

import com.rostislav.spaceball.services.ads.AppOpenGate
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.manager.MusicManager
import com.rostislav.spaceball.game.manager.SoundManager
import com.rostislav.spaceball.game.manager.SpriteManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SpaceLoaderScreen(override val game: GdxGame) : AdvancedScreen() {

    private val progressFlow     = MutableStateFlow(0f)
    private var isFinishLoading  = false
    private var isFinishProgress = false

    override fun show() {
        loadSplashAssets()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        super.show()
        loadAssets()
        collectProgress()
    }

    override fun render(delta: Float) {
        super.render(delta)
        loadingAssets()
        isFinish()
    }

    override fun AdvancedStage.addActorsOnStageUI() {}

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------

    private fun loadSplashAssets() {
        with(game.spriteManager) {
            loadableTextureList = SpriteManager.EnumTexture.entries.take(4).map { it.data }.toMutableList()
            loadTexture()
        }
        game.assetManager.finishLoading()
        game.spriteManager.initTexture()
    }

    private fun loadAssets() {
        with(game.spriteManager) {
            loadableAtlasList = SpriteManager.EnumAtlas.entries.map { it.data }.toMutableList()
            loadAtlas()
            loadableTextureList = SpriteManager.EnumTexture.entries.map { it.data }.toMutableList()
            loadTexture()
        }
        with(game.musicManager) {
            loadableMusicList = MusicManager.EnumMusic.entries.map { it.data }.toMutableList()
            load()
        }
        with(game.soundManager) {
            loadableSoundList = SoundManager.EnumSound.entries.map { it.data }.toMutableList()
            load()
        }
    }

    private fun initAssets() {
        game.spriteManager.initAtlasAndTexture()
        game.musicManager.init()
        game.soundManager.init()
    }

    private fun loadingAssets() {
        if (isFinishLoading.not()) {
            if (game.assetManager.update(16)) {
                isFinishLoading = true
                initAssets()
            }
            progressFlow.value = game.assetManager.progress
        }
    }

    private fun collectProgress() {
        coroutine?.launch {
            var progress = 0
            progressFlow.collect { p ->
                while (progress < (p * 100)) {
                    progress += 1
                    if (progress % 25 == 0) log("progress = $progress%")
                    if (progress == 100) isFinishProgress = true

                    // Плавна анімація прогресу (~1.6 с) — заодно дає рекламі час завантажитись
                    delay(16.milliseconds)
                }
            }
        }
    }

    private fun isFinish() {
        if (isFinishProgress.not()) return

        // Тримаємо лоадер, доки App Open реклама не закриється (або не спрацює таймаут).
        // Інакше реклама вилітає вже поверх меню.
        if (AppOpenGate.isResolved.not()) return

        isFinishProgress = false

        stageUI.root.animHide(TIME_ANIM_ALPHA) {
            game.activity.lottie.hideLoader()

            val debugLevel  = DebugFlags.startLevel(game.activity)
            val debugScreen = when (DebugFlags.startScreen(game.activity)) {
                "shop"   -> SpaceShopScreen::class.java.name
                "levels" -> SpaceLevelsScreen::class.java.name
                else     -> null
            }
            when {
                debugLevel >= 0 -> {
                    AbstractGameScreen.level = debugLevel
                    AbstractGameScreen.isDaily = false
                    game.navigationManager.navigate(AbstractGameScreen::class.java.name, SpaceMenuScreen::class.java.name)
                }
                debugScreen != null -> game.navigationManager.navigate(debugScreen, SpaceMenuScreen::class.java.name)
                else -> game.navigationManager.navigate(SpaceMenuScreen::class.java.name)
            }
        }
    }


}