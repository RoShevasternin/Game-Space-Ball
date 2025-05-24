package com.rostislav.spaceball.game.box2d.bodies

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.rostislav.spaceball.game.actors.image.AImage
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.screens.AbstractGameScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup

class BPlat(override val screenBox2d: AdvancedBox2dScreen): AbstractBody() {
    override val name       = "plat"
    override val bodyDef    = BodyDef().apply {
        type = BodyDef.BodyType.StaticBody
    }
    override val fixtureDef = FixtureDef()

    private val color = listOf(
        Color.valueOf("C5D5F3"),
        Color.valueOf("011C45"),
        Color.valueOf("4F2A71"),
        Color.valueOf("C82B50"),
    )[AbstractGameScreen.level]

    override var actor: AdvancedGroup? = AImage(screenBox2d, screenBox2d.drawerUtil.getRegion(color))
}