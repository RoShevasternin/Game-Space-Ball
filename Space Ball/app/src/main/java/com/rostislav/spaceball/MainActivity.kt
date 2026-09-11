package com.rostislav.spaceball

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.rostislav.spaceball.services.ads.AdManager
import com.rostislav.spaceball.databinding.ActivityMainBinding
import com.rostislav.spaceball.services.tiktok.TikTokManager
import com.rostislav.spaceball.util.Lottie
import com.rostislav.spaceball.util.log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    companion object {
        private const val RC_LEADERBOARD_UI = 9004
    }

    private val coroutine = CoroutineScope(Dispatchers.Default)
    private val onceExit  = AtomicBoolean(true)

    private lateinit var binding : ActivityMainBinding
    lateinit var lottie          : Lottie
    lateinit var adManager       : AdManager private set

    // ---------------------------------------------------------------------------------------
    // Google Play Games
    // ---------------------------------------------------------------------------------------

    var isGPGAuthenticated = false
        private set

    /** Рахунок, який не вдалося відправити без авторизації — відправимо одразу після входу. */
    private var pendingScore: Long? = null

    private var gamesSignInClient: GamesSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        initialize()

        adManager = AdManager(this, binding.root)
        adManager.initialize()

        lottie.showLoader()

        gamesSignInClient = PlayGames.getGamesSignInClient(this)
        checkGPGAuthentication()
    }

    override fun onResume() {
        super.onResume()
        if (::adManager.isInitialized) adManager.onResume()
    }

    override fun onPause() {
        if (::adManager.isInitialized) adManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::adManager.isInitialized) adManager.onDestroy()
        super.onDestroy()
    }

    override fun exit() {
        if (onceExit.getAndSet(false)) {
            log("exit")
            finish()
        }
    }

    private fun initialize() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lottie = Lottie(binding)

        initTikTok()
    }

    // ---------------------------------------------------------------------------------------
    // Ads
    // ---------------------------------------------------------------------------------------

    /**
     * Показує interstitial (якщо готовий і дозволяє частотний ліміт).
     * [onDone] викликається завжди — і після закриття реклами, і якщо її не було.
     */
    fun showInterstitial(onDone: () -> Unit) {
        adManager.showInterstitial(onDone)
    }

    /** Чи готова rewarded-реклама (безпечно з GL-потоку). */
    fun isRewardedReady(): Boolean = ::adManager.isInitialized && adManager.isRewardedReady()

    /**
     * Rewarded-реклама: [onReward] — нагорода заслужена, [onDone] — реклама закрита (завжди).
     * Обидва колбеки приходять на main-потоці — у грі загортати в runGDX.
     */
    fun showRewarded(onReward: () -> Unit, onDone: () -> Unit) {
        adManager.showRewarded(onReward, onDone)
    }

    // ---------------------------------------------------------------------------------------
    // Google Play Games: auth + leaderboard
    // ---------------------------------------------------------------------------------------

    private fun checkGPGAuthentication() {
        gamesSignInClient?.isAuthenticated()?.addOnCompleteListener { task ->
            val authenticated = task.isSuccessful && task.result.isAuthenticated
            log("PlayGames isAuthenticated = $authenticated")
            setGPGAuthenticated(authenticated)

            if (authenticated) {
                PlayGames.getPlayersClient(this).currentPlayer.addOnSuccessListener { player ->
                    log("PlayGames playerId = ${player.playerId}")
                }
            }
        }
    }

    private fun setGPGAuthenticated(value: Boolean) {
        isGPGAuthenticated = value

        if (value) {
            // Досилаємо рахунок, який чекав на авторизацію
            pendingScore?.let { score ->
                pendingScore = null
                submitLeaderboardScore(score)
            }
        }
    }

    /**
     * Безпечна відправка рахунку в лідерборд:
     * якщо користувач не авторизований — рахунок запам'ятовується
     * і відправляється автоматично після входу.
     */
    fun submitLeaderboardScore(score: Long) {
        if (score <= 0) return

        if (!isGPGAuthenticated) {
            pendingScore = maxOf(pendingScore ?: 0L, score)
            log("Leaderboard: not authenticated, pending score = $pendingScore")
            return
        }

        PlayGames.getLeaderboardsClient(this)
            .submitScoreImmediate(getString(R.string.leaderboard_number_of_stars), score)
            .addOnSuccessListener { log("Leaderboard: score $score submitted") }
            .addOnFailureListener { e ->
                log("Leaderboard: submit failed: ${e.message}")
                pendingScore = maxOf(pendingScore ?: 0L, score)
            }
    }

    fun showLeaderboard() {
        if (!isGPGAuthenticated) {
            // Спочатку логінимось, потім відкриваємо лідерборд
            gamesSignInClient?.signIn()?.addOnCompleteListener { task ->
                val authenticated = task.isSuccessful && task.result.isAuthenticated
                setGPGAuthenticated(authenticated)
                if (authenticated) openLeaderboardUI()
            }
            return
        }
        openLeaderboardUI()
    }

    private fun openLeaderboardUI() {
        PlayGames.getLeaderboardsClient(this)
            .getLeaderboardIntent(getString(R.string.leaderboard_number_of_stars))
            .addOnSuccessListener { intent -> startActivityForResult(intent, RC_LEADERBOARD_UI) }
            .addOnFailureListener { e ->
                log("Leaderboard: intent failed: ${e.message}")
                // Токен міг протухнути — пробуємо перевірити авторизацію ще раз
                checkGPGAuthentication()
            }
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        log("Hello: $resultCode")
    }

    // ------------------------------------------------------------------------
    // TikTok
    // ------------------------------------------------------------------------
    private fun initTikTok() {
        // Ключі — у gradle.properties → BuildConfig (див. TikTokManager)
        TikTokManager.initialize(application)
    }

    // ------------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------------
    /**
     * Push permission (Android 13+)
     * */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> log("POST_NOTIFICATIONS granted = $granted") }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

}