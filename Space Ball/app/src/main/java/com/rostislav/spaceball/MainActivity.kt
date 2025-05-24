package com.rostislav.spaceball

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowMetrics
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.rostislav.spaceball.databinding.ActivityMainBinding
import com.rostislav.spaceball.util.Lottie
import com.rostislav.spaceball.util.Once
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    companion object {
        private const val RC_LEADERBOARD_UI = 9004
    }

    private val coroutine = CoroutineScope(Dispatchers.Default)
    private val onceExit  = Once()

    private lateinit var binding : ActivityMainBinding
    lateinit var lottie          : Lottie

    var isGPGAuthenticated = false
    var gamesSignInClient: GamesSignInClient? = null

    // Ads
    private val adSize: AdSize
        get() {
            val displayMetrics = resources.displayMetrics
            val adWidthPixels =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics: WindowMetrics = this.windowManager.currentWindowMetrics
                    windowMetrics.bounds.width()
                } else {
                    displayMetrics.widthPixels
                }
            val density = displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }
    private val adView by lazy { AdView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialize()
        initializeAdMob()
        lottie.showLoader()

        gamesSignInClient = PlayGames.getGamesSignInClient(this)

        gamesSignInClient?.let { gsc ->
            gsc.isAuthenticated().addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                isGPGAuthenticated = (isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated)
                log("PlayGames isAuthenticated = $isGPGAuthenticated")

                if (isGPGAuthenticated) {
                    PlayGames.getPlayersClient(this).currentPlayer.addOnCompleteListener { mTask ->
                        log("PlayGames playerId = ${mTask.result.playerId}")
                    }
                }
            }
        }
    }

    override fun exit() {
        onceExit.once {
            log("exit")
            coroutine.launch(Dispatchers.Main) {
                finishAndRemoveTask()
                delay(100)
                exitProcess(0)
            }
        }
    }

    private fun initialize() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lottie       = Lottie(binding)
    }

    fun setNavigationBarColor(@ColorRes colorId: Int) {
        coroutine.launch(Dispatchers.Main) {
            window.navigationBarColor = ContextCompat.getColor(this@MainActivity, colorId)
        }
    }

    fun showLeaderboard() {
        PlayGames.getLeaderboardsClient(this)
            .getLeaderboardIntent(getString(R.string.leaderboard_number_of_stars))
            .addOnSuccessListener { intent -> startActivityForResult(intent, RC_LEADERBOARD_UI) }
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        log("Hello: $resultCode")
    }

    // Ads -----------------------------------------------------------------------------------------

    private fun initializeAdMob() {
        coroutine.launch(Dispatchers.IO) {
            MobileAds.initialize(this@MainActivity)
            withContext(Dispatchers.Main) { addBannerAd() }
        }
    }

    private fun addBannerAd() {
        adView.adUnitId = getString(R.string.ad_banner_id)
        adView.setAdSize(adSize)
        adView.id = View.generateViewId()

        binding.root.addView(adView)

        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.root)

        constraintSet.connect(adView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(adView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(adView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(binding.root)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

}