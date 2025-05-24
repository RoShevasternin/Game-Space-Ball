package com.rostislav.spaceball.game.box2d.bodiesGroup

import com.rostislav.spaceball.game.box2d.AbstractBodyGroup
import com.rostislav.spaceball.game.box2d.BodyId
import com.rostislav.spaceball.game.box2d.bodies.BHorizontal
import com.rostislav.spaceball.game.box2d.bodies.BVertical
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen

class BGBorders(override val screenBox2d: AdvancedBox2dScreen) : AbstractBodyGroup() {

    override val standartW = 1080f

    val bTop   = BHorizontal(screenBox2d)
    val bDown  = BHorizontal(screenBox2d)
    val bLeft  = BVertical(screenBox2d)
    val bRight = BVertical(screenBox2d)

    override fun create(x: Float, y: Float, w: Float, h: Float) {
        super.create(x, y, w, h)

        initB_Borders()

        createHorizontal()
        createVertical()
    }


    // ---------------------------------------------------
    // Init
    // ---------------------------------------------------

    private fun initB_Borders() {
        arrayOf(bTop, bDown, bLeft, bRight).onEach { it.apply {
            id = BodyId.BORDERS
            collisionList.addAll(arrayOf(
                BodyId.BALL
            ))
        } }
    }

    // ---------------------------------------------------
    // Create Body
    // ---------------------------------------------------

    private fun createHorizontal() {
        createBody(bTop, 0f, 1920f, 1080f, 10f)
        createBody(bDown, 0f, -10f, 1080f, 10f)
    }

    private fun createVertical() {
        createBody(bLeft, -10f, 0f, 10f, 1920f)
        createBody(bRight, 1080f, 0f, 10f, 1920f)
    }

    // ---------------------------------------------------
    // Logic
    // ---------------------------------------------------


}