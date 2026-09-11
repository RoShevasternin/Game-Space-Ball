package com.rostislav.spaceball.game.box2d

object BodyId {
    const val NONE      = "none"
    /** Те, на чому можна стояти: земля і платформи. */
    const val BORDERS   = "borders"
    /** Стіни та стеля — м'яч від них відбивається, але це не «земля» для стрибка. */
    const val WALL      = "wall"
    const val BALL      = "ball"
    const val TRI       = "tri"
    const val STAR      = "star"
    const val PORTAL    = "portal"
    const val HOLE      = "hole"
    const val LASER     = "laser"
    const val ASTEROID  = "asteroid"
    const val GATE      = "gate"
}
