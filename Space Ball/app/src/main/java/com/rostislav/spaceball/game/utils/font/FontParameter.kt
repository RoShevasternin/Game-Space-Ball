package com.rostislav.spaceball.game.utils.font

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter

class FontParameter : FreeTypeFontParameter() {

    init {
        setLinear()
    }

    fun setLinear(): FontParameter {
        minFilter = Texture.TextureFilter.Linear
        magFilter = Texture.TextureFilter.Linear
        return this
    }
    fun setSize(size: Int): FontParameter {
        this.size = size
        return this
    }
    fun setCharacters(characters: CharType): FontParameter {
        this.characters = characters.chars
        return this
    }
    fun setCharacters(chars: String): FontParameter {
        this.characters = chars
        return this
    }

    /** Темна обводка — текст читається на будь-якому фоні. */
    fun setOutline(width: Float = 2.5f, color: Color = OUTLINE): FontParameter {
        borderWidth = width
        borderColor = color
        borderStraight = false
        return this
    }

    /** М'яка тінь під текстом. */
    fun setShadow(dx: Int = 0, dy: Int = 3, color: Color = SHADOW): FontParameter {
        shadowOffsetX = dx
        shadowOffsetY = dy
        shadowColor = color
        return this
    }

    /** Стандартний UI-шрифт гри: латиниця + цифри + символи, обводка й тінь. */
    fun ui(size: Int, outline: Float = if (size >= 48) 3f else 2.2f): FontParameter =
        setCharacters(CharType.UI).setSize(size).setOutline(outline).setShadow()

    companion object {
        val OUTLINE: Color = Color(0.05f, 0.03f, 0.12f, 0.95f)
        val SHADOW : Color = Color(0f, 0f, 0f, 0.45f)
    }

    enum class CharType(val chars: String) {
        SYMBOLS       ("\"!`?'•.,;:()[]{}<>|/@\\^\$€—%-+=#_&~*’…«»❤°"                      ),
        NUMBERS       ("1234567890"                                                        ),
        LATIN         ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"              ),
        CYRILLIC      ("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЄЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэєюяІЇії"),

        LATIN_CYRILLIC(LATIN.chars.plus(CYRILLIC.chars)                                         ),
        /** Усе, що потрібно для інтерфейсу гри (без кирилиці — менша текстура). */
        UI            (SYMBOLS.chars.plus(NUMBERS.chars).plus(LATIN.chars)                      ),
        ALL           (SYMBOLS.chars.plus(NUMBERS.chars).plus(LATIN.chars).plus(CYRILLIC.chars) ),
    }

}
