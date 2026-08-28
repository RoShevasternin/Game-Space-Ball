package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.utils.Layout
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setBounds
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.game.utils.runGDX

class SpaceMenuScreen(override val game: GdxGame): AdvancedScreen() {

    private val params = FontParameter().setCharacters(FontParameter.CharType.NUMBERS).setSize(62)
    private val font   = fontGenerator_InterBold.generateFont(params)

    private val btns          = Image(game.assetsAllUtil.btns)
    private val stars         = Image(game.assetsAllUtil.STARS)
    private val starsLbl      = Label("${game.starsUtil.stars}", Label.LabelStyle(font, Color.valueOf("C5D5F3")))

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addBtns()
        addStars()

        // Безпечна відправка: якщо не авторизований — рахунок відправиться після входу
        game.activity.submitLeaderboardScore(game.starsUtil.stars)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addBtns() {
        addActor(btns)
        btns.setBounds(240f, 612f, 599f, 697f)

        val levels  = Actor()
        val records = Actor()
        val exit    = Actor()
        addActors(levels, records, exit)
        levels.apply {
            setBounds(240f, 1130f, 599f, 179f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.navigate(SpaceLevelsScreen::class.java.name, SpaceMenuScreen::class.java.name)
                }
            }
        }
        records.apply {
            setBounds(240f, 871f, 599f, 179f)
            setOnClickListener(game.soundUtil) { game.activity.showLeaderboard() }
        }
        exit.apply {
            setBounds(240f, 612f, 599f, 179f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.exit()
                }
            }
        }

    }

    private fun AdvancedStage.addStars() {
        addActor(stars)
        stars.setBounds(305f, 177f, 470f, 251f)

        addActor(starsLbl)
        starsLbl.apply {
            setBounds(Layout.stars)
            setAlignment(Align.center)
        }
    }

}