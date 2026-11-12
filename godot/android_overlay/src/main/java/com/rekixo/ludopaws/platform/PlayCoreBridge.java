package com.rekixo.ludopaws.platform;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import org.json.JSONObject;

/**
 * Live Play Core features (live versions): in-app update (PlayUpdateCoordinator), in-app review
 * (NaturalReviewController), install referrer + Play Integrity (ReferralRepository). Results go
 * to GDScript as JSON through the plugin's "playcore" signal: (kind, json). Only builds
 * installed from Google Play get real update / review / referrer answers.
 */
final class PlayCoreBridge {
    interface Sink { void emit(String kind, String json); }
    static final int UPDATE_REQUEST = 18421;

    private final Activity activity;
    private final Sink sink;
    private AppUpdateManager updates;
    private AppUpdateInfo lastInfo;
    private boolean listening = false;

    private final InstallStateUpdatedListener listener;

    PlayCoreBridge(Activity activity, Sink sink) {
        this.activity = activity;
        this.sink = sink;
        this.listener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) sink.emit("update_downloaded", "{}");
            else if (state.installStatus() == InstallStatus.DOWNLOADING) {
                sink.emit("update_progress", "{\"done\":" + state.bytesDownloaded() + ",\"total\":" + state.totalBytesToDownload() + "}");
            }
        };
    }

    private AppUpdateManager updates() {
        if (updates == null) updates = AppUpdateManagerFactory.create(activity);
        if (!listening) {
            try { updates.registerListener(listener); listening = true; } catch (Exception ignored) { }
        }
        return updates;
    }

    /** {availability, installStatus, priority, availableVersionCode, flexible, immediate}. */
    void checkUpdate() {
        try {
            updates().getAppUpdateInfo().addOnCompleteListener(task -> {
                try {
                    if (!task.isSuccessful() || task.getResult() == null) { sink.emit("update_info", "{\"ok\":false}"); return; }
                    AppUpdateInfo info = task.getResult();
                    lastInfo = info;
                    String availability;
                    switch (info.updateAvailability()) {
                        case UpdateAvailability.UPDATE_AVAILABLE: availability = "AVAILABLE"; break;
                        case UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS: availability = "IN_PROGRESS"; break;
                        case UpdateAvailability.UPDATE_NOT_AVAILABLE: availability = "NOT_AVAILABLE"; break;
                        default: availability = "UNKNOWN";
                    }
                    sink.emit("update_info", new JSONObject().put("ok", true).put("availability", availability)
                        .put("downloaded", info.installStatus() == InstallStatus.DOWNLOADED)
                        .put("priority", info.updatePriority()).put("availableVersionCode", info.availableVersionCode())
                        .put("flexible", info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
                        .put("immediate", info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)).toString());
                } catch (Exception e) {
                    sink.emit("update_info", "{\"ok\":false}");
                }
            });
        } catch (Exception e) {
            sink.emit("update_info", "{\"ok\":false}");
        }
    }

    boolean startUpdate(boolean immediate) {
        if (lastInfo == null) return false;
        try {
            return updates().startUpdateFlowForResult(lastInfo, activity,
                AppUpdateOptions.newBuilder(immediate ? AppUpdateType.IMMEDIATE : AppUpdateType.FLEXIBLE).build(), UPDATE_REQUEST);
        } catch (Exception e) {
            return false;
        }
    }

    void completeUpdate() {
        try { updates().completeUpdate(); } catch (Exception ignored) { }
    }

    /** The Play review sheet (Play decides whether it shows; nothing is reported back). */
    void requestReview() {
        try {
            ReviewManager manager = ReviewManagerFactory.create(activity);
            manager.requestReviewFlow().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) { sink.emit("review", "{\"ok\":false}"); return; }
                sink.emit("review_ready", "{}");
                try {
                    manager.launchReviewFlow(activity, task.getResult()).addOnCompleteListener(done -> sink.emit("review", "{\"ok\":true}"));
                } catch (Exception e) {
                    sink.emit("review", "{\"ok\":false}");
                }
            });
        } catch (Exception e) {
            sink.emit("review", "{\"ok\":false}");
        }
    }

    /** {ok, referrer, installAt, clickedAt} (server seconds -> ms) or {ok:false, code}. */
    void readInstallReferrer() {
        final InstallReferrerClient client = InstallReferrerClient.newBuilder(activity.getApplicationContext()).build();
        try {
            client.startConnection(new InstallReferrerStateListener() {
                @Override public void onInstallReferrerSetupFinished(int code) {
                    try {
                        if (code != InstallReferrerClient.InstallReferrerResponse.OK) {
                            sink.emit("referrer", "{\"ok\":false,\"code\":" + code + "}");
                            return;
                        }
                        ReferrerDetails d = client.getInstallReferrer();
                        String raw = d.getInstallReferrer();
                        sink.emit("referrer", new JSONObject().put("ok", true).put("referrer", raw == null ? "" : raw)
                            .put("installAt", d.getInstallBeginTimestampServerSeconds() * 1000L)
                            .put("clickedAt", d.getReferrerClickTimestampServerSeconds() * 1000L).toString());
                    } catch (Exception e) {
                        sink.emit("referrer", "{\"ok\":false,\"code\":-1}");
                    } finally {
                        try { client.endConnection(); } catch (Exception ignored) { }
                    }
                }
                @Override public void onInstallReferrerServiceDisconnected() { }
            });
        } catch (Exception e) {
            sink.emit("referrer", "{\"ok\":false,\"code\":-1}");
        }
    }

    /** Standard Play Integrity token for the server challenge (live ReferralRepository.bindPending). */
    void integrityToken(String cloudProjectNumber, String requestHash) {
        try {
            StandardIntegrityManager manager = IntegrityManagerFactory.createStandard(activity.getApplicationContext());
            manager.prepareIntegrityToken(StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(Long.parseLong(cloudProjectNumber)).build())
                .addOnCompleteListener(prepared -> {
                    if (!prepared.isSuccessful()) { sink.emit("integrity", "{\"ok\":false}"); return; }
                    prepared.getResult().request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                        .setRequestHash(requestHash).build()).addOnCompleteListener(token -> {
                            try {
                                sink.emit("integrity", token.isSuccessful()
                                    ? new JSONObject().put("ok", true).put("token", token.getResult().token()).toString() : "{\"ok\":false}");
                            } catch (Exception e) {
                                sink.emit("integrity", "{\"ok\":false}");
                            }
                        });
                });
        } catch (Exception e) {
            sink.emit("integrity", "{\"ok\":false}");
        }
    }

    /** {versionCode, firstInstall, lastUpdate} for the live What's New rule. */
    String installInfo() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return new JSONObject().put("versionCode", code).put("versionName", info.versionName)
                .put("firstInstall", info.firstInstallTime).put("lastUpdate", info.lastUpdateTime).toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    void openStore() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + activity.getPackageName())).setPackage("com.android.vending"));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + activity.getPackageName())));
            } catch (Exception ignored) { }
        }
    }
}
