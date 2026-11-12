package com.rekixo.ludopaws.platform;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;
import org.json.JSONObject;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.gms.games.PlayGames;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Ludo Paws platform bridge (project-owned Godot Android plugin).
 *
 * Account session storage is the live app's AccountSessionStore, unchanged: the same
 * SharedPreferences file, Android Keystore alias, AES/GCM transformation, AAD strings and
 * validation rules. Because the update keeps the package (and therefore the app's Keystore),
 * an existing player's session written by the Kotlin app is read here as-is: no migration and
 * no sign-in needed. Nothing secret ever leaves this class except to the game process.
 */
public class LudoPawsPlatformPlugin extends GodotPlugin {
    private static final String PREFS_NAME = "ludoproof_account_session_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "ludoproof_account_session_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_VERSION = "version";
    private static final String KEY_ACCOUNT_MODE = "account_mode";
    private static final String KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID = "identity_recovery_pending_account_id";
    private static final String MODE_GUEST = "GUEST";
    private static final String MODE_GOOGLE = "GOOGLE";
    private static final String KEY_IV = "session_iv";
    private static final String KEY_CIPHERTEXT = "session_ciphertext";
    private static final String KEY_GUEST_REQUEST_IV = "guest_request_iv";
    private static final String KEY_GUEST_REQUEST_CIPHERTEXT = "guest_request_ciphertext";
    private static final String KEY_PENDING_REFRESH_REQUEST_ID = "pending_refresh_request_id";
    private static final byte[] SESSION_AAD = "ludoproof:account-session:v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GUEST_REQUEST_AAD = "ludoproof:guest-request:v1".getBytes(StandardCharsets.UTF_8);
    private static final Pattern ACCOUNT_ID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Pattern OPAQUE_BILLING_ID = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern GUEST_DELETION_CREDENTIAL = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern REFRESH_TOKEN = Pattern.compile("^lpr_[A-Za-z0-9_-]{43}$");

    public LudoPawsPlatformPlugin(Godot godot) {
        super(godot);
    }

    @Override
    public String getPluginName() {
        return "LudoPawsPlatform";
    }

    /** google_credential(status, idToken): OK / CANCELLED / NO_CREDENTIAL / UNSUPPORTED / FAILED.
     *  play_games(action, ok, displayName): action = "status" or "sign_in". */
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new HashSet<>();
        signals.add(new SignalInfo("google_credential", String.class, String.class));
        signals.add(new SignalInfo("play_games", String.class, Boolean.class, String.class));
        signals.add(new SignalInfo("billing", String.class, String.class));
        signals.add(new SignalInfo("ads", String.class, String.class));
        signals.add(new SignalInfo("playcore", String.class, String.class));
        return signals;
    }

    private SharedPreferences prefs() {
        return getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    /** The saved session as JSON (same fields as AccountSession), or "" (invalid data is cleared, like live). */
    @UsedByGodot
    public synchronized String sessionLoad() {
        SharedPreferences p = prefs();
        String iv = p.getString(KEY_IV, null);
        String ct = p.getString(KEY_CIPHERTEXT, null);
        if (iv == null || ct == null) return "";
        try {
            if (p.getInt(KEY_VERSION, 0) != 1) throw new IllegalStateException("version");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            cipher.updateAAD(SESSION_AAD);
            JSONObject json = new JSONObject(new String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), StandardCharsets.UTF_8));
            boolean legacyGuest = MODE_GUEST.equals(p.getString(KEY_ACCOUNT_MODE, null));
            boolean guest = json.optBoolean("guestRecoveryEligible", legacyGuest);
            JSONObject out = new JSONObject()
                .put("accountId", json.getString("accountId"))
                .put("sessionId", json.getString("sessionId"))
                .put("accessToken", json.getString("accessToken"))
                .put("refreshToken", json.getString("refreshToken"))
                .put("billingAccountId", json.getString("billingAccountId"))
                .put("accessExpiresAt", json.getLong("accessExpiresAt"))
                .put("sessionExpiresAt", json.getLong("sessionExpiresAt"))
                .put("displayName", json.optString("displayName", "Guest User"))
                .put("guestRecoveryEligible", guest)
                .put("identityRecoveryPending", json.optBoolean("identityRecoveryPending",
                    json.optString("accountId").equals(p.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null))));
            String deletion = json.optString("guestDeletionCredential");
            if (guest && GUEST_DELETION_CREDENTIAL.matcher(deletion).matches()) out.put("guestDeletionCredential", deletion);
            if (!ACCOUNT_ID.matcher(out.getString("accountId")).matches()
                || !ACCOUNT_ID.matcher(out.getString("sessionId")).matches()
                || !OPAQUE_BILLING_ID.matcher(out.getString("billingAccountId")).matches()
                || out.getString("accessToken").split("\\.", -1).length != 3
                || !REFRESH_TOKEN.matcher(out.getString("refreshToken")).matches()) {
                throw new IllegalStateException("invalid session");
            }
            return out.toString();
        } catch (Exception e) {
            sessionClear();
            return "";
        }
    }

    /** AccountSessionStore.save (same validation, encryption and markers). */
    @UsedByGodot
    public synchronized boolean sessionSave(String sessionJson) {
        try {
            JSONObject s = new JSONObject(sessionJson);
            boolean guest = s.optBoolean("guestRecoveryEligible", false);
            if (!ACCOUNT_ID.matcher(s.getString("accountId")).matches()
                || !ACCOUNT_ID.matcher(s.getString("sessionId")).matches()
                || !OPAQUE_BILLING_ID.matcher(s.getString("billingAccountId")).matches()
                || s.getString("accessToken").split("\\.", -1).length != 3
                || !REFRESH_TOKEN.matcher(s.getString("refreshToken")).matches()
                || s.getLong("accessExpiresAt") <= 0 || s.getLong("sessionExpiresAt") <= 0) {
                return false;
            }
            String name = s.optString("displayName", "Guest User");
            if (name.length() > 24) name = name.substring(0, 24);
            JSONObject json = new JSONObject()
                .put("accountId", s.getString("accountId"))
                .put("sessionId", s.getString("sessionId"))
                .put("accessToken", s.getString("accessToken"))
                .put("refreshToken", s.getString("refreshToken"))
                .put("billingAccountId", s.getString("billingAccountId"))
                .put("accessExpiresAt", s.getLong("accessExpiresAt"))
                .put("sessionExpiresAt", s.getLong("sessionExpiresAt"))
                .put("displayName", name)
                .put("guestRecoveryEligible", guest);
            String deletion = s.optString("guestDeletionCredential", "");
            if (guest && !deletion.isEmpty()) {
                if (!GUEST_DELETION_CREDENTIAL.matcher(deletion).matches()) return false;
                json.put("guestDeletionCredential", deletion);
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            cipher.updateAAD(SESSION_AAD);
            byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
            SharedPreferences p = prefs();
            SharedPreferences.Editor editor = p.edit()
                .putInt(KEY_VERSION, 1)
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_ACCOUNT_MODE, guest ? MODE_GUEST : MODE_GOOGLE)
                .remove(KEY_PENDING_REFRESH_REQUEST_ID);
            if (!guest) editor.remove(KEY_GUEST_REQUEST_IV).remove(KEY_GUEST_REQUEST_CIPHERTEXT);
            if (s.optBoolean("identityRecoveryPending", false)) {
                editor.putString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, s.getString("accountId"));
            } else if (s.getString("accountId").equals(p.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null))) {
                editor.remove(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID);
            }
            return editor.commit();
        } catch (Exception e) {
            return false;
        }
    }

    @UsedByGodot
    public synchronized boolean sessionClear() {
        return prefs().edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).remove(KEY_VERSION)
            .remove(KEY_PENDING_REFRESH_REQUEST_ID).commit();
    }

    @UsedByGodot
    public String lastAccountMode() {
        String mode = prefs().getString(KEY_ACCOUNT_MODE, null);
        return (MODE_GUEST.equals(mode) || MODE_GOOGLE.equals(mode)) ? mode : "";
    }

    /** The Keystore-encrypted idempotency id that always reopens this installation's guest. */
    @UsedByGodot
    public synchronized String guestRequestId() {
        SharedPreferences p = prefs();
        String iv = p.getString(KEY_GUEST_REQUEST_IV, null);
        String ct = p.getString(KEY_GUEST_REQUEST_CIPHERTEXT, null);
        if (iv != null && ct != null) {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                cipher.updateAAD(GUEST_REQUEST_AAD);
                String id = new String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), StandardCharsets.UTF_8).trim().toLowerCase();
                if (ACCOUNT_ID.matcher(id).matches()) return id;
            } catch (Exception ignored) {
            }
            p.edit().remove(KEY_GUEST_REQUEST_IV).remove(KEY_GUEST_REQUEST_CIPHERTEXT).commit();
        }
        String generated = UUID.randomUUID().toString().toLowerCase();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            cipher.updateAAD(GUEST_REQUEST_AAD);
            byte[] encrypted = cipher.doFinal(generated.getBytes(StandardCharsets.UTF_8));
            if (!p.edit()
                .putString(KEY_GUEST_REQUEST_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(KEY_GUEST_REQUEST_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit()) return "";
        } catch (Exception e) {
            return "";
        }
        return generated;
    }

    @UsedByGodot
    public synchronized String pendingRefreshRequestId() {
        SharedPreferences p = prefs();
        String existing = p.getString(KEY_PENDING_REFRESH_REQUEST_ID, null);
        if (existing != null && ACCOUNT_ID.matcher(existing).matches()) return existing;
        String generated = UUID.randomUUID().toString().toLowerCase();
        return p.edit().putString(KEY_PENDING_REFRESH_REQUEST_ID, generated).commit() ? generated : "";
    }

    @UsedByGodot
    public boolean hasPendingIdentityRecovery() {
        String id = prefs().getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null);
        return id != null && ACCOUNT_ID.matcher(id).matches();
    }

    /** AccountSessionStore.completeIdentityRecovery. */
    @UsedByGodot
    public synchronized boolean sessionCompleteIdentityRecovery(String accountId) {
        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) return false;
        String current = sessionLoad();
        try {
            if (!current.isEmpty()) {
                JSONObject s = new JSONObject(current);
                if (accountId.equals(s.getString("accountId"))) {
                    s.put("identityRecoveryPending", false);
                    return sessionSave(s.toString());
                }
            }
        } catch (Exception e) {
            return false;
        }
        return prefs().edit().remove(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID).commit();
    }

    // ------------------------------------------------------------ family age screening
    // Live FamilyAgeScreen store, unchanged: only the band + policy version, never the age.

    private static final String FAMILY_STORE = "family_privacy_v1";
    private static final String FAMILY_KEY = "age_band";
    private static final String FAMILY_POLICY_KEY = "entry_policy_version";
    private static final int FAMILY_POLICY_VERSION = 1;

    /** Saved band 0-4 under the current policy, or -1 (no durable choice). */
    @UsedByGodot
    public int ageBand() {
        return familyBand(getActivity());
    }

    public static int familyBand(Context context) {
        try {
            SharedPreferences p = context.getSharedPreferences(FAMILY_STORE, Context.MODE_PRIVATE);
            int band = p.getInt(FAMILY_KEY, -1);
            return p.getInt(FAMILY_POLICY_KEY, 0) == FAMILY_POLICY_VERSION && band >= 0 && band <= 4 ? band : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** FamilyAgeScreen.saveChoice (band already mapped from the age); rolls back on failure. */
    @UsedByGodot
    public boolean ageBandSave(int band) {
        if (band < 0 || band > 3) return false;
        SharedPreferences p = getActivity().getSharedPreferences(FAMILY_STORE, Context.MODE_PRIVATE);
        int oldBand = -1;
        int oldPolicy = 0;
        try { oldBand = p.getInt(FAMILY_KEY, -1); oldPolicy = p.getInt(FAMILY_POLICY_KEY, 0); } catch (Exception ignored) { }
        boolean saved;
        try { saved = p.edit().putInt(FAMILY_KEY, band).putInt(FAMILY_POLICY_KEY, FAMILY_POLICY_VERSION).commit(); }
        catch (Exception e) { saved = false; }
        if (!saved) {
            try {
                SharedPreferences.Editor rollback = p.edit().remove(FAMILY_KEY).remove(FAMILY_POLICY_KEY);
                if (oldBand >= 0 && oldBand <= 4) rollback.putInt(FAMILY_KEY, oldBand);
                if (oldPolicy > 0) rollback.putInt(FAMILY_POLICY_KEY, oldPolicy);
                rollback.commit();
            } catch (Exception ignored) { }
        }
        return saved;
    }

    // ------------------------------------------------------------ Google sign-in (Credential Manager)

    private CancellationSignal credentialCancellation;

    /** Live ProfileActivity.launchGoogleCredentialPicker: explicit Sign in with Google with the
     *  server nonce. The ID token goes only to GDScript (then to the account server); never logged. */
    @UsedByGodot
    public void googleSignIn(String serverClientId, String nonce) {
        Activity activity = getActivity();
        if (activity == null || serverClientId == null || serverClientId.trim().isEmpty() || nonce == null || nonce.isEmpty()) {
            emitSignal("google_credential", "UNSUPPORTED", "");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                if (credentialCancellation != null) credentialCancellation.cancel();
                credentialCancellation = new CancellationSignal();
                GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(serverClientId.trim()).setNonce(nonce).build();
                GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
                CredentialManager.create(activity).getCredentialAsync(activity, request, credentialCancellation, activity.getMainExecutor(),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            Credential credential = result.getCredential();
                            if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                                emitSignal("google_credential", "UNSUPPORTED", "");
                                return;
                            }
                            try {
                                emitSignal("google_credential", "OK", GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                            } catch (Exception e) {
                                emitSignal("google_credential", "INVALID", "");
                            }
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            String type = e.getType() == null ? "" : e.getType().toLowerCase();
                            emitSignal("google_credential", type.contains("cancel") ? "CANCELLED"
                                : (e instanceof NoCredentialException ? "NO_CREDENTIAL" : "FAILED"), "");
                        }
                    });
            } catch (Throwable t) {
                emitSignal("google_credential", "FAILED", "");
            }
        });
    }

    @Override
    public void onMainDestroy() {
        if (billing != null) billing.close();
        if (credentialCancellation != null) credentialCancellation.cancel();
        super.onMainDestroy();
    }

    // ------------------------------------------------------------ Google Play Games (PGS v2)

    /** StartupPlayGames.configured: production package + positive project id resource. */
    public static boolean playGamesConfigured(Context context) {
        if (!"com.rekixo.ludopaws".equals(context.getPackageName())) return false;
        int id = context.getResources().getIdentifier("game_services_project_id", "string", context.getPackageName());
        if (id == 0) return false;
        try { return Long.parseLong(context.getString(id).trim()) > 0L; } catch (Exception e) { return false; }
    }

    @UsedByGodot
    public boolean playGamesConfigured() {
        return getActivity() != null && playGamesConfigured(getActivity());
    }

    @UsedByGodot
    public boolean playGamesInitialized() {
        return PlayGamesStartup.initialized;
    }

    /** Explicit Profile sign-in initializes the SDK itself (initializeForExplicitSignIn). */
    @UsedByGodot
    public boolean playGamesInitialize() {
        Activity activity = getActivity();
        if (activity == null || familyBand(activity) != 3 || !playGamesConfigured(activity)) return false;
        return PlayGamesStartup.initialize(activity.getApplication());
    }

    /** Reads the SDK's automatic attempt (never opens a sign-in UI). */
    @UsedByGodot
    public void playGamesStatus() {
        playGamesTask("status");
    }

    /** Explicit sign-in from the Profile action. */
    @UsedByGodot
    public void playGamesSignIn() {
        playGamesTask("sign_in");
    }

    private void playGamesTask(String action) {
        Activity activity = getActivity();
        if (activity == null || !PlayGamesStartup.initialized || familyBand(activity) != 3) {
            emitSignal("play_games", action, false, "");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                com.google.android.gms.games.GamesSignInClient client = PlayGames.getGamesSignInClient(activity);
                (action.equals("sign_in") ? client.signIn() : client.isAuthenticated()).addOnCompleteListener(task -> {
                    boolean ok = task.isSuccessful() && task.getResult() != null && task.getResult().isAuthenticated();
                    if (!ok) { emitSignal("play_games", action, false, ""); return; }
                    PlayGames.getPlayersClient(activity).getCurrentPlayer().addOnCompleteListener(player -> {
                        String name = player.isSuccessful() && player.getResult() != null ? player.getResult().getDisplayName() : "";
                        emitSignal("play_games", action, true, name == null ? "" : name);
                    });
                });
            } catch (Throwable t) {
                emitSignal("play_games", action, false, "");
            }
        });
    }

    // ------------------------------------------------------------ live SharedPreferences bridge
    // V2 keeps the live app's own preference files and keys, so a player's data stays valid
    // across the update with no conversion. Only these plain (non-secret) files are reachable;
    // the account session and encrypted identity stores never are.

    private static final Set<String> SHARED_PREFS = new HashSet<>(Arrays.asList(
        "ludoproof_wallet",
        "ludo_paws_character_selection_v1",
        "ludopaws_store_catalog_cache_v1",
        "ludoproof_game_settings",
        "ludoproof_profile",
        "weekly_challenges_v1",
        "ludo_paws_offline_character_setup_v1",
        "ludoproof_computer_game",
        "ludoproof_offline_game",
        "ludopaws_pending_local_results_v1",
        "ludoproof_secure_session",
        "ludoproof_pending_forfeits",
        "ludoproof_session",
        "ludoproof_leaderboard_identity",
        "ludoproof_friend_identity_v1",
        "ludoproof_leaderboard_cache",
        "ludoproof_public_matchmaking_v1",
        "ludoproof_pending_operation",
        "ludoproof_team_matchmaking",
        "pending_ad_rewards_v1",
        "ludopaws_update_experience_v1",
        "automatic_prompts_v2",
        "engagement_v1",
        "friendship_attribution",
        "referral_popup_v1",
        "ludopaws_ad_breaks"
    ));

    /** {key: {t: s|i|l|b|f|ss, v: value}} as JSON, or "" when the file is not allowed. */
    @UsedByGodot
    public String prefsRead(String name) {
        if (!SHARED_PREFS.contains(name) || getActivity() == null) return "";
        try {
            JSONObject out = new JSONObject();
            for (Map.Entry<String, ?> e : getActivity().getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof String) item.put("t", "s").put("v", v);
                else if (v instanceof Integer) item.put("t", "i").put("v", v);
                else if (v instanceof Long) item.put("t", "l").put("v", String.valueOf(v));
                else if (v instanceof Boolean) item.put("t", "b").put("v", v);
                else if (v instanceof Float) item.put("t", "f").put("v", ((Float) v).doubleValue());
                else if (v instanceof Set) {
                    JSONArray arr = new JSONArray();
                    for (Object o : (Set<?>) v) arr.put(String.valueOf(o));
                    item.put("t", "ss").put("v", arr);
                } else continue;
                out.put(e.getKey(), item);
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Applies [{op: put|remove, k, t, v}] in one commit (SharedPreferences.Editor.commit). */
    @UsedByGodot
    public boolean prefsWrite(String name, String opsJson) {
        if (!SHARED_PREFS.contains(name) || getActivity() == null) return false;
        try {
            JSONArray ops = new JSONArray(opsJson);
            SharedPreferences.Editor editor = getActivity().getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            for (int i = 0; i < ops.length(); i++) {
                JSONObject op = ops.getJSONObject(i);
                String key = op.getString("k");
                if ("remove".equals(op.getString("op"))) { editor.remove(key); continue; }
                switch (op.getString("t")) {
                    case "s": editor.putString(key, op.getString("v")); break;
                    case "i": editor.putInt(key, op.getInt("v")); break;
                    case "l": editor.putLong(key, Long.parseLong(op.getString("v"))); break;
                    case "b": editor.putBoolean(key, op.getBoolean("v")); break;
                    case "f": editor.putFloat(key, (float) op.getDouble("v")); break;
                    case "ss": {
                        JSONArray arr = op.getJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int j = 0; j < arr.length(); j++) set.add(arr.getString(j));
                        editor.putStringSet(key, set);
                        break;
                    }
                    default: return false;
                }
            }
            return editor.commit();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ live Keystore keys (AES/GCM)
    // The live stores' own Keystore aliases, so their ciphertexts written by the Kotlin app
    // decrypt after the update. Only these aliases; the account session key stays private
    // to sessionLoad / sessionSave.

    private static final Set<String> KEY_ALIASES = new HashSet<>(Arrays.asList(
        "ludoproof_player_session_v1",
        "ludoproof_friend_identity_key_v1",
        "ludoproof_leaderboard_identity_key_v2",
        "ludoproof_pending_play_purchases_key_v1"
    ));

    private static SecretKey aliasKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(alias, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    /** {"iv": b64, "ct": b64} or "" on failure. An empty aad means no AAD. */
    @UsedByGodot
    public String keystoreEncrypt(String alias, String aad, String plaintext) {
        if (!KEY_ALIASES.contains(alias)) return "";
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aliasKey(alias));
            if (aad != null && !aad.isEmpty()) cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP)).toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** {"ok": true, "v": plaintext} or {"ok": false}. */
    @UsedByGodot
    public String keystoreDecrypt(String alias, String aad, String ivB64, String ctB64) {
        try {
            if (!KEY_ALIASES.contains(alias)) return "{\"ok\":false}";
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aliasKey(alias), new GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)));
            if (aad != null && !aad.isEmpty()) cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            String v = new String(cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            return new JSONObject().put("ok", true).put("v", v).toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    // ------------------------------------------------------------ links and sharing

    /** The VIEW link that opened / resumed the app (once), or "". */
    @UsedByGodot
    public String takeLaunchUri() {
        Activity activity = getActivity();
        if (activity == null || activity.getIntent() == null) return "";
        android.content.Intent intent = activity.getIntent();
        if (!android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return "";
        String uri = intent.getData().toString();
        intent.setData(null);
        intent.setAction(android.content.Intent.ACTION_MAIN);
        return uri.length() > 512 ? "" : uri;
    }

    /** Android share sheet (live gameShareChooser / shareText). */
    @UsedByGodot
    public void shareText(String subject, String text, String chooserTitle) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain");
                if (subject != null && !subject.isEmpty()) send.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
                send.putExtra(android.content.Intent.EXTRA_TEXT, text);
                activity.startActivity(android.content.Intent.createChooser(send, chooserTitle));
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------ Google Play Billing 9.1.0

    private BillingBridge billing;

    private BillingBridge billing() {
        if (billing == null && getActivity() != null) {
            billing = new BillingBridge(getActivity(), (kind, json) -> emitSignal("billing", kind, json));
        }
        return billing;
    }

    @UsedByGodot public void billingConnect() { if (billing() != null) billing().connect(); }
    @UsedByGodot public void billingSetAccount(String id) { if (billing() != null) billing().setAccount(id); }
    @UsedByGodot public void billingQueryProducts(String idsJson) { if (billing() != null) billing().queryProducts(idsJson); }
    @UsedByGodot public void billingPurchase(String productId) { if (billing() != null) billing().purchase(productId); }
    @UsedByGodot public void billingQueryPurchases() { if (billing() != null) billing().queryPurchases(); }

    // Live PendingStorePurchaseStore: ludoproof_wallet "pending_play_purchases",
    // "v1." + urlsafe(iv) + "." + urlsafe(ct), alias ludoproof_pending_play_purchases_key_v1,
    // AAD "ludoproof:pending-play-purchases:v1". A legacy plaintext array is migrated.
    private static final String PENDING_PREFIX = "v1.";
    private static final byte[] PENDING_AAD = "ludoproof:pending-play-purchases:v1".getBytes(StandardCharsets.UTF_8);

    /** {"ok": true, "json": "[...]"} or {"ok": false} (unreadable data is kept, never dropped). */
    @UsedByGodot
    public synchronized String pendingPurchasesLoad() {
        try {
            SharedPreferences p = getActivity().getSharedPreferences("ludoproof_wallet", Context.MODE_PRIVATE);
            String raw = p.getString("pending_play_purchases", null);
            if (raw == null) return "{\"ok\":true,\"json\":\"[]\"}";
            String plain;
            if (raw.startsWith(PENDING_PREFIX)) {
                String payload = raw.substring(PENDING_PREFIX.length());
                int dot = payload.indexOf('.');
                byte[] iv = Base64.decode(payload.substring(0, dot), Base64.NO_WRAP | Base64.URL_SAFE);
                byte[] ct = Base64.decode(payload.substring(dot + 1), Base64.NO_WRAP | Base64.URL_SAFE);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, aliasKey("ludoproof_pending_play_purchases_key_v1"), new GCMParameterSpec(128, iv));
                cipher.updateAAD(PENDING_AAD);
                plain = new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            } else {
                plain = raw;
            }
            return new JSONObject().put("ok", true).put("json", plain).put("legacy", !raw.startsWith(PENDING_PREFIX)).toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    @UsedByGodot
    public synchronized boolean pendingPurchasesSave(String json) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aliasKey("ludoproof_pending_play_purchases_key_v1"));
            cipher.updateAAD(PENDING_AAD);
            byte[] ct = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            String encoded = PENDING_PREFIX + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP | Base64.URL_SAFE) + "."
                + Base64.encodeToString(ct, Base64.NO_WRAP | Base64.URL_SAFE);
            return getActivity().getSharedPreferences("ludoproof_wallet", Context.MODE_PRIVATE).edit()
                .putString("pending_play_purchases", encoded).commit();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ Google Mobile Ads + UMP

    private AdsBridge ads;

    private AdsBridge ads() {
        if (ads == null && getActivity() != null) ads = new AdsBridge(getActivity(), (kind, detail) -> emitSignal("ads", kind, detail));
        return ads;
    }

    @UsedByGodot public void adsPrepare(boolean showForm) { if (ads() != null) ads().prepare(showForm); }
    @UsedByGodot public boolean adsCanRequest() { return ads() != null && ads().canRequestAds(); }
    @UsedByGodot public void adsLoadRewarded(String unit) { if (ads() != null) ads().loadRewarded(unit); }
    @UsedByGodot public boolean adsRewardedReady() { return ads() != null && ads().rewardedReady(); }
    @UsedByGodot public void adsShowRewarded(String userId, String customData) { if (ads() != null) ads().showRewarded(userId, customData); }
    @UsedByGodot public void adsLoadInterstitial(String unit) { if (ads() != null) ads().loadInterstitial(unit); }
    @UsedByGodot public boolean adsInterstitialReady() { return ads() != null && ads().interstitialReady(); }
    @UsedByGodot public void adsShowInterstitial() { if (ads() != null) ads().showInterstitial(); }
    @UsedByGodot public void adsPrivacyOptions() { if (ads() != null) ads().privacyOptions(); }

    // ------------------------------------------------------------ Play Core (update / review / referrer)

    private PlayCoreBridge playCore;

    private PlayCoreBridge playCore() {
        if (playCore == null && getActivity() != null) playCore = new PlayCoreBridge(getActivity(), (kind, json) -> emitSignal("playcore", kind, json));
        return playCore;
    }

    @UsedByGodot public void updateCheck() { if (playCore() != null) playCore().checkUpdate(); }
    @UsedByGodot public boolean updateStart(boolean immediate) { return playCore() != null && playCore().startUpdate(immediate); }
    @UsedByGodot public void updateComplete() { if (playCore() != null) playCore().completeUpdate(); }
    @UsedByGodot public void reviewRequest() { if (playCore() != null) playCore().requestReview(); }
    @UsedByGodot public void installReferrerRead() { if (playCore() != null) playCore().readInstallReferrer(); }
    @UsedByGodot public void integrityTokenRequest(String project, String hash) { if (playCore() != null) playCore().integrityToken(project, hash); }
    @UsedByGodot public String appInstallInfo() { return playCore() != null ? playCore().installInfo() : "{}"; }
    @UsedByGodot public void openPlayStore() { if (playCore() != null) playCore().openStore(); }

    @Override
    public void onMainActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == PlayCoreBridge.UPDATE_REQUEST) emitSignal("playcore", "update_result", "{\"result\":" + resultCode + "}");
        super.onMainActivityResult(requestCode, resultCode, data);
    }
}
