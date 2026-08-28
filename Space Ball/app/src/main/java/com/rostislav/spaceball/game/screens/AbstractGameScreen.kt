package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.rostislav.spaceball.ads.AdPolicy
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.button.AButton
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.box2d.BodyId
import com.rostislav.spaceball.game.box2d.WorldUtil
import com.rostislav.spaceball.game.box2d.bodies.BBall
import com.rostislav.spaceball.game.box2d.bodies.BHorizontal
import com.rostislav.spaceball.game.box2d.bodies.BPlat
import com.rostislav.spaceball.game.box2d.bodies.BStar
import com.rostislav.spaceball.game.box2d.bodies.BTriangle
import com.rostislav.spaceball.game.box2d.bodiesGroup.BGBorders
import com.rostislav.spaceball.game.box2d.destroyAll
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.runGDX

class AbstractGameScreen(override val game: GdxGame): AdvancedBox2dScreen(WorldUtil()) {

    companion object {
        var level: Int = 0

        /** Візуальна тема (фон, кольори, спрайти зірки/трикутника) — 4 набори по колу. */
        val theme: Int get() = level % 4
    }

    private val levelData = LevelGenerator.get(level)

    // Actor
    private val aUp    = AButton(this, AButton.Static.Type.UP)
    private val aLeft  = AButton(this, AButton.Static.Type.LEFT)
    private val aRight = AButton(this, AButton.Static.Type.RIGHT)
    private val aUpUp  = AButton(this, AButton.Static.Type.UPUP)
    private val back   = Image(game.assetsAllUtil.BACK)

    // BodyGroup
    private val bgBorders = BGBorders(this)

    // Body
    private val bDown = BHorizontal(this)
    private val bBall = BBall(this)
    private val bPlatList = List(levelData.plats.size) { BPlat(this) }
    private val bTriList  = List(levelData.tris.size)  { BTriangle(this) }
    private val bStaList  = List(levelData.stars.size) { BStar(this) }

    // Fields
    private val downColor   = listOf(
        Color.valueOf("C5D5F3"),
        Color.valueOf("0ABCE2"),
        Color.valueOf("D48FCC"),
        Color.valueOf("CA6068"),
    )[theme]


    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[theme].region)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        createBG_Borders()
        createB_Down()
        createB_PlatList()
        createB_TriList()
        createB_StaList()

        createB_Ball()

        addButtons()
        addBack()
    }

    override fun dispose() {
        listOf(bgBorders).destroyAll()
        super.dispose()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addButtons() {
        addActors(aUp, aUpUp, aLeft, aRight)
        aUp.apply {
            setBounds(139f, 43f, 156f, 165f)
            setOnClickListener {
                bBall.body?.apply { applyLinearImpulse(Vector2(0f, 5f), worldCenter, true) }
            }
        }
        aUpUp.apply {
            setBounds(321f, 43f, 156f, 165f)
            setOnClickListener {
                bBall.body?.apply { applyLinearImpulse(Vector2(0f, 10f), worldCenter, true) }
            }
        }
        aLeft.apply {
            setBounds(669f, 43f, 156f, 165f)
            setOnClickListener {
                bBall.body?.apply { applyLinearImpulse(Vector2(-4f, 0f), worldCenter, true) }
            }
        }
        aRight.apply {
            setBounds(851f, 43f, 156f, 165f)
            setOnClickListener {
                bBall.body?.apply { applyLinearImpulse(Vector2(4f, 0f), worldCenter, true) }
            }
        }
    }

    private fun AdvancedStage.addBack() {
        addActor(back)
        back.setBounds(25f, 1776f, 119f, 119f)
        back.setOnClickListener(game.soundUtil) {
            stageUI.root.animHide(TIME_ANIM_ALPHA) {
                game.navigationManager.back()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Create Body Group
    // ------------------------------------------------------------------------
    private fun createBG_Borders() {
        bgBorders.create(0f,0f,1080f,1920f)
    }

    // ------------------------------------------------------------------------
    // Create Body
    // ------------------------------------------------------------------------
    private fun createB_Down() {
        val aDownImg = Image(drawerUtil.getRegion(downColor))
        stageUI.addActor(aDownImg)
        aDownImg.setBounds(0f, 250f, 1080f, 28f)

        bDown.apply {
            id = BodyId.BORDERS
            collisionList.add(BodyId.BALL)
        }
        bDown.create(0f,268f,1080f,10f)
    }

    private fun createB_Ball() {
        bBall.apply {
            id = BodyId.BALL
            collisionList.addAll(arrayOf(BodyId.BORDERS, BodyId.TRI, BodyId.STAR))

            var starCount = 0

            beginContactBlockArray.add(AbstractBody.ContactBlock {
                when(it.id) {
                    BodyId.BORDERS -> {
                        Gdx.input.vibrate(50)
                    }
                    BodyId.TRI -> {
                        game.soundUtil.apply { play(FAIL, 0.7f) }
                        Gdx.input.vibrate(200)

                        isPauseWorld = true
                        // Реклама — лише після кожної другої поразки
                        val showAd = AdPolicy.onLevelLost()
                        stageUI.root.animHide(TIME_ANIM_ALPHA) {
                            if (showAd) game.activity.showInterstitial {
                                runGDX { game.navigationManager.navigate(AbstractGameScreen::class.java.name) }
                            }
                            else game.navigationManager.navigate(AbstractGameScreen::class.java.name)
                        }
                    }
                    BodyId.STAR -> {
                        game.soundUtil.apply { play(BONUS, 10f) }

                        val newStars = game.starsUtil.stars + 1
                        game.starsUtil.update(newStars)
                        game.activity.submitLeaderboardScore(newStars)

                        runGDX {
                            it.destroy()
                            starCount++

                            if (starCount == bStaList.size) {
                                isPauseWorld = true
                                // Перемога — відкриваємо наступний рівень
                                game.levelUtil.complete(level)
                                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                                    game.navigationManager.navigate(SpaceWinScreen::class.java.name)
                                }
                            }
                        }
                    }
                }
            })
        }
        bBall.create(481f,382f,119f,119f)
    }

    private fun createB_PlatList() {
        bPlatList.onEachIndexed { index, bPlat ->
            bPlat.apply {
                id = BodyId.BORDERS
                collisionList.add(BodyId.BALL)
            }
            bPlat.create(levelData.plats[index], LevelGenerator.PLAT_SIZE)
        }
    }

    private fun createB_TriList() {
        bTriList.onEachIndexed { index, bTri ->
            bTri.apply {
                id = BodyId.TRI
                collisionList.add(BodyId.BALL)
            }
            bTri.create(levelData.tris[index], LevelGenerator.TRI_SIZE)
        }
    }

    private fun createB_StaList() {
        bStaList.onEachIndexed { index, bSta ->
            bSta.apply {
                id = BodyId.STAR
                collisionList.add(BodyId.BALL)
            }
            bSta.create(levelData.stars[index], LevelGenerator.STAR_SIZE)
        }
    }

}