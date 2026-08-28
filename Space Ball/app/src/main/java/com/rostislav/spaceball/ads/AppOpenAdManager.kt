package com.rostislav.spaceball.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.rostislav.spaceball.R
import com.rostislav.spaceball.util.log
import java.util.Date

/**
 * App Open Ad — реклама при поверненні гри на передній план.
 *
 * Що змінилось порівняно з базовою схемою Google:
 *  1. Якщо гравець повернувся, а реклама ще НЕ довантажилась — запит не втрачається:
 *     ставимо прапорець [pendingShow] і показуємо одразу після завантаження
 *     (якщо гра все ще на передньому плані).
 *  2. Повтор при помилці завантаження з наростаючою затримкою.
 *  3. Детальні логи (тег "Rostik") — видно кожен крок у logcat.
 *
 * При холодному старті ([SHOW_ON_COLD_START]) реклама показується поверх екрана
 * завантаження: [AppOpenGate] тримає лоадер, доки реклама не закриється
 * (або доки не спрацює таймаут), тож вона не вилітає посеред меню.
 */
class AppOpenAdManager(
    private val application: Application,
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        /** Термін життя закешованої App Open реклами (години). */
        private const val AD_EXPIRY_HOURS = 4L

        /** Показувати рекламу і при першому запуску гри. */
        private const val SHOW_ON_COLD_START = true

        private const val MAX_RETRIES     = 3
        private const val RETRY_BASE_MS   = 3_000L
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime = 0L
    private var retryCount = 0

    private var currentActivity: Activity? = null
    private var isForeground = false
    private var isColdStart  = true

    /** Гравець уже повернувся в гру, але реклама ще вантажиться. */
    private var pendingShow = false

    private val handler = Handler(Looper.getMainLooper())

    fun register() {
        // Лоадер має зачекати на рекламу лише якщо ми справді збираємось її показати
        if (SHOW_ON_COLD_START) AppOpenGate.arm()

        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        MobileAdsInitializer.onReady {
            log("AppOpenAd: SDK ready, starting first load")
            loadAd()
        }
    }

    // -------------------------------------------------------------------------------------------
    // ProcessLifecycleOwner — застосунок вийшов на передній план / пішов у фон
    // -------------------------------------------------------------------------------------------

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true

        if (isColdStart) {
            isColdStart = false
            if (!SHOW_ON_COLD_START) {
                log("AppOpenAd: cold start — skip (SHOW_ON_COLD_START = false)")
                return
            }
            log("AppOpenAd: cold start — will show over the loader")
        }

        log("AppOpenAd: app foregrounded, adAvailable = ${isAdAvailable()}")
        val activity = currentActivity
        if (activity == null) {
            log("AppOpenAd: no current activity yet — will show when ad is ready")
            pendingShow = true
            loadAd()
            return
        }
        showAdIfAvailable(activity)
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        pendingShow  = false
    }

    // -------------------------------------------------------------------------------------------
    // Load / Show
    // -------------------------------------------------------------------------------------------

    private fun loadAd() {
        if (isLoadingAd) {
            log("AppOpenAd: load skipped — already loading")
            return
        }
        if (isAdAvailable()) {
            log("AppOpenAd: load skipped — ad already cached")
            return
        }
        isLoadingAd = true
        log("AppOpenAd: loading...")

        AppOpenAd.load(
            application,
            application.getString(R.string.ad_app_open_id),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    log("AppOpenAd: loaded")
                    appOpenAd   = ad
                    isLoadingAd = false
                    loadTime    = Date().time
                    retryCount  = 0

                    // Гравець уже чекає — показуємо негайно
                    if (pendingShow && isForeground) {
                        pendingShow = false
                        currentActivity?.let { showAdIfAvailable(it) }
                            ?: log("AppOpenAd: pending show, but activity is null")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    log("AppOpenAd: failed to load — code=${error.code}, msg=${error.message}")

                    // Не тримаємо лоадер, якщо реклами не буде
                    AppOpenGate.resolve("load failed")

                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        val delay = RETRY_BASE_MS * retryCount
                        log("AppOpenAd: retry #$retryCount in ${delay}ms")
                        handler.postDelayed({ loadAd() }, delay)
                    }
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity) {
        if (FullScreenAdState.isShowingAd) {
            log("AppOpenAd: another full screen ad is showing — skip")
            return
        }
        if (!isAdAvailable()) {
            log("AppOpenAd: not ready — will show right after load")
            pendingShow = true
            loadAd()
            return
        }

        val ad = appOpenAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                log("AppOpenAd: dismissed")
                appOpenAd = null
                FullScreenAdState.isShowingAd = false
                AppOpenGate.resolve("ad dismissed")
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                log("AppOpenAd: failed to show — ${adError.message}")
                appOpenAd = null
                FullScreenAdState.isShowingAd = false
                AppOpenGate.resolve("failed to show")
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                log("AppOpenAd: shown")
                FullScreenAdState.isShowingAd = true
            }
        }
        log("AppOpenAd: showing on ${activity::class.java.simpleName}")
        ad.show(activity)
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && wasLoadTimeLessThanHoursAgo(AD_EXPIRY_HOURS)

    private fun wasLoadTimeLessThanHoursAgo(hours: Long): Boolean {
        val msPerHour = 3_600_000L
        return Date().time - loadTime < msPerHour * hours
    }

    // -------------------------------------------------------------------------------------------
    // ActivityLifecycleCallbacks — відстежуємо поточну Activity
    // -------------------------------------------------------------------------------------------

    override fun onActivityStarted(activity: Activity) {
        // Не перехоплюємо AdActivity, поки показується реклама
        if (!FullScreenAdState.isShowingAd) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (FullScreenAdState.isShowingAd) return
        currentActivity = activity

        // Реклама могла довантажитись, поки Activity ще не була готова
        if (pendingShow && isForeground && isAdAvailable()) {
            pendingShow = false
            showAdIfAvailable(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}