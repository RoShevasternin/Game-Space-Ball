package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.box2d.WorldUtil
import com.rostislav.spaceball.game.utils.Layout
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.*
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.region

class SpaceLevelsScreen(override val game: GdxGame): AdvancedBox2dScreen(WorldUtil()) {

    // Actor
    private val pListImg = List(4) { Image(game.assetsAllUtil.pList[it]) }
    private val back     = Image(game.assetsAllUtil.BACK)

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addPListImg()
        addBack()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addPListImg() {
        val posList = listOf(
            Pos(184f, 1319f, 390f, 390f),
            Pos(654f, 1037f, 305f, 305f),
            Pos(65f, 618f, 453f, 453f),
            Pos(467f, 91f, 527f, 527f),
        )
        pListImg.onEachIndexed { index, img ->
            addActor(img)
            posList[index].also { p ->
                img.setBounds(p.x, p.y, p.w, p.h)
                img.setOnClickListener(game.soundUtil) {
                    img.disable()
                    stageUI.root.animHide(TIME_ANIM_ALPHA) {
                        AbstractGameScreen.level = index
                        game.navigationManager.navigate(AbstractGameScreen::class.java.name, SpaceLevelsScreen::class.java.name)
                    }
                }
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

    data class Pos(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
    )
}