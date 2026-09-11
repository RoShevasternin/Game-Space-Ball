package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.ui.PillButton
import com.rostislav.spaceball.game.actors.ui.StarRating
import com.rostislav.spaceball.game.effects.FxLayer
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.effects.StarfieldActor
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.dataStore.DailyUtil
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.game.utils.runGDX
import com.rostislav.spaceball.services.ads.AdPolicy

/**
 * Екран перемоги: неонова скляна панель, заголовок, зірки рейтингу (вилітають по черзі),
 * конфеті та кнопки MENU / NEXT / REPLAY.
 */
class SpaceWinScreen(override val game: GdxGame): AdvancedScreen() {

    private val result = AbstractGameScreen.lastResult
        ?: AbstractGameScreen.LevelResult(AbstractGameScreen.level, 3, false, 0, false)

    private val fontBig   = fontGenerator_InterBold.generateFont(FontParameter().ui(88, outline = 5f).setOutline(5f, Color.valueOf("FF5CC8")).setShadow(0, 6, Color(0.2f, 0f, 0.4f, 0.7f)))
    private val fontTitle = fontGenerator_InterBold.generateFont(FontParameter().ui(46))
    private val fontSmall = fontGenerator_InterBold.generateFont(FontParameter().ui(32))
    private val fontBtn   = fontGenerator_InterBold.generateFont(FontParameter().ui(44))

    private val fx = FxSystem(drawerUtil)

    private val rating = StarRating(drawerUtil, result.stars, 190f, gap = 40f, big = true)

    private val planet = if (result.isDaily) LevelGenerator.daily(DailyUtil.todayEpochDay()).planet else Planet.of(result.level)

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[planet.theme].region)
        animateBackground()
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addActor(StarfieldActor(drawerUtil, seed = 4242L, tint = planet.accent))
        addPanel()
        addTitle()
        addRating()
        addButtons()
        addActor(FxLayer(fx, front = true))
        addConfetti()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addPanel() {
        val panel = NeonPanel(drawerUtil.whiteRegion, Color.valueOf("241454"), Color.valueOf("0C0724"), planet.accent, planet.accent2).apply {
            radius = 54f; glow = 0.9f
            setBounds(90f, 560f, 900f, 1000f)
            setOrigin(Align.center); setScale(0.85f); color.a = 0f
        }
        addActor(panel)
        panel.addAction(Actions.parallel(Actions.fadeIn(0.3f), Actions.scaleTo(1f, 1f, 0.5f, Interpolation.swingOut)))
    }

    private fun AdvancedStage.addTitle() {
        val big = Label("YOU WIN", Label.LabelStyle(fontBig, Color.WHITE)).apply {
            setBounds(0f, 1620f, 1080f, 120f); setAlignment(Align.center)
            setOrigin(Align.center); setScale(0.3f)
        }
        addActor(big)
        big.addAction(Actions.sequence(
            Actions.scaleTo(1f, 1f, 0.5f, Interpolation.swingOut),
            Actions.forever(Actions.sequence(
                Actions.scaleTo(1.04f, 1.04f, 1.2f, Interpolation.sine), Actions.scaleTo(1f, 1f, 1.2f, Interpolation.sine),
            )),
        ))

        val text = if (result.isDaily) "DAILY CHALLENGE COMPLETE" else "LEVEL ${result.level + 1} COMPLETE"
        val lbl = Label(text, Label.LabelStyle(fontTitle, Color.valueOf("F4F0FF"))).apply {
            setBounds(0f, 1420f, 1080f, 60f); setAlignment(Align.center)
        }
        addActor(lbl)

        val subText = when {
            result.isDaily && result.bonusStars > 0 -> "+${result.bonusStars} BONUS STARS"
            result.isDaily -> "BONUS ALREADY CLAIMED TODAY"
            result.isNewBest && result.stars == 3 -> "PERFECT! NEW BEST"
            result.isNewBest -> "NEW BEST!"
            result.stars == 3 -> "PERFECT!"
            else -> "${planet.title}  -  ${result.stars} / 3 STARS"
        }
        val sub = Label(subText, Label.LabelStyle(fontSmall, if (result.isNewBest || result.bonusStars > 0 || result.stars == 3) StarRating.GOLD else planet.accent)).apply {
            setBounds(0f, 1362f, 1080f, 44f); setAlignment(Align.center)
        }
        addActor(sub)
    }

    private fun AdvancedStage.addRating() {
        rating.setPosition((1080f - rating.width) / 2f, 1050f)
        addActor(rating)
        rating.pop()
        // Звук на кожну зірку — синхронно з анімацією StarRating.pop()
        for (i in 0 until result.stars) {
            addAction(Actions.delay(0.35f * i + 0.05f, Actions.run {
                game.soundUtil.apply { play(SPARKLE, 0.9f, 0.9f + 0.15f * i) }
                val cx = rating.x + 95f + i * 230f
                fx.burst(cx, rating.y + rating.height / 2f, StarRating.GOLD, 18, speedMin = 100f, speedMax = 380f, sizeMin = 3f, sizeMax = 7f, life = 0.7f)
                fx.ring(cx, rating.y + rating.height / 2f, Color.WHITE, size = 50f, life = 0.5f)
            }))
        }
        if (result.stars == 0) {
            val hint = Label("TIP: COLLECT STARS TO UNLOCK SKINS", Label.LabelStyle(fontSmall, Color.valueOf("D9CFFF"))).apply {
                setBounds(0f, 990f, 1080f, 40f); setAlignment(Align.center)
            }
            addActor(hint)
        }
    }

    private fun AdvancedStage.addButtons() {
        val menu = PillButton(drawerUtil, "MENU", fontBtn, PillButton.Style.GRAY, soundUtil = game.soundUtil).apply {
            setBounds(130f, 780f, 390f, 150f)
            setOnClickListener {
                // Interstitial показується між рівнями; onDone спрацює і якщо реклами не було
                game.activity.showInterstitial {
                    runGDX { stageUI.root.animHide(TIME_ANIM_ALPHA) { game.navigationManager.back() } }
                }
            }
        }
        val next = PillButton(drawerUtil, if (result.isDaily) "DONE" else "NEXT", fontBtn, PillButton.Style.PURPLE, soundUtil = game.soundUtil).apply {
            setBounds(560f, 780f, 390f, 150f)
            setOnClickListener {
                game.activity.showInterstitial {
                    runGDX {
                        stageUI.root.animHide(TIME_ANIM_ALPHA) {
                            if (result.isDaily) {
                                game.navigationManager.back()
                            } else {
                                // Наступний рівень по порядку; після останнього — знову на початок
                                AbstractGameScreen.isDaily = false
                                // Наступний відкритий і ще не пройдений рівень (закриті планети пропускаємо)
                                AbstractGameScreen.level = game.levelUtil.nextLevelAfter(result.level)
                                game.navigationManager.navigate(AbstractGameScreen::class.java.name)
                            }
                        }
                    }
                }
            }
        }
        val replay = PillButton(drawerUtil, "REPLAY", fontBtn, PillButton.Style.CYAN, soundUtil = game.soundUtil).apply {
            setBounds(300f, 610f, 480f, 130f)
            setOnClickListener {
                stageUI.root.animHide(TIME_ANIM_ALPHA) {
                    AbstractGameScreen.isDaily = result.isDaily
                    AbstractGameScreen.level = result.level
                    game.navigationManager.navigate(AbstractGameScreen::class.java.name)
                }
            }
        }
        addActors(menu, next, replay)
        next.addAction(Actions.forever(Actions.sequence(
            Actions.scaleTo(1.04f, 1.04f, 0.8f, Interpolation.sine), Actions.scaleTo(1f, 1f, 0.8f, Interpolation.sine),
        )))
        AdPolicy.onLevelWon()
    }

    /** Конфеті сиплеться згори перші кілька секунд. */
    private fun AdvancedStage.addConfetti() {
        val colors = arrayOf(StarRating.GOLD, planet.accent, planet.accent2, Color.WHITE, Color.valueOf("7BE58A"))
        addAction(Actions.repeat(50, Actions.sequence(
            Actions.run {
                repeat(3) {
                    val c = colors[MathUtils.random(colors.size - 1)]
                    fx.spark(
                        MathUtils.random(60f, 1020f), 1960f, c,
                        MathUtils.random(-60f, 60f), MathUtils.random(-320f, -180f),
                        MathUtils.random(2.5f, 4f), MathUtils.random(4f, 8f), gravity = -120f,
                    )
                }
            },
            Actions.delay(0.08f),
        )))
    }

}
