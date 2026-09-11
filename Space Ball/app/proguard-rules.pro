# ── LibGDX ────────────────────────────────────────────────────────────
# Box2D викликає ці методи з нативного коду через JNI.
# R8 таких викликів не бачить і вирізає методи як мертві.
-keep class com.badlogic.gdx.physics.box2d.** { *; }
-keep class com.badlogic.gdx.utils.** { *; }
-keep class com.badlogic.gdx.backends.android.** { *; }

-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
    <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object,
           com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}

-dontwarn com.badlogic.gdx.**

#LibGDX -----------------------------------------------------------------
-dontwarn javax.annotation.Nullable

-verbose

-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication

-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
-keep public class com.badlogic.gdx.graphics.Color { *; }

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

#TikTok -----------------------------------------------------------------
-keep class com.tiktok.** { *; }
# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**
# Google Install Referrer
-keep class com.android.installreferrer.api.** { *; }
# Android Lifecycle
-keep class androidx.lifecycle.** { *; }