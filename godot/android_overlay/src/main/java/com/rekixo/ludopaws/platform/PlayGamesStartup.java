package com.rekixo.ludopaws.platform;

import android.app.Application;
import android.content.Context;

import com.google.android.gms.games.PlayGamesSdk;

/**
 * Live StartupPlayGames initialization rules: optional PGS v2 platform authentication, only
 * after a durable adult age choice and only in the production package with a project id.
 * The PGS init provider is removed from the manifest; missing Play services must never stop
 * an offline launch. Platform identity never changes the game's account or wallet.
 */
public final class PlayGamesStartup {
    static volatile boolean initialized = false;

    private PlayGamesStartup() { }

    public static synchronized boolean initialize(Application application) {
        if (initialized) return true;
        try {
            PlayGamesSdk.initialize(application);
            initialized = true;
        } catch (Throwable ignored) {
            // Missing Play services / configuration must never prevent offline launch.
        }
        return initialized;
    }

    /** Called from the activity before the engine starts: the SDK registers its automatic
     *  authenticator for the next activity creation, so initializing here (when allowed)
     *  gives the same automatic sign-in as live without recreating the game activity. */
    public static void initializeAtLaunchIfAllowed(Context context) {
        if (LudoPawsPlatformPlugin.familyBand(context) == 3 && LudoPawsPlatformPlugin.playGamesConfigured(context)) {
            initialize((Application) context.getApplicationContext());
        }
    }
}
