package com.rostislav.spaceball.game.actors.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.rostislav.spaceball.game.manager.util.SpriteUtil
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.skin.BallSkin

/** М'яч зі скіном: свій спрайт, відтінок, світіння, веселка, блимання при невразливості. */
class ABall(
    override val screen: AdvancedScreen,
    assets: SpriteUtil.AllAssets,
    val skin: BallSkin,
) : AdvancedGroup() {

    private val image = Image(if (skin.region < 0) assets.BALL else assets.ballList[skin.region])
    private var time  = 0f
    private val tmp   = Color()

    /** Поточний колір світіння/сліду — для веселки змінюється щокадру. */
    val glowColor = Color(skin.trail)

    var invulnerable = false

    override fun addActorsOnGroup() {
        addActor(image)
        val s = skin.spriteScale
        image.setBounds(-(s - 1f) * width / 2f, -(s - 1f) * height / 2f, width * s, height * s)
        image.color.set(skin.tint)
    }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        if (skin.rainbow) {
            val hue = (time * 80f) % 360f
            image.color.fromHsv(hue, 0.35f, 1f)
            glowColor.fromHsv(hue, 0.85f, 1f)
        }
        image.color.a = if (invulnerable) 0.35f + 0.65f * (0.5f + 0.5f * MathUtils.sin(time * 28f)) else 1f
    }

    /** Приземлення: сплющується і повертається. */
    fun squash() {
        clearActions()
        setScale(1.18f, 0.82f)
        addAction(Actions.scaleTo(1f, 1f, 0.2f, Interpolation.swingOut))
    }

    /** Стрибок: витягується вгору. */
    fun stretch() {
        clearActions()
        setScale(0.86f, 1.16f)
        addAction(Actions.scaleTo(1f, 1f, 0.22f, Interpolation.swingOut))
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (skin.glow) {
            val a = color.a * parentAlpha
            val cx = x + width / 2f
            val cy = y + height / 2f
            screen.drawerUtil.additive {
                tmp.set(glowColor); tmp.a = 0.22f * a * (0.8f + 0.2f * MathUtils.sin(time * 6f))
                filledCircle(cx, cy, width * 0.68f, tmp)
                tmp.a = 0.30f * a
                filledCircle(cx, cy, width * 0.55f, tmp)
            }
        }
        super.draw(batch, parentAlpha)
    }
}
