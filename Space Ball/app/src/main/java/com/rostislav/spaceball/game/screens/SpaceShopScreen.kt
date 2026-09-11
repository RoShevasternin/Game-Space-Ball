package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.game.ABall
import com.rostislav.spaceball.game.actors.ui.PillButton
import com.rostislav.spaceball.game.actors.ui.StarRating
import com.rostislav.spaceball.game.actors.ui.StarShape
import com.rostislav.spaceball.game.effects.FxLayer
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.effects.StarfieldActor
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.game.utils.runGDX
import com.rostislav.spaceball.game.utils.skin.BallSkin

/**
 * Магазин скінів м'яча. Валюта — зірки (баланс = зібрані − витрачені).
 * Внизу — кнопка «безкоштовні зірки» за rewarded-рекламу.
 */
class SpaceShopScreen(override val game: GdxGame): AdvancedScreen() {

    companion object {
        const val FREE_STARS_REWARD = 15L

        private const val COLS   = 2
        private const val CARD_W = 470f
        private const val CARD_H = 280f
        private const val GAP    = 60f
        private const val GRID_TOP = 1690f
    }

    private val fontTitle = fontGenerator_InterBold.generateFont(FontParameter().ui(56))
    private val fontCard  = fontGenerator_InterBold.generateFont(FontParameter().ui(34))
    private val fontSmall = fontGenerator_InterBold.generateFont(FontParameter().ui(28))
    private val fontBtn   = fontGenerator_InterBold.generateFont(FontParameter().ui(40))

    private val fx = FxSystem(drawerUtil)
    private val back = Image(game.assetsAllUtil.BACK)
    private val balanceLbl = Label("", Label.LabelStyle(fontTitle, StarRating.GOLD))
    private val cards = ArrayList<SkinCard>()
    private lateinit var freeBtn: PillButton

    private var knownRevision = -1

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[3].region)
        animateBackground()
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addActor(StarfieldActor(drawerUtil, seed = 99L, count = 60, tint = Color.valueOf("B07BFF")))
        addHeader()
        addCards()
        addFreeStars()
        addActor(FxLayer(fx, front = true))
        addBack()

        addAction(Actions.forever(Actions.sequence(Actions.run { refreshIfChanged() }, Actions.delay(0.2f))))
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addHeader() {
        val title = Label("BALL SKINS", Label.LabelStyle(fontTitle, Color.valueOf("F4F0FF"))).apply {
            setBounds(0f, 1836f, 1080f, 64f); setAlignment(Align.center)
        }
        addActor(title)

        balanceLbl.setBounds(0f, 1770f, 1080f, 60f)
        balanceLbl.setAlignment(Align.center)
        addActor(balanceLbl)
        addActor(object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                val w = balanceLbl.prefWidth
                StarShape.fill(drawerUtil.drawer, 540f - w / 2f - 34f, 1800f, 22f, Color(StarRating.GOLD).also { it.a *= parentAlpha })
            }
        })
    }

    private fun AdvancedStage.addCards() {
        BallSkin.entries.forEachIndexed { i, skin ->
            val col = i % COLS
            val row = i / COLS
            val x = (1080f - COLS * CARD_W - (COLS - 1) * GAP) / 2f + col * (CARD_W + GAP)
            val y = GRID_TOP - (row + 1) * (CARD_H + 34f)
            val card = SkinCard(skin).apply { setBounds(x, y, CARD_W, CARD_H) }
            addActor(card)
            cards.add(card)
            // Картки вилітають по черзі
            card.color.a = 0f
            card.setScale(0.7f)
            card.addAction(Actions.sequence(
                Actions.delay(0.05f * i),
                Actions.parallel(Actions.fadeIn(0.25f), Actions.scaleTo(1f, 1f, 0.4f, Interpolation.swingOut)),
            ))
        }
    }

    private fun AdvancedStage.addFreeStars() {
        freeBtn = PillButton(drawerUtil, "FREE +$FREE_STARS_REWARD STARS  (AD)", fontBtn, PillButton.Style.GREEN, soundUtil = game.soundUtil).apply {
            setBounds(140f, 150f, 800f, 140f)
            setOnClickListener {
                enabledLook = false
                var rewarded = false
                game.activity.showRewarded(
                    onReward = { rewarded = true },
                    onDone = { runGDX {
                        if (rewarded) {
                            game.starsUtil.add(FREE_STARS_REWARD)
                            game.activity.submitLeaderboardScore(game.starsUtil.stars)
                            game.soundUtil.apply { play(PURCHASE, 1f) }
                            fx.burst(540f, 1800f, StarRating.GOLD, 40, speedMin = 100f, speedMax = 500f, sizeMin = 3f, sizeMax = 8f, life = 0.9f)
                            balanceLbl.addAction(Actions.sequence(Actions.scaleTo(1.3f, 1.3f, 0.1f), Actions.scaleTo(1f, 1f, 0.2f)))
                        }
                        enabledLook = game.activity.isRewardedReady()
                    } },
                )
            }
        }
        balanceLbl.setOrigin(Align.center)
        addActor(freeBtn)
    }

    private fun AdvancedStage.addBack() {
        addActor(back)
        back.setBounds(25f, 1776f, 119f, 119f)
        back.setOnClickListener(game.soundUtil) {
            stageUI.root.animHide(TIME_ANIM_ALPHA) { game.navigationManager.back() }
        }
    }

    private fun refreshIfChanged() {
        val rev = game.skinUtil.revision * 1000 + game.starsUtil.revision
        val adReady = game.activity.isRewardedReady()
        if (rev == knownRevision && freeBtn.enabledLook == adReady) return
        knownRevision = rev
        balanceLbl.setText("${game.skinUtil.balance}")
        cards.forEach { it.refresh() }
        freeBtn.enabledLook = adReady
    }

    // ------------------------------------------------------------------------
    // Картка скіна
    // ------------------------------------------------------------------------
    private inner class SkinCard(val skin: BallSkin) : Group() {

        private val panel = NeonPanel(drawerUtil.whiteRegion, Color.valueOf("1E1246"), Color.valueOf("0A0620"), Color(skin.trail), Color(skin.trail).lerp(Color.WHITE, 0.4f))
            .apply { radius = 34f; glow = 0.3f; sheen = 0f }
        private val ball  = ABall(this@SpaceShopScreen, game.assetsAllUtil, skin)
        private val name  = Label(skin.title, Label.LabelStyle(fontCard, Color.valueOf("F4F0FF")))
        private val state = Label("", Label.LabelStyle(fontSmall, StarRating.GOLD))
        private val tmp   = Color()
        private var selected = false
        private var owned = false

        init {
            isTransform = true
            addActor(panel); addActor(ball); addActor(name); addActor(state)
            ball.addAction(Actions.forever(Actions.rotateBy(-360f, 7f)))
            setOnClickListener(game.soundUtil) { onTap() }
        }

        override fun sizeChanged() {
            super.sizeChanged()
            setOrigin(Align.center)
            panel.setBounds(0f, 0f, width, height)
            ball.setBounds(30f, (height - 150f) / 2f, 150f, 150f)
            ball.setOrigin(Align.center)
            name.setBounds(200f, height - 100f, width - 210f, 40f);  name.setAlignment(Align.left)
            state.setBounds(200f, height - 152f, width - 210f, 36f); state.setAlignment(Align.left)
        }

        fun refresh() {
            owned = game.skinUtil.isOwned(skin)
            selected = game.skinUtil.selected == skin
            state.setText(when {
                selected -> "SELECTED"
                owned -> "TAP TO SELECT"
                else -> "${skin.price} STARS"
            })
            state.color.set(when {
                selected -> Color.valueOf("7BE58A")
                owned -> Color.valueOf("D9CFFF")
                game.skinUtil.canAfford(skin) -> StarRating.GOLD
                else -> Color.valueOf("A0A6BD")
            })
            panel.glow  = if (selected) 1f else if (owned) 0.5f else 0.25f
            panel.sheen = if (selected) 1f else 0f
            panel.color.a = if (owned || game.skinUtil.canAfford(skin)) 1f else 0.75f
        }

        private fun onTap() {
            when {
                owned -> {
                    game.skinUtil.select(skin)
                    pulse()
                }
                game.skinUtil.buy(skin) -> {
                    game.skinUtil.select(skin)
                    game.soundUtil.apply { play(PURCHASE, 1f) }
                    val cx = x + 105f; val cy = y + height / 2f
                    fx.burst(cx, cy, skin.trail, 30, speedMin = 100f, speedMax = 450f, sizeMin = 3f, sizeMax = 7f, life = 0.8f)
                    fx.ring(cx, cy, Color.WHITE, size = 70f, life = 0.6f)
                    pulse()
                }
                else -> {
                    game.soundUtil.apply { play(LOCKED, 0.7f) }
                    clearActions()
                    addAction(Actions.sequence(
                        Actions.moveBy(-10f, 0f, 0.04f), Actions.moveBy(20f, 0f, 0.08f), Actions.moveBy(-10f, 0f, 0.04f),
                    ))
                }
            }
            refreshIfChanged()
        }

        private fun pulse() {
            clearActions()
            addAction(Actions.sequence(Actions.scaleTo(1.06f, 1.06f, 0.08f), Actions.scaleTo(1f, 1f, 0.18f, Interpolation.swingOut)))
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            super.draw(batch, parentAlpha)
            if (!owned) {
                // Прайс-тег зірочкою
                val a = color.a * parentAlpha
                tmp.set(StarRating.GOLD); tmp.a = a
                StarShape.fill(drawerUtil.drawer, x + width - 44f, y + height - 44f, 18f, tmp)
            }
        }
    }
}
