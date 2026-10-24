package com.xovrix.quickvoicereminder.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class AdsConsentManager(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    fun gatherConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onComplete(consentInformation.canRequestAds())
                }
            },
            { onComplete(consentInformation.canRequestAds()) }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}
