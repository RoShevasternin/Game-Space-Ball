package com.rostislav.spaceball.game.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.rostislav.spaceball.game.utils.ShapeDrawerUtil
import kotlin.random.Random

/** Мерехтливі зірочки поверх фону — дешевий «живий» задній план. */
class StarfieldActor(
    private val drawerUtil: ShapeDrawerUtil,
    seed: Long,
    private val count: Int = 70,
    private val tint: Color = Color.WHITE,
) : Actor() {

    private val xs     = FloatArray(count)
    private val ys     = FloatArray(count)
    private val sizes  = FloatArray(count)
    private val phases = FloatArray(count)
    private val freqs  = FloatArray(count)
    private var time   = 0f
    private val tmp    = Color()

    init {
        val rnd = Random(seed)
        for (i in 0 until count) {
            xs[i]     = rnd.nextFloat() * 1080f
            ys[i]     = 300f + rnd.nextFloat() * 1600f
            sizes[i]  = 1.2f + rnd.nextFloat() * 2.4f
            phases[i] = rnd.nextFloat() * MathUtils.PI2
            freqs[i]  = 0.8f + rnd.nextFloat() * 2.2f
        }
    }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawerUtil.drawer
        drawerUtil.additive {
            for (i in 0 until count) {
                val tw = 0.5f + 0.5f * MathUtils.sin(time * freqs[i] + phases[i])
                tmp.set(tint); tmp.a = (0.15f + 0.55f * tw) * parentAlpha * color.a
                d.filledCircle(xs[i], ys[i], sizes[i] * (0.7f + 0.5f * tw), tmp)
            }
        }
    }
}
