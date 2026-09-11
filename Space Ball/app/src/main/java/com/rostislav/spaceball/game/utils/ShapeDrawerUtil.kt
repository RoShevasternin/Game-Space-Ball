package com.rostislav.spaceball.game.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import space.earlygrey.shapedrawer.ShapeDrawer

class ShapeDrawerUtil(val batch: Batch): Disposable {

    private val disposableSet = mutableSetOf<Disposable>()

    val drawer = ShapeDrawer(batch, getRegion())

    /**
     * Біла текстура для шейдерних квадів. Навмисно 4×4, а не 1×1: для регіону 1×1
     * libGDX зсуває UV до центру пікселя (0.25..0.75), і шейдер бачив би лише
     * половину свого поля. З 4×4 текстурні координати чесно йдуть від 0 до 1.
     */
    val whiteRegion: TextureRegion by lazy { getRegion(Color.WHITE, 4) }

    override fun dispose() {
        disposableSet.disposeAll()
    }

    fun update() {
        drawer.update()
    }

    fun getRegion(color: Color = Color.WHITE, size: Int = 1): TextureRegion {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()

        val texture = Texture(pixmap)
        disposableSet.add(texture)

        pixmap.dispose()
        return TextureRegion(texture, 0, 0, size, size)
    }

    /** Малює [block] з адитивним змішуванням (світіння), потім повертає звичайне. */
    inline fun additive(block: ShapeDrawer.() -> Unit) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        drawer.block()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

}
