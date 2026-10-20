package com.rekixo.ludopaws.feature.store.data.ads

import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentRequestParameters

/** Every user, including unknown age, receives the most restrictive ad treatment. */
internal object FamilyAdsPrivacy {
    fun configureBeforeInitialization() {
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration().toBuilder()
                .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
    }

    fun consentParameters(): ConsentRequestParameters =
        ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build()

    fun request(): AdRequest {
        configureBeforeInitialization()
        return AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
            .build()
    }
}
