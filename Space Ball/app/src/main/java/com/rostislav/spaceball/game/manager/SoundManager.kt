package com.rostislav.spaceball.game.manager

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound

class SoundManager(var assetManager: AssetManager) {

    var loadableSoundList = mutableListOf<SoundData>()

    fun load() {
        loadableSoundList.onEach { assetManager.load(it.path, Sound::class.java) }
    }

    fun init() {
        loadableSoundList.onEach { it.sound = assetManager[it.path, Sound::class.java] }
        loadableSoundList.clear()
    }

    enum class EnumSound(val data: SoundData) {
        BONUS(SoundData("sound/bonus.mp3")),
        CLICK(SoundData("sound/click.mp3")),
        DOWN (SoundData("sound/down.mp3")),
        FAIL (SoundData("sound/fail.mp3")),

        // Згенеровані процедурно (scratchpad/synth.py)
        JUMP      (SoundData("sound/jump.wav")),
        JUMP2     (SoundData("sound/jump2.wav")),
        TRAMPOLINE(SoundData("sound/trampoline.wav")),
        PORTAL    (SoundData("sound/portal.wav")),
        CRUMBLE   (SoundData("sound/crumble.wav")),
        LASER     (SoundData("sound/laser.wav")),
        WARP      (SoundData("sound/warp.wav")),
        ASTEROID  (SoundData("sound/asteroid.wav")),
        EXPLODE   (SoundData("sound/explode.wav")),
        BLACKHOLE (SoundData("sound/blackhole.wav")),
        PURCHASE  (SoundData("sound/purchase.wav")),
        REVIVE    (SoundData("sound/revive.wav")),
        SPARKLE   (SoundData("sound/sparkle.wav")),
        LOCKED    (SoundData("sound/locked.wav")),
        TICK      (SoundData("sound/tick.wav")),
    }

    data class SoundData(
        val path: String,
    ) {
        lateinit var sound: Sound
    }

}
