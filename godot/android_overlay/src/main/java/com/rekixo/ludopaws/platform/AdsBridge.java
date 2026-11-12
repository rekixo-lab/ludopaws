package com.rekixo.ludopaws.platform;

import android.app.Activity;
import android.os.Bundle;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Google Mobile Ads 25.5.0 + UMP 4.0.0 with the live FamilyAdsPrivacy configuration (every
 * user gets the most restrictive treatment: CHILD age-restricted treatment, max rating G,
 * consent tagged under age, non-personalized requests). Rewarded ads carry the live SSV
 * user id + custom data; the server credits only signed callbacks. Events go to GDScript
 * through the plugin's "ads" signal: (kind, detail).
 */
final class AdsBridge {
    interface Sink { void emit(String kind, String detail); }

    private final Activity activity;
    private final Sink sink;
    private final ConsentInformation consent;
    private boolean initialized = false;
    private boolean initializing = false;
    private RewardedAd rewarded;
    private InterstitialAd interstitial;
    private boolean rewardedLoading = false;
    private boolean interstitialLoading = false;

    AdsBridge(Activity activity, Sink sink) {
        this.activity = activity;
        this.sink = sink;
        consent = UserMessagingPlatform.getConsentInformation(activity);
    }

    private static void configure() {
        MobileAds.setRequestConfiguration(MobileAds.getRequestConfiguration().toBuilder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build());
    }

    private static AdRequest request() {
        configure();
        Bundle extras = new Bundle();
        extras.putString("npa", "1");
        return new AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter.class, extras).build();
    }

    boolean canRequestAds() { return consent.canRequestAds(); }

    /** Consent update (form only when asked to show), then SDK init when allowed. */
    void prepare(boolean showForm) {
        activity.runOnUiThread(() -> {
            configure();
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build();
            consent.requestConsentInfoUpdate(activity, params, () -> {
                if (showForm) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> initIfAllowed());
                } else {
                    initIfAllowed();
                }
            }, error -> initIfAllowed());
        });
    }

    private void initIfAllowed() {
        if (!consent.canRequestAds()) { sink.emit("consent", "blocked"); return; }
        if (initialized) { sink.emit("consent", "ready"); return; }
        if (initializing) return;
        initializing = true;
        configure();
        MobileAds.initialize(activity, status -> activity.runOnUiThread(() -> {
            initializing = false;
            initialized = true;
            sink.emit("consent", "ready");
        }));
    }

    void loadRewarded(String unit) {
        activity.runOnUiThread(() -> {
            if (!initialized || rewarded != null || rewardedLoading || !consent.canRequestAds()) return;
            rewardedLoading = true;
            RewardedAd.load(activity, unit, request(), new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) { rewardedLoading = false; rewarded = ad; sink.emit("rewarded_loaded", ""); }
                @Override public void onAdFailedToLoad(LoadAdError error) { rewardedLoading = false; sink.emit("rewarded_failed", error.getMessage()); }
            });
        });
    }

    boolean rewardedReady() { return rewarded != null && consent.canRequestAds(); }

    void showRewarded(String userId, String customData) {
        activity.runOnUiThread(() -> {
            RewardedAd ad = rewarded;
            if (ad == null || !consent.canRequestAds()) { sink.emit("rewarded_finished", "false"); return; }
            rewarded = null;
            final boolean[] delivered = {false};
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { sink.emit("rewarded_finished", delivered[0] ? "true" : "false"); }
                @Override public void onAdFailedToShowFullScreenContent(AdError error) { sink.emit("rewarded_finished", "false"); }
            });
            try {
                ad.setServerSideVerificationOptions(new ServerSideVerificationOptions.Builder()
                    .setUserId(userId).setCustomData(customData).build());
                ad.show(activity, reward -> {
                    if (!delivered[0]) { delivered[0] = true; sink.emit("rewarded_earned", ""); }
                });
            } catch (RuntimeException e) {
                sink.emit("rewarded_finished", "false");
            }
        });
    }

    void loadInterstitial(String unit) {
        activity.runOnUiThread(() -> {
            if (!initialized || interstitial != null || interstitialLoading || !consent.canRequestAds()) return;
            interstitialLoading = true;
            InterstitialAd.load(activity, unit, request(), new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) { interstitialLoading = false; interstitial = ad; }
                @Override public void onAdFailedToLoad(LoadAdError error) { interstitialLoading = false; }
            });
        });
    }

    boolean interstitialReady() { return interstitial != null && consent.canRequestAds(); }

    void showInterstitial() {
        activity.runOnUiThread(() -> {
            InterstitialAd ad = interstitial;
            if (ad == null || !consent.canRequestAds()) { sink.emit("interstitial_finished", "false"); return; }
            interstitial = null;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { sink.emit("interstitial_finished", "true"); }
                @Override public void onAdFailedToShowFullScreenContent(AdError error) { sink.emit("interstitial_finished", "false"); }
            });
            try { ad.show(activity); } catch (RuntimeException e) { sink.emit("interstitial_finished", "false"); }
        });
    }

    void privacyOptions() {
        activity.runOnUiThread(() -> UserMessagingPlatform.showPrivacyOptionsForm(activity, error ->
            sink.emit("privacy", error == null ? "ok" : "failed")));
    }
}
