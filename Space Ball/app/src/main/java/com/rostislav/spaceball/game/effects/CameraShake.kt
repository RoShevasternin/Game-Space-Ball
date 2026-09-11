package com.rostislav.spaceball.game.effects

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.max

/** Трясіння камери: сила спадає лінійно до кінця [duration]. */
class CameraShake {

    private var time     = 0f
    private var duration = 0f
    private var strength = 0f

    val offset = Vector2()

    fun shake(strength: Float, duration: Float) {
        this.strength = max(this.strength * (time / max(this.duration, 0.001f)), strength)
        this.duration = duration
        time = duration
    }

    /** Повертає true, поки трясе. */
    fun update(delta: Float): Boolean {
        if (time <= 0f) {
            offset.setZero()
            strength = 0f
            return false
        }
        time -= delta
        val k = (time / duration).coerceIn(0f, 1f)
        offset.set(
            MathUtils.random(-1f, 1f) * strength * k,
            MathUtils.random(-1f, 1f) * strength * k,
        )
        return true
    }
}
