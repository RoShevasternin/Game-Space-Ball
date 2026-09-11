package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.rostislav.spaceball.game.actors.image.AImage
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.level.SpikeData
import com.rostislav.spaceball.game.utils.toB2
import kotlin.math.max
import kotlin.math.min

/**
 * Шип. Сенсор (смерть на дотик), тому може бути кінематичним і рухатись через
 * setTransform без побічних ефектів фізики:
 *  - прив'язаний до рухомої платформи ([attachedTo]) — повторює її позицію;
 *  - «повзучий» ([SpikeData.isSliding]) — їздить по платформі сам.
 */
class BTriangle(
    override val screenBox2d: AdvancedBox2dScreen,
    val data: SpikeData,
    theme: Int,
): AbstractBody() {

    override val name       = "b"
    override val bodyDef    = BodyDef().apply {
        type = if (data.attachedTo >= 0 || data.isSliding) BodyDef.BodyType.KinematicBody else BodyDef.BodyType.StaticBody
    }
    override val fixtureDef = FixtureDef().apply { isSensor = true }
    override var actor: AdvancedGroup? = AImage(screenBox2d, screenBox2d.game.assetsAllUtil.bList[theme])

    /** Платформа, з якою рухається шип (задає екран). */
    var attachedPlat: BPlat? = null

    private val offset     = Vector2()
    private var offsetInit = false

    private val loX = min(data.x, data.x + data.rangeX).toB2
    private val hiX = max(data.x, data.x + data.rangeX).toB2
    private val speedM = data.speed.toB2
    private var dir = if (data.rangeX >= 0f) 1f else -1f

    init {
        if (data.attachedTo >= 0) renderBlockArray.add(RenderBlock { follow() })
        else if (data.isSliding) renderBlockArray.add(RenderBlock { dt -> slide(dt) })
    }

    private fun follow() {
        val b  = body ?: return
        val pb = attachedPlat?.body ?: return
        if (!offsetInit) {
            offset.set(b.position).sub(pb.position)
            offsetInit = true
        }
        b.setTransform(pb.position.x + offset.x, pb.position.y + offset.y, 0f)
    }

    private fun slide(dt: Float) {
        val b = body ?: return
        // position.x тіла = лівий край шипа (origin форми "b" = 0,0)
        var x = b.position.x + dir * speedM * dt
        if (x >= hiX) { x = hiX; dir = -1f } else if (x <= loX) { x = loX; dir = 1f }
        b.setTransform(x, b.position.y, 0f)
    }
}
