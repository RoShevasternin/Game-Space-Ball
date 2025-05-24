package com.rostislav.spaceball.game.manager.util

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.rostislav.spaceball.game.manager.SpriteManager

class SpriteUtil {

     class LoaderAssets {
          val _1 = SpriteManager.EnumTexture._1.data.texture
          val _2 = SpriteManager.EnumTexture._2.data.texture
          val _3 = SpriteManager.EnumTexture._3.data.texture
          val _4 = SpriteManager.EnumTexture._4.data.texture

          val backgrounds = listOf(_1, _2, _3, _4)
     }

     class AllAssets {
          private fun getRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.ALL.data.atlas.findRegion(name)

          val BALL  = getRegion("ball")
          val UP    = getRegion("up")
          val LEFT  = getRegion("left")
          val RIGHT = getRegion("right")
          val UP_P    = getRegion("up_p")
          val LEFT_P  = getRegion("left_p")
          val RIGHT_P = getRegion("right_p")
          val STARS   = getRegion("stars")
          val BACK    = getRegion("BACK")
          val upup_d    = getRegion("upup_d")
          val upup_p    = getRegion("upup_p")

          val aList = List(4) { getRegion("a${it.inc()}") }
          val bList = List(4) { getRegion("b${it.inc()}") }
          val pList = List(4) { getRegion("p${it.inc()}") }

          val btns       = SpriteManager.EnumTexture.btns.data.texture
          val gpg_signin = SpriteManager.EnumTexture.gpg_signin.data.texture
          val you_win = SpriteManager.EnumTexture.you_win.data.texture
     }

}