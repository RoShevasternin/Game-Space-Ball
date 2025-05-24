package com.rostislav.spaceball.game.utils.advanced

import com.badlogic.gdx.utils.viewport.FitViewport
import com.rostislav.spaceball.game.box2d.WorldUtil
import com.rostislav.spaceball.game.utils.HEIGHT_BOX2D
import com.rostislav.spaceball.game.utils.HEIGHT_UI
import com.rostislav.spaceball.game.utils.WIDTH_BOX2D
import com.rostislav.spaceball.game.utils.WIDTH_UI
import com.rostislav.spaceball.util.log

abstract class AdvancedBox2dScreen(
    val worldUtil: WorldUtil,
    val uiW  : Float = WIDTH_UI,
    val uiH  : Float = HEIGHT_UI,
    val boxW : Float = WIDTH_BOX2D,
    val boxH : Float = HEIGHT_BOX2D,
): AdvancedScreen(uiW, uiH) {

    private val viewportBox2d by lazy { FitViewport(boxW, boxH) }

    var isPauseWorld = false

    override fun resize(width: Int, height: Int) {
        viewportBox2d.update(width, height, true)
        super.resize(width, height)
    }

    override fun render(delta: Float) {
        if (isPauseWorld.not()) worldUtil.update(delta)
        super.render(delta)
        worldUtil.debug(viewportBox2d.camera.combined)
    }

    override fun dispose() {
        log("dispose AdvancedBox2dScreen: ${this::class.java.name.substringAfterLast('.')}")
        worldUtil.dispose()
        super.dispose()
    }

}