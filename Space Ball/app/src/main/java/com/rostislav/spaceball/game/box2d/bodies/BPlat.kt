package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.rostislav.spaceball.game.actors.game.APlatform
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.game.utils.level.PlatformData
import com.rostislav.spaceball.game.utils.level.PlatformKind
import com.rostislav.spaceball.game.utils.toB2
import kotlin.math.max
import kotlin.math.min

/**
 * Платформа. Тип задається [data]:
 *  - MOVING     — кінематичне тіло, їздить туди-сюди між [PlatformData.x] і x + rangeX;
 *  - ICE        — тертя 0;
 *  - TRAMPOLINE — відскок робить екран (імпульс м'ячу), тут лише анімація;
 *  - CRUMBLE    — після дотику тремтить, зникає (тіло вимикається) і повертається.
 */
class BPlat(
    override val screenBox2d: AdvancedBox2dScreen,
    val data: PlatformData,
    planet: Planet,
    private val fx: FxSystem,
): AbstractBody() {

    companion object {
        private const val CRUMBLE_SHAKE_TIME  = 0.5f
        private const val CRUMBLE_GONE_TIME   = 2.6f
        private const val CRUMBLE_RETURN_TIME = 0.3f
    }

    override val name       = "plat"
    override val bodyDef    = BodyDef().apply {
        type = if (data.kind == PlatformKind.MOVING) BodyDef.BodyType.KinematicBody else BodyDef.BodyType.StaticBody
    }
    override val fixtureDef = FixtureDef().apply {
        friction = if (data.kind == PlatformKind.SLIME) 0f else 0.35f
    }

    val kind get() = data.kind

    val platActor = APlatform(screenBox2d, data.kind, planet.platformColor, planet.accent)
    override var actor: AdvancedGroup? = platActor

    // MOVING
    private val loX = min(data.x, data.x + data.rangeX).toB2
    private val hiX = max(data.x, data.x + data.rangeX).toB2
    private val speedM = data.speed.toB2
    private var dir = if (data.rangeX >= 0f) 1f else -1f

    // CRUMBLE: 0 — ціла, 1 — тремтить, 2 — зникла, 3 — повертається
    private var crumbleState = 0
    private var stateTime    = 0f
    private val debrisColor  = Color(planet.platformColor).lerp(Color.valueOf("7A5C4E"), 0.6f)

    val isGone get() = crumbleState == 2

    init {
        platActor.trackFrom = min(data.x, data.x + data.rangeX)
        platActor.trackTo   = max(data.x, data.x + data.rangeX) + LevelGenerator.PLAT_SIZE.x
        platActor.trackY    = data.y

        when (data.kind) {
            PlatformKind.MOVING  -> renderBlockArray.add(RenderBlock { move() })
            PlatformKind.CRUMBLE -> renderBlockArray.add(RenderBlock { dt -> crumble(dt) })
            else -> {}
        }
    }

    private fun move() {
        val b = body ?: return
        val x = b.position.x
        if (x >= hiX) dir = -1f else if (x <= loX) dir = 1f
        b.setLinearVelocity(dir * speedM, 0f)
    }

    /** Викликати при дотику м'яча. */
    fun touched() {
        if (data.kind == PlatformKind.CRUMBLE && crumbleState == 0) {
            crumbleState = 1
            stateTime = 0f
            platActor.shaking = true
            screenBox2d.game.soundUtil.apply { play(TICK, 0.5f, 0.7f) }
        }
        if (data.kind == PlatformKind.TRAMPOLINE) platActor.bounce()
    }

    private fun crumble(dt: Float) {
        val b = body ?: return
        stateTime += dt
        when (crumbleState) {
            1 -> if (stateTime > CRUMBLE_SHAKE_TIME) {
                crumbleState = 2; stateTime = 0f
                platActor.shaking = false
                b.isActive = false
                platActor.clearActions()
                platActor.addAction(Actions.alpha(0f, 0.18f))
                screenBox2d.game.soundUtil.apply { play(CRUMBLE, 0.9f) }
                // Уламки
                repeat(4) { i ->
                    fx.burst(
                        data.x + 30f + i * 60f, data.y + 10f, debrisColor, 5,
                        speedMin = 40f, speedMax = 220f, sizeMin = 4f, sizeMax = 9f,
                        life = 0.9f, gravity = -1100f, additive = false,
                    )
                }
            }
            2 -> if (stateTime > CRUMBLE_GONE_TIME) {
                crumbleState = 3; stateTime = 0f
                b.isActive = true
                platActor.clearActions()
                platActor.addAction(Actions.alpha(1f, CRUMBLE_RETURN_TIME))
            }
            3 -> if (stateTime > CRUMBLE_RETURN_TIME) crumbleState = 0
        }
    }
}
