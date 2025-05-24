package com.rostislav.spaceball.game.utils

import com.badlogic.gdx.math.Vector2

object Layout {

    val stars = LayoutData(564f,264f,199f,75f)

    data class LayoutData(
        val x: Float = 0f,
        val y: Float = 0f,
        val w: Float = 0f,
        val h: Float = 0f,
        // horizontal space
        val hs: Float = 0f,
        // vertical space
        val vs: Float = 0f,
    ) {

        fun position() = Vector2(x, y)
        fun size() = Vector2(w, h)

    }

}












