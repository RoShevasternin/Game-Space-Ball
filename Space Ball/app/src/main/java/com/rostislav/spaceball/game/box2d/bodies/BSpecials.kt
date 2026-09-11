package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.rostislav.spaceball.game.actors.game.AAsteroid
import com.rostislav.spaceball.game.actors.game.ABlackHole
import com.rostislav.spaceball.game.actors.game.AGate
import com.rostislav.spaceball.game.actors.game.ALaser
import com.rostislav.spaceball.game.actors.game.APortal
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.box2d.BodyId
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.level.BlackHoleData
import com.rostislav.spaceball.game.utils.level.LaserData
import com.rostislav.spaceball.game.utils.runGDX
import com.rostislav.spaceball.game.utils.toB2
import com.rostislav.spaceball.game.utils.toUI

/** Портал (сенсор). Пара порталів знає одна про одну через [other]. */
class BPortal(override val screenBox2d: AdvancedBox2dScreen, accent: Color): AbstractBody() {
    override val name       = "circle"
    override val bodyDef    = BodyDef().apply { type = BodyDef.BodyType.StaticBody }
    override val fixtureDef = FixtureDef().apply { isSensor = true }

    val portalActor = APortal(screenBox2d, accent)
    override var actor: AdvancedGroup? = portalActor

    var other: BPortal? = null

    /** Центр у координатах UI. */
    val centerUI: Vector2 get() = Vector2(position).add(size.x / 2f, size.y / 2f)
}

/** Чорна діра: ядро — сенсор (смерть), притягання — у render-блоці. */
class BBlackHole(
    override val screenBox2d: AdvancedBox2dScreen,
    val data: BlackHoleData,
    fx: FxSystem,
    accent: Color,
): AbstractBody() {
    override val name       = "circle"
    override val bodyDef    = BodyDef().apply { type = BodyDef.BodyType.StaticBody }
    override val fixtureDef = FixtureDef().apply { isSensor = true }
    override var actor: AdvancedGroup? = ABlackHole(screenBox2d, data.pullRadius, fx, accent)

    /** Кого притягувати. */
    var target: BBall? = null
    var enabled = true

    private val tmp = Vector2()
    private val pullRadiusM = data.pullRadius.toB2

    init {
        renderBlockArray.add(RenderBlock { dt -> pull(dt) })
    }

    private fun pull(dt: Float) {
        if (!enabled) return
        val b  = body ?: return
        val tb = target?.body ?: return
        tmp.set(b.position).sub(tb.position)
        val dist = tmp.len()
        if (dist > pullRadiusM || dist < 0.01f) return
        // a = strength / d², але не ближче за 0.55 м — інакше сила нескінченна
        val d = maxOf(dist, 0.55f)
        val accel = data.strength / (d * d)
        tmp.scl(1f / dist).scl(accel * tb.mass * dt)
        tb.applyLinearImpulse(tmp, tb.worldCenter, true)
    }
}

/**
 * Лазер: статичний сенсор, що вмикається на пів [LaserData.period].
 * Товщина фізичної форми залежить від ширини (форми laser_s..laser_xl ≈ 24 px).
 */
class BLaser(
    override val screenBox2d: AdvancedBox2dScreen,
    val data: LaserData,
    accent: Color,
): AbstractBody() {
    companion object {
        /** Скільки секунд до ввімкнення випромінювачі попереджають. */
        const val WARNING_TIME = 0.4f
    }

    override val name = when {
        data.w <= 350f -> "laser_s"
        data.w <= 550f -> "laser_m"
        data.w <= 800f -> "laser_l"
        else           -> "laser_xl"
    }
    override val bodyDef    = BodyDef().apply { type = BodyDef.BodyType.StaticBody }
    override val fixtureDef = FixtureDef().apply { isSensor = true }

    val laserActor = ALaser(screenBox2d, accent)
    override var actor: AdvancedGroup? = laserActor

    private var time = data.phase * data.period
    val isOn get() = laserActor.isOn

    init {
        renderBlockArray.add(RenderBlock { dt -> tick(dt) })
    }

    private fun tick(dt: Float) {
        val b = body ?: return
        time += dt
        val t  = time % data.period
        val on = t < data.period / 2f
        val warn = !on && (data.period - t) < WARNING_TIME

        if (on != laserActor.isOn) {
            laserActor.isOn = on
            b.isActive = on
            if (on) screenBox2d.game.soundUtil.apply { play(LASER, 0.35f) }
        }
        laserActor.warning = warn
    }

    /** Початковий стан тіла: якщо промінь вимкнений — тіло теж. */
    fun applyInitialState() {
        val b = body ?: return
        val on = (time % data.period) < data.period / 2f
        laserActor.isOn = on
        b.isActive = on
    }
}

/** Астероїд: динамічне тіло, руйнується об платформи/землю, вбиває м'яч. */
class BAsteroid(
    override val screenBox2d: AdvancedBox2dScreen,
    private val fx: FxSystem,
): AbstractBody() {
    override val name       = "circle"
    override val bodyDef    = BodyDef().apply {
        type = BodyDef.BodyType.DynamicBody
        angularVelocity = MathUtils.random(-2.5f, 2.5f)
    }
    override val fixtureDef = FixtureDef().apply {
        density = 2f
        friction = 0.5f
        restitution = 0.1f
    }
    override var actor: AdvancedGroup? = AAsteroid(screenBox2d)

    private var exploded = false
    private val dustColor = Color.valueOf("B39A8F")

    init {
        beginContactBlockArray.add(ContactBlock { other ->
            if (other.id == BodyId.BORDERS || other.id == BodyId.WALL) explode()
        })
    }

    fun explode() {
        if (exploded) return
        exploded = true
        val b = body
        if (b != null) {
            val c = Vector2(b.worldCenter).toUI
            fx.dust(c.x, c.y - size.x / 2f, dustColor, count = 14, spread = 70f)
            fx.burst(c.x, c.y, Color.valueOf("FF8A5C"), 10, speedMin = 80f, speedMax = 300f, sizeMin = 2f, sizeMax = 5f, life = 0.5f)
            screenBox2d.game.soundUtil.apply { play(ASTEROID, 0.8f, MathUtils.random(0.85f, 1.15f)) }
        }
        runGDX { destroy() }
    }
}

/** Варп-ворота (сенсор): дотик = перемога. */
class BGate(override val screenBox2d: AdvancedBox2dScreen, accent: Color): AbstractBody() {
    override val name       = "circle"
    override val bodyDef    = BodyDef().apply { type = BodyDef.BodyType.StaticBody }
    override val fixtureDef = FixtureDef().apply { isSensor = true }

    val gateActor = AGate(screenBox2d, accent)
    override var actor: AdvancedGroup? = gateActor

    val centerUI: Vector2 get() = Vector2(position).add(size.x / 2f, size.y / 2f)
}
