package com.xovrix.quickvoicereminder.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // Official Google AdMob Test Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShownTime: Long = 0
    private var scheduleActionCount = 0
    private const val INTERSTITIAL_COOLDOWN_MS = 15_000L // Reduced cooldown for better flow

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) return

        try {
            isLoadingInterstitial = true
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                        Log.d(TAG, "Interstitial ad loaded successfully")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                        Log.w(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            interstitialAd = null
            isLoadingInterstitial = false
            Log.w(TAG, "Failed to initiate interstitial load", e)
        }
    }

    fun showInterstitial(
        activity: Activity,
        isAdsFree: Boolean = false,
        force: Boolean = false,
        onDismissed: () -> Unit = {}
    ) {
        if (isAdsFree) {
            onDismissed()
            return
        }

        val now = System.currentTimeMillis()
        if (!force && (now - lastInterstitialShownTime < INTERSTITIAL_COOLDOWN_MS)) {
            Log.d(TAG, "Skipping interstitial due to cooldown")
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitial(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed")
                }
            }
            try {
                ad.show(activity)
            } catch (e: Exception) {
                Log.w(TAG, "Error displaying interstitial ad", e)
                interstitialAd = null
                onDismissed()
            }
        } else {
            Log.d(TAG, "Interstitial ad not ready, loading for next time")
            loadInterstitial(activity)
            onDismissed()
        }
    }

    /**
     * Increment the schedule counter and show an ad only every 3rd time.
     */
    fun showInterstitialEveryNth(
        activity: Activity,
        isAdsFree: Boolean = false,
        n: Int = 3,
        onDismissed: () -> Unit = {}
    ) {
        if (isAdsFree) {
            onDismissed()
            return
        }

        scheduleActionCount++
        if (scheduleActionCount >= n) {
            scheduleActionCount = 0
            showInterstitial(activity, isAdsFree, force = true, onDismissed = onDismissed)
        } else {
            Log.d(TAG, "Schedule count: $scheduleActionCount. Ad will show after $n schedules.")
            onDismissed()
        }
    }
}
