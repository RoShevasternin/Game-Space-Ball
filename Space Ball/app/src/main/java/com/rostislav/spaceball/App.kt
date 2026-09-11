package com.rostislav.spaceball

import android.app.Application
import android.content.Context
import com.google.android.gms.games.PlayGamesSdk
import com.google.firebase.messaging.FirebaseMessaging
import com.rostislav.spaceball.services.ads.AppOpenAdManager
import com.rostislav.spaceball.services.ads.MobileAdsInitializer
import com.rostislav.spaceball.util.log


lateinit var appContext: Context private set

class App: Application() {

    private lateinit var appOpenAdManager: AppOpenAdManager

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        //FirebaseMessaging.getInstance().token.addOnSuccessListener { log("FCM token: $it") }

        PlayGamesSdk.initialize(this)

        // AdMob: одна ініціалізація на весь застосунок (фоновий потік)
        MobileAdsInitializer.initialize(this)

        // App Open реклама при поверненні гри на передній план
        appOpenAdManager = AppOpenAdManager(this)
        appOpenAdManager.register()
    }

}