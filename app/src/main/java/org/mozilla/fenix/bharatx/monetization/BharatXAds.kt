/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.appnext.adunits.banner.BannerSize
import com.appnext.adunits.banner.BannerView
import com.appnext.adunits.interstitial.Interstitial
import com.appnext.adunits.interstitial.InterstitialAdCallbacks
import com.appnext.adunits.interstitial.InterstitialLoadCallback
import com.appnext.adunits.rewardedads.RewardedAd
import com.appnext.adunits.rewardedads.RewardedAdCallbacks
import com.appnext.adunits.rewardedads.RewardedAdLoadCallback
import com.appnext.core.AppnextError
import com.nitaistudio.rikivpn.api.RikiVpn
import com.nitaistudio.rikivpn.models.VpnState
import com.appnext.nexdk.AppnextSDK
import com.appnext.nexdk.callbacks.AdCallbacks
import java.util.UUID
import org.mozilla.fenix.R
import org.mozilla.fenix.bharatx.BharatXBrand
import org.mozilla.fenix.bharatx.BharatXPages

object BharatXAds {
    const val PLACEMENT_ID = "dd500dee-43a9-40c7-8b70-afd51662f044"
    const val INTERSTITIAL_COOLDOWN_MS = 180_000L
    private var nextInterstitialAt = SystemClock.elapsedRealtime() + 60_000L

    private fun preferences(context: Context) = context.getSharedPreferences("bharatx_ad_choices", Context.MODE_PRIVATE)
    fun choiceMade(context: Context): Boolean = preferences(context).getBoolean("choice_made", false)
    fun allowed(context: Context): Boolean = preferences(context).getBoolean("ads_allowed", false)

    fun showChoices(activity: ComponentActivity, finished: (Boolean) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.bharatx_ads_choices)
            .setMessage(R.string.bharatx_ads_disclosure)
            .setPositiveButton(R.string.bharatx_ads_allow) { _, _ ->
                preferences(activity).edit().putBoolean("choice_made", true).putBoolean("ads_allowed", true).apply()
                finished(true)
            }
            .setNegativeButton(R.string.bharatx_ads_decline) { _, _ ->
                preferences(activity).edit().putBoolean("choice_made", true).putBoolean("ads_allowed", false).apply()
                val sdk = AppnextSDK.getInstance()
                if (sdk.isInitialized()) sdk.setConsent(false)
                finished(false)
            }
            .setNeutralButton(R.string.bharatx_vpn_privacy) { _, _ ->
                BharatXPages.openIfLocal(activity, BharatXBrand.PRIVACY)
                finished(false)
            }
            .setOnCancelListener { finished(false) }
            .show()
    }

    internal fun initialize(context: Context) {
        check(allowed(context))
        AppnextSDK.getInstance().initialize(context.applicationContext)
        AppnextSDK.getInstance().setConsent(true)
    }

    internal fun interstitialDue(): Boolean = SystemClock.elapsedRealtime() >= nextInterstitialAt
    internal fun interstitialShown() { nextInterstitialAt = SystemClock.elapsedRealtime() + INTERSTITIAL_COOLDOWN_MS }
}

/** Ads belong to this native VPN screen. No browsing URL, history, DNS or packet data is passed to Appnext. */
class BharatXAdSession(private val activity: ComponentActivity, private val bannerHolder: LinearLayout) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var banner: BannerView? = null
    private var bannerGeneration = 0
    private var interstitial: Interstitial? = null
    private var interstitialReady = false
    private var interstitialLoading = false
    private var fullscreen = false
    private var rewardLoading = false

    private fun alive() = !closed && !activity.isFinishing && !activity.isDestroyed
    private fun enabled() = alive() && BharatXAds.allowed(activity) && BharatXBilling.canShowAds()
    private fun eligible() = enabled() && !fullscreen && RikiVpn.getStatus().state == VpnState.DISCONNECTED
    private fun ui(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    fun reconcile() {
        try {
            reconcileInternal()
        } catch (_: RuntimeException) {
            hideBanner()
            interstitial = null
            interstitialReady = false
        }
    }

    private fun reconcileInternal() {
        if (!enabled()) {
            bannerGeneration++
            bannerHolder.removeAllViews()
            bannerHolder.visibility = View.GONE
            banner = null
            interstitial = null
            interstitialReady = false
            return
        }
        if (!eligible()) {
            hideBanner()
            banner = null
            return
        }
        BharatXAds.initialize(activity)
        if (banner == null) loadBanner()
        if (interstitial == null && !interstitialLoading && !interstitialReady) loadInterstitial()
    }

    private fun loadBanner() {
        val generation = ++bannerGeneration
        val view = BannerView(activity).apply {
            setSize(BannerSize.SMALL_BANNER)
            setSmartBanner(true)
            setPlacementId(BharatXAds.PLACEMENT_ID)
        }
        banner = view
        bannerHolder.removeAllViews()
        bannerHolder.visibility = View.INVISIBLE
        bannerHolder.addView(TextView(activity).apply {
            setText(R.string.bharatx_advertisement)
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
        })
        bannerHolder.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (50 * activity.resources.displayMetrics.density).toInt()))
        val timeout = Runnable { if (generation == bannerGeneration) hideBanner() }
        main.postDelayed(timeout, 20_000L)
        view.load(activity, object : AdCallbacks {
            override fun onAdClicked(packageName: String) = Unit
            override fun onAdImpressionReceived(packageName: String) = Unit
            override fun onAdsLoadedSuccessfully() = Unit
            override fun onViewLoadedSuccessfully() = ui {
                if (generation == bannerGeneration && eligible()) {
                    main.removeCallbacks(timeout)
                    bannerHolder.visibility = View.VISIBLE
                    bannerHolder.requestApplyInsets()
                }
            }
            override fun onViewError(error: AppnextError) = ui { if (generation == bannerGeneration) hideBanner() }
            override fun onAdsLoadedError(error: AppnextError) = ui { if (generation == bannerGeneration) hideBanner() }
        })
    }

    private fun hideBanner() {
        bannerGeneration++
        bannerHolder.removeAllViews() // SDK destroys its banner WebView when detached.
        bannerHolder.visibility = View.GONE
        // Keep the failed request marker until next screen visit; do not loop on no-fill.
    }

    private fun loadInterstitial() {
        val ad = Interstitial(BharatXAds.PLACEMENT_ID)
        interstitial = ad
        interstitialLoading = true
        ad.loadAds(activity, object : InterstitialLoadCallback {
            override fun onInterstitialLoaded() = ui {
                interstitialLoading = false
                interstitialReady = enabled() && interstitial === ad
            }
            override fun onInterstitialLoadError(error: AppnextError) = ui {
                interstitialLoading = false
                interstitialReady = false
            }
        }, "")
    }

    /** Called only after the user-requested VPN disconnect has actually finished. */
    fun afterDisconnect() {
        if (!eligible() || fullscreen || !interstitialReady || !BharatXAds.interstitialDue() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val ad = interstitial ?: return
        interstitialReady = false
        fullscreen = true
        ad.showAd(activity, object : InterstitialAdCallbacks {
            override fun onAdShown() = ui { BharatXAds.interstitialShown() }
            override fun onAdClicked(packageName: String) = Unit
            override fun onAdClosed() = ui { fullscreen = false; interstitial = null; reconcile() }
            override fun onAdError(error: AppnextError) = ui { fullscreen = false; interstitial = null }
        })
    }

    fun watchReward(serverId: String, complete: (Boolean) -> Unit) {
        if (!alive() || fullscreen || rewardLoading || !BharatXAds.allowed(activity)) { complete(false); return }
        if (BharatXBilling.isAdFree()) { complete(true); return }
        BharatXAds.initialize(activity)
        rewardLoading = true
        val requestId = UUID.randomUUID().toString()
        val ad = RewardedAd(BharatXAds.PLACEMENT_ID)
        val watch = RewardedVideoPolicy()
        var finished = false
        var timeout: Runnable? = null
        fun finish(success: Boolean) {
            if (finished) return
            finished = true
            timeout?.let { main.removeCallbacks(it) }
            fullscreen = false
            rewardLoading = false
            if (alive()) complete(success)
        }
        timeout = Runnable { finish(false) }
        main.postDelayed(timeout, 20_000L)
        ad.loadAds(activity, object : RewardedAdLoadCallback {
            override fun onRewardedAdLoadError(error: AppnextError) = ui { finish(false) }
            override fun onRewardedAdLoaded() = ui {
                if (finished || !alive() || !BharatXAds.allowed(activity)) { finish(false); return@ui }
                if (BharatXBilling.isAdFree()) { finish(true); return@ui }
                main.removeCallbacks(timeout)
                rewardLoading = false
                fullscreen = true
                ad.showAd(activity, object : RewardedAdCallbacks {
                    override fun onAdShown() = ui { watch.shown() }
                    override fun onAdClicked(packageName: String) = Unit
                    override fun onAdError(error: AppnextError) = ui { watch.error(); finish(false) }
                    override fun onAdClosed() = ui { finish(watch.close()) }
                    override fun onVideoEnded() = ui {
                        if (!finished && watch.videoEnded()) {
                            // The only ad callback that creates a connection credit.
                            BharatXPremiumAccess.rewardCompleted(serverId, requestId)
                        }
                    }
                })
            }
        }, "")
    }

    fun close() {
        closed = true
        bannerGeneration++
        bannerHolder.removeAllViews()
        banner = null
        interstitial = null
        interstitialReady = false
    }
}
