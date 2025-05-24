package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
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
import com.rostislav.spaceball.game.utils.runGDX

class AbstractGameScreen(override val game: GdxGame): AdvancedBox2dScreen(WorldUtil()) {

    companion object {
        var level: Int = 0
    }

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
    private val bPlatList = List(5) { BPlat(this) }
    private val bTriList  = List(3) { BTriangle(this) }
    private val bStaList  = List(3) { BStar(this) }

    // Fields
    private val downColor   = listOf(
        Color.valueOf("C5D5F3"),
        Color.valueOf("0ABCE2"),
        Color.valueOf("D48FCC"),
        Color.valueOf("CA6068"),
    )[level]
    private val randomIndex = (0..3).random()


    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[level].region)
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
                        stageUI.root.animHide(TIME_ANIM_ALPHA) {
                            game.navigationManager.navigate(AbstractGameScreen::class.java.name)
                        }
                    }
                    BodyId.STAR -> {
                        game.soundUtil.apply { play(BONUS, 10f) }
                        game.starsUtil.apply { update(stars+1) }

                        runGDX {
                            it.destroy()
                            starCount++

                            if (starCount == 3) {
                                isPauseWorld = true
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
        val positionList = listOf(
            listOf(
                Vector2(812f, 671f),
                Vector2(206f, 847f),
                Vector2(687f, 1152f),
                Vector2(96f, 1473f),
                Vector2(777f, 1683f),
            ),
            listOf(
                Vector2(684f, 765f),
                Vector2(96f, 960f),
                Vector2(415f, 1152f),
                Vector2(739f, 1410f),
                Vector2(58f, 1588f),
            ),
            listOf(
                Vector2(738f, 874f),
                Vector2(108f, 1149f),
                Vector2(772f, 1191f),
                Vector2(314f, 1382f),
                Vector2(590f, 1648f),
            ),
            listOf(
                Vector2(45f, 732f),
                Vector2(471f, 1011f),
                Vector2(16f, 1311f),
                Vector2(778f, 1314f),
                Vector2(612f, 1744f),
            ),
        )[randomIndex]
        val size         = Vector2(250f, 28f)

        bPlatList.onEachIndexed { index, bPlat ->
            bPlat.apply {
                id = BodyId.BORDERS
                collisionList.add(BodyId.BALL)
            }
            bPlat.create(positionList[index], size)
        }
    }

    private fun createB_TriList() {
        val positionList = listOf(
            listOf(
                Vector2(879f, 698f),
                Vector2(243f, 874f),
                Vector2(271f, 1500f),
            ),
            listOf(
                Vector2(435f, 1178f),
                Vector2(835f, 1436f),
                Vector2(205f, 1614f),
            ),
            listOf(
                Vector2(164f, 1173f),
                Vector2(805f, 1217f),
                Vector2(603f, 1673f),
            ),
            listOf(
                Vector2(571f, 1036f),
                Vector2(146f, 1338f),
                Vector2(749f, 1770f),
            ),
        )[randomIndex]
        val size         = Vector2(58f, 70f)

        bTriList.onEachIndexed { index, bTri ->
            bTri.apply {
                id = BodyId.TRI
                collisionList.add(BodyId.BALL)
            }
            bTri.create(positionList[index], size)
        }
    }

    private fun createB_StaList() {
        val positionList = listOf(
            listOf(
                Vector2(972f, 698f),
                Vector2(145f, 1499f),
                Vector2(920f, 1710f),
            ),
            listOf(
                Vector2(192f, 1000f),
                Vector2(918f, 1439f),
                Vector2(86f, 1615f),
            ),
            listOf(
                Vector2(257f, 1176f),
                Vector2(908f, 1220f),
                Vector2(711f, 1678f),
            ),
            listOf(
                Vector2(28f, 1341f),
                Vector2(865f, 1341f),
                Vector2(613f, 1771f),
            ),
        )[randomIndex]
        val size         = Vector2(70f, 67f)

        bStaList.onEachIndexed { index, bSta ->
            bSta.apply {
                id = BodyId.STAR
                collisionList.add(BodyId.BALL)
            }
            bSta.create(positionList[index], size)
        }
    }

}