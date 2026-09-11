package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.game.ABall
import com.rostislav.spaceball.game.actors.ui.PillButton
import com.rostislav.spaceball.game.effects.FxLayer
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.effects.StarfieldActor
import com.rostislav.spaceball.game.utils.Layout
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setBounds
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.dataStore.DailyUtil
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.region

class SpaceMenuScreen(override val game: GdxGame): AdvancedScreen() {

    private val font      = fontGenerator_InterBold.generateFont(FontParameter().setCharacters(FontParameter.CharType.NUMBERS).setSize(62).setOutline(3f).setShadow())
    private val fontBtn   = fontGenerator_InterBold.generateFont(FontParameter().ui(44))
    private val fontTiny  = fontGenerator_InterBold.generateFont(FontParameter().ui(26))
    private val fontLogo  = fontGenerator_InterBold.generateFont(FontParameter().ui(96, outline = 5f).setOutline(5f, Color.valueOf("FF5CC8")).setShadow(0, 6, Color(0.2f, 0f, 0.4f, 0.7f)))

    private val fx = FxSystem(drawerUtil)

    private val btns          = Image(game.assetsAllUtil.btns)
    private val stars         = Image(game.assetsAllUtil.STARS)
    private val starsLbl      = Label("${game.starsUtil.stars}", Label.LabelStyle(font, Color.valueOf("F4F0FF")))

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        animateBackground()
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addActor(StarfieldActor(drawerUtil, seed = 5L, count = 60))
        addActor(FxLayer(fx, front = true))
        addLogo()
        addBallPreview()
        addBtns()
        addExtraBtns()
        addStars()
        addDust()

        // Безпечна відправка: якщо не авторизований — рахунок відправиться після входу
        game.activity.submitLeaderboardScore(game.starsUtil.stars)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addLogo() {
        val logo = Label("SPACE BALL", Label.LabelStyle(fontLogo, Color.valueOf("FFFFFF"))).apply {
            setBounds(0f, 1780f, 1080f, 110f); setAlignment(Align.center)
            setOrigin(Align.center)
        }
        addActor(logo)
        logo.addAction(Actions.forever(Actions.sequence(
            Actions.scaleTo(1.03f, 1.03f, 1.8f, Interpolation.sine),
            Actions.scaleTo(1f, 1f, 1.8f, Interpolation.sine),
        )))
    }

    private fun AdvancedStage.addBtns() {
        addActor(btns)
        btns.setBounds(184f, 612f, 711f, 697f)

        val levels  = Actor()
        val records = Actor()
        val exit    = Actor()
        addActors(levels, records, exit)
        levels.apply {
            setBounds(184f, 1101f, 697f, 208f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.navigate(SpaceLevelsScreen::class.java.name, SpaceMenuScreen::class.java.name)
                }
            }
        }
        records.apply {
            setBounds(184f, 854f, 697f, 189f)
            setOnClickListener(game.soundUtil) { game.activity.showLeaderboard() }
        }
        exit.apply {
            setBounds(184f, 612f, 697f, 195f)
            setOnClickListener(game.soundUtil) {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.exit()
                }
            }
        }

    }

    /** Магазин скінів і щоденний виклик — над основними кнопками. */
    private fun AdvancedStage.addExtraBtns() {
        val shop = PillButton(drawerUtil, "SHOP", fontBtn, PillButton.Style.CYAN, soundUtil = game.soundUtil).apply {
            setBounds(184f, 1360f, 335f, 150f)
            setOnClickListener {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    game.navigationManager.navigate(SpaceShopScreen::class.java.name, SpaceMenuScreen::class.java.name)
                }
            }
        }
        val done  = game.dailyUtil.isDoneToday()
        val daily = PillButton(drawerUtil, if (done) "DAILY DONE" else "DAILY", fontBtn,
            if (done) PillButton.Style.GRAY else PillButton.Style.ORANGE, soundUtil = game.soundUtil).apply {
            setBounds(560f, 1360f, 335f, 150f)
            setOnClickListener {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    AbstractGameScreen.isDaily = true
                    game.navigationManager.navigate(AbstractGameScreen::class.java.name, SpaceMenuScreen::class.java.name)
                }
            }
        }
        addActors(shop, daily)

        val hint = Label(if (done) "COME BACK TOMORROW" else "+${DailyUtil.BONUS_STARS} STARS BONUS", Label.LabelStyle(fontTiny, Color.valueOf("FFD33D"))).apply {
            setBounds(560f, 1322f, 335f, 30f); setAlignment(Align.center)
        }
        addActor(hint)

        // Кнопка «дихає» — щоб око чіплялось за новий контент
        if (!done) daily.addAction(Actions.forever(Actions.sequence(
            Actions.scaleTo(1.04f, 1.04f, 0.8f, Interpolation.sine), Actions.scaleTo(1f, 1f, 0.8f, Interpolation.sine),
        )))
    }

    /** Обраний скін м'яча крутиться над кнопками, лишаючи слід із частинок. */
    private fun AdvancedStage.addBallPreview() {
        val skin = game.skinUtil.selected
        val ball = ABall(this@SpaceMenuScreen, game.assetsAllUtil, skin)
        ball.setBounds(540f - 95f, 1560f, 190f, 190f)
        ball.setOrigin(Align.center)
        addActor(ball)
        ball.addAction(Actions.forever(Actions.rotateBy(-360f, 6f)))
        ball.addAction(Actions.forever(Actions.sequence(
            Actions.moveBy(0f, 22f, 1.4f, Interpolation.sine),
            Actions.moveBy(0f, -22f, 1.4f, Interpolation.sine),
        )))
        ball.addAction(Actions.forever(Actions.sequence(
            Actions.run {
                val cx = ball.x + ball.width / 2f; val cy = ball.y + ball.height / 2f
                val ang = MathUtils.random(0f, MathUtils.PI2)
                fx.spark(cx + MathUtils.cos(ang) * 100f, cy + MathUtils.sin(ang) * 100f, ball.glowColor,
                    MathUtils.cos(ang) * 40f, MathUtils.sin(ang) * 40f + 20f, 0.9f, MathUtils.random(2f, 4f))
            },
            Actions.delay(0.07f),
        )))
    }

    /** Повільні світлячки, що пливуть угору — «живий» простір. */
    private fun AdvancedStage.addDust() {
        val colors = arrayOf(Color.valueOf("FF7BE0"), Color.valueOf("7FA4FF"), Color.WHITE)
        addAction(Actions.forever(Actions.sequence(
            Actions.run {
                fx.spark(MathUtils.random(0f, 1080f), MathUtils.random(-20f, 200f), colors[MathUtils.random(2)],
                    MathUtils.random(-15f, 15f), MathUtils.random(35f, 80f), MathUtils.random(4f, 7f), MathUtils.random(2f, 4f))
            },
            Actions.delay(0.18f),
        )))
    }

    private fun AdvancedStage.addStars() {
        addActor(stars)
        stars.setBounds(305f, 177f, 470f, 251f)

        addActor(starsLbl)
        starsLbl.apply {
            setBounds(Layout.stars)
            setAlignment(Align.center)
        }
        // Число могло довантажитись із DataStore після створення екрана
        starsLbl.addAction(Actions.forever(Actions.sequence(
            Actions.run { starsLbl.setText("${game.starsUtil.stars}") },
            Actions.delay(0.5f),
        )))
    }

}
