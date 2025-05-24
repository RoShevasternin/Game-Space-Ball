package com.rostislav.spaceball

import android.app.Application
import android.content.Context
import com.google.android.gms.games.PlayGamesSdk


lateinit var appContext: Context private set

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        PlayGamesSdk.initialize(this)
    }

}