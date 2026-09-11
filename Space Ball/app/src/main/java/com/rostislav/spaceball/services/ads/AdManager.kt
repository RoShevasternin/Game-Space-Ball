package com.rostislav.spaceball.services.ads

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.rostislav.spaceball.R
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.util.log

/**
 * Керує банером, interstitial- та rewarded-рекламою для однієї Activity.
 *
 * Банер: адаптивний, прикріплюється до низу root ConstraintLayout.
 * Interstitial: завантажується наперед, показується з обмеженням частоти
 * (не частіше ніж раз на [INTERSTITIAL_MIN_INTERVAL_MS]) і автоматично
 * перезавантажується після показу чи помилки.
 * Rewarded: «Continue» після смерті та безкоштовні зірки в магазині;
 * після невдалого завантаження повторює спробу через [REWARDED_RETRY_MS].
 */
class AdManager(
    private val activity: Activity,
    private val root: ConstraintLayout,
) {

    companion object {
        /** Мінімальний інтервал між показами interstitial (мс). */
        private const val INTERSTITIAL_MIN_INTERVAL_MS = 25_000L
        private const val REWARDED_RETRY_MS = 30_000L
    }

    private var adView: AdView? = null

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownAt = 0L

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    private val handler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------------

    fun initialize() {
        MobileAdsInitializer.onReady {
            if (activity.isDestroyed || activity.isFinishing) return@onReady
            // Режим зйомки скріншотів — без банера
            if (!DebugFlags.shots) addBannerAd()
            loadInterstitial()
            loadRewarded()
        }
    }

    /**
     * Показує interstitial, якщо він завантажений і не порушується частотний ліміт.
     * [onDone] викликається ЗАВЖДИ (на main-потоці): або після закриття реклами,
     * або одразу, якщо показувати нічого/зарано — тож сюди можна безпечно
     * класти навігацію гри.
     *
     * Можна викликати з будь-якого потоку (зокрема з GL-потоку libGDX): уся робота
     * з AdMob переноситься на main-потік, бо SDK кидає "#008 Must be called on the main UI thread".
     */
    fun showInterstitial(onDone: () -> Unit) = activity.runOnUiThread {
        val ad = interstitialAd
        val now = SystemClock.elapsedRealtime()

        val tooSoon = now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS && lastInterstitialShownAt != 0L
        if (ad == null || tooSoon || FullScreenAdState.isShowingAd) {
            if (ad == null && !isInterstitialLoading) loadInterstitial()
            onDone()
        } else {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    log("Interstitial dismissed")
                    FullScreenAdState.isShowingAd = false
                    interstitialAd = null
                    loadInterstitial()
                    onDone()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    log("Interstitial failed to show: ${adError.message}")
                    FullScreenAdState.isShowingAd = false
                    interstitialAd = null
                    loadInterstitial()
                    onDone()
                }

                override fun onAdShowedFullScreenContent() {
                    log("Interstitial shown")
                    FullScreenAdState.isShowingAd = true
                    lastInterstitialShownAt = SystemClock.elapsedRealtime()
                }
            }
            ad.show(activity)
        }
    }

    /** Чи є завантажена rewarded-реклама (можна читати з будь-якого потоку). */
    fun isRewardedReady(): Boolean = rewardedAd != null && !FullScreenAdState.isShowingAd

    /**
     * Показує rewarded-рекламу. [onReward] — гравець додивився і заслужив нагороду
     * (може не викликатись), [onDone] — реклама закрита або її не було (викликається завжди).
     * Обидва — на main-потоці.
     */
    fun showRewarded(onReward: () -> Unit, onDone: () -> Unit) = activity.runOnUiThread {
        val ad = rewardedAd
        if (ad == null || FullScreenAdState.isShowingAd) {
            if (ad == null) loadRewarded()
            onDone()
            return@runOnUiThread
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                log("Rewarded dismissed")
                FullScreenAdState.isShowingAd = false
                rewardedAd = null
                loadRewarded()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                log("Rewarded failed to show: ${adError.message}")
                FullScreenAdState.isShowingAd = false
                rewardedAd = null
                loadRewarded()
                onDone()
            }

            override fun onAdShowedFullScreenContent() {
                log("Rewarded shown")
                FullScreenAdState.isShowingAd = true
            }
        }
        ad.show(activity) { reward ->
            log("Rewarded earned: ${reward.amount} ${reward.type}")
            onReward()
        }
    }

    fun onResume()  { adView?.resume() }
    fun onPause()   { adView?.pause() }
    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        adView?.destroy()
        adView = null
        interstitialAd = null
        rewardedAd = null
    }

    // -------------------------------------------------------------------------------------------
    // Banner
    // -------------------------------------------------------------------------------------------

    private val adSize: AdSize
        get() {
            val displayMetrics = activity.resources.displayMetrics
            val adWidthPixels =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
                    windowMetrics.bounds.width()
                } else {
                    displayMetrics.widthPixels
                }
            val density = displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        }

    private fun addBannerAd() {
        if (adView != null) return

        val banner = AdView(activity).apply {
            adUnitId = activity.getString(R.string.ad_banner_id)
            setAdSize(this@AdManager.adSize)
            id = View.generateViewId()
        }
        adView = banner

        root.addView(banner)

        val constraintSet = ConstraintSet()
        constraintSet.clone(root)
        constraintSet.connect(banner.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(banner.id, ConstraintSet.START,  ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(banner.id, ConstraintSet.END,    ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.applyTo(root)

        banner.loadAd(AdRequest.Builder().build())
    }

    // -------------------------------------------------------------------------------------------
    // Interstitial
    // -------------------------------------------------------------------------------------------

    private fun loadInterstitial() {
        if (isInterstitialLoading || interstitialAd != null) return
        isInterstitialLoading = true

        InterstitialAd.load(
            activity,
            activity.getString(R.string.ad_interstitial_id),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    log("Interstitial loaded")
                    isInterstitialLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("Interstitial failed to load: ${error.message}")
                    isInterstitialLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    // -------------------------------------------------------------------------------------------
    // Rewarded
    // -------------------------------------------------------------------------------------------

    private fun loadRewarded() {
        if (isRewardedLoading || rewardedAd != null) return
        if (activity.isDestroyed || activity.isFinishing) return
        isRewardedLoading = true

        RewardedAd.load(
            activity,
            activity.getString(R.string.ad_rewarded_id),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    log("Rewarded loaded")
                    isRewardedLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("Rewarded failed to load: ${error.message}")
                    isRewardedLoading = false
                    rewardedAd = null
                    handler.postDelayed({ loadRewarded() }, REWARDED_RETRY_MS)
                }
            }
        )
    }
}

/** Спільний прапорець, щоб App Open, Interstitial та Rewarded не показувалися одночасно. */
object FullScreenAdState {
    @Volatile
    var isShowingAd = false
}
