package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.rostislav.spaceball.game.actors.image.AImage
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup

class BStar(override val screenBox2d: AdvancedBox2dScreen, theme: Int): AbstractBody() {
    override val name       = "a"
    override val bodyDef    = BodyDef().apply {
        type = BodyDef.BodyType.StaticBody
    }
    // Сенсор: м'яч пролітає крізь зірку, а не відбивається від неї
    override val fixtureDef = FixtureDef().apply { isSensor = true }
    override var actor: AdvancedGroup? = AImage(screenBox2d, screenBox2d.game.assetsAllUtil.aList[theme])
}
