package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.rostislav.spaceball.game.actors.game.ABall
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.skin.BallSkin

class BBall(
    override val screenBox2d: AdvancedBox2dScreen,
    skin: BallSkin = BallSkin.CLASSIC,
): AbstractBody() {
    override val name       = "circle"
    override val bodyDef    = BodyDef().apply {
        type = BodyDef.BodyType.DynamicBody
        bullet = true
    }
    override val fixtureDef = FixtureDef().apply {
        density = 3f
        restitution = 0.2f
        friction    = 0.4f
    }

    val ballActor = ABall(screenBox2d, screenBox2d.game.assetsAllUtil, skin)
    override var actor: AdvancedGroup? = ballActor
}
