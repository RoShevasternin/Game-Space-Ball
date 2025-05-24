package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.region

class SpaceWinScreen(override val game: GdxGame): AdvancedScreen() {

    // Actor
    private val youWinImg = Image(game.assetsAllUtil.you_win)

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addWinImg()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addWinImg() {
        addActor(youWinImg)
        youWinImg.setBounds(119f, 653f, 842f, 968f)

        val menu = Actor()
        val next = Actor()
        addActors(menu, next)
        menu.apply {
            setBounds(119f, 653f, 408f, 195f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.back()
                }
            }
        }
        next.apply {
            setBounds(553f, 653f, 408f, 195f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    AbstractGameScreen.level = (0..3).random()
                    game.navigationManager.navigate(AbstractGameScreen::class.java.name)
                }
            }
        }

    }

}