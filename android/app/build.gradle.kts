plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ludoProofApiBaseUrl =
    providers.gradleProperty("LUDOPROOF_API_BASE_URL")
        .orElse("https://api.ludopaws.rekixo.com")
        .get()
val escapedApiBaseUrl =
    ludoProofApiBaseUrl
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
val ludoProofBillingEnabled =
    providers.gradleProperty("LUDOPROOF_BILLING_ENABLED")
        .orElse("false")
        .get()
        .toBooleanStrictOrNull()
        ?: false
val ludoProofAdsEnabled =
    providers.gradleProperty("LUDOPROOF_ADS_ENABLED")
        .orElse("false")
        .get()
        .toBooleanStrictOrNull()
        ?: false
val ludoProofInterstitialAdsEnabled =
    providers.gradleProperty("LUDOPROOF_INTERSTITIAL_ADS_ENABLED")
        .orElse("false")
        .get()
        .toBooleanStrictOrNull()
        ?: false
// Ads are off by default until the publisher explicitly approves the intended
// Play target audience, ad SDK treatment and independent consent controls.
// This opt-in is a release review gate, NOT age verification or legal certification.
val adSafetyReviewApproved =
    providers.gradleProperty("LUDOPAWS_AD_SAFETY_REVIEW_APPROVED")
        .orElse("false")
        .get()
        .toBooleanStrictOrNull()
        ?: false
require(!(ludoProofAdsEnabled || ludoProofInterstitialAdsEnabled) || adSafetyReviewApproved) {
    "Advertising cannot be enabled before explicit target-age, Families, privacy and consent review. " +
        "After approval set LUDOPAWS_AD_SAFETY_REVIEW_APPROVED=true."
}
// Until independently authorized connected-age access is available, an offline-only
// package must not initialize Billing or Ads regardless of unrelated opt-in flags.
require(!(ludoProofBillingEnabled || ludoProofAdsEnabled || ludoProofInterstitialAdsEnabled)) {
    "The age-safe offline-only release cannot enable purchases, rewarded ads or interstitials."
}
val admobAppId =
    providers.gradleProperty("ADMOB_APP_ID")
        .orElse("ca-app-pub-9987799703505828~1411336271")
        .get()
val admobRewardedAdUnitId =
    providers.gradleProperty("ADMOB_REWARDED_AD_UNIT_ID")
        .orElse("ca-app-pub-9987799703505828/9430436252")
        .get()
val admobInterstitialAdUnitId =
    providers.gradleProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID")
        .orElse("")
        .get()
require(admobAppId.isNotBlank()) {
    "ADMOB_APP_ID must not be blank."
}
require(admobRewardedAdUnitId.isNotBlank()) {
    "ADMOB_REWARDED_AD_UNIT_ID must not be blank."
}
if (ludoProofAdsEnabled) {
    require(Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$").matches(admobAppId)) {
        "ADMOB_APP_ID must be a valid production AdMob App ID."
    }
    require(Regex("^ca-app-pub-[0-9]{16}/[0-9]{10}$").matches(admobRewardedAdUnitId)) {
        "ADMOB_REWARDED_AD_UNIT_ID must be a valid production Ad Unit ID."
    }
    require(
        admobAppId != "ca-app-pub-3940256099942544~3347511713" &&
            admobRewardedAdUnitId != "ca-app-pub-3940256099942544/5224354917",
    ) {
        "Google sample AdMob IDs are not allowed when rewarded ads are enabled."
    }
}
if (ludoProofInterstitialAdsEnabled) {
    require(admobInterstitialAdUnitId.isNotBlank()) {
        "ADMOB_INTERSTITIAL_AD_UNIT_ID is required before enabling interstitial ads."
    }
    require(Regex("^ca-app-pub-[0-9]{16}/[0-9]{10}$").matches(admobInterstitialAdUnitId)) {
        "ADMOB_INTERSTITIAL_AD_UNIT_ID must be a valid AdMob interstitial unit ID."
    }
    require(
        admobAppId != "ca-app-pub-3940256099942544~3347511713" &&
            admobInterstitialAdUnitId != "ca-app-pub-3940256099942544/1033173712",
    ) {
        "Set your real AdMob app and interstitial IDs before enabling production interstitial ads."
    }
}

val googleSignInWebClientId =
    providers.gradleProperty("GOOGLE_SIGN_IN_WEB_CLIENT_ID")
        .orElse("")
        .get()
        .trim()
if (googleSignInWebClientId.isNotBlank()) {
    require(googleSignInWebClientId.endsWith(".apps.googleusercontent.com")) {
        "GOOGLE_SIGN_IN_WEB_CLIENT_ID must be a Google web OAuth client ID."
    }
}
val escapedGoogleSignInWebClientId =
    googleSignInWebClientId.replace("\\", "\\\\").replace("\"", "\\\"")
val playGamesProjectId =
    providers.gradleProperty("PLAY_GAMES_PROJECT_ID")
        .orElse("0")
        .get()
require(
    playGamesProjectId == "0" ||
        playGamesProjectId.toLongOrNull()?.let { it > 0 } == true,
) {
    "PLAY_GAMES_PROJECT_ID must be a positive numeric Play Games Services project ID."
}

val releaseStoreFile = providers.environmentVariable("LUDOPROOF_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("LUDOPROOF_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LUDOPROOF_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LUDOPROOF_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    )
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
val releaseSigningPartiallyConfigured =
    releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured

if (releaseSigningPartiallyConfigured) {
    throw org.gradle.api.GradleException(
        "Release signing is partially configured. Set all LUDOPROOF_RELEASE_* variables or none of them.",
    )
}


val authoredAudioDir = rootProject.projectDir.parentFile.resolve("audio")
val generatedAudioAssetsDir = layout.buildDirectory.dir("generated/audio-assets")
val syncAuthoredAudioAssets =
    tasks.register<Sync>("syncAuthoredAudioAssets") {
        from(authoredAudioDir) {
            include("**/*.wav", "**/*.ogg", "**/*.mp3")
        }
        into(generatedAudioAssetsDir.map { it.dir("audio") })
    }

android {
    namespace = "com.rekixo.ludopaws"

    androidResources {
        noCompress += listOf("wav", "ogg", "mp3")
    }
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rekixo.ludopaws"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0-rc2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "LUDOPROOF_API_BASE_URL",
            "\"$escapedApiBaseUrl\"",
        )
        // Connected gameplay is disabled in the age-safety release until server-side
        // eligibility, target-audience and regional protections are approved.
        // Do not expose a build-flag override as if it were age verification.
        buildConfigField("boolean", "LUDOPAWS_CONNECTED_FEATURES_ENABLED", "false")
        buildConfigField(
            "boolean",
            "LUDOPROOF_BILLING_ENABLED",
            ludoProofBillingEnabled.toString(),
        )
        buildConfigField(
            "boolean",
            "LUDOPROOF_ADS_ENABLED",
            ludoProofAdsEnabled.toString(),
        )
        buildConfigField(
            "boolean",
            "LUDOPROOF_INTERSTITIAL_ADS_ENABLED",
            ludoProofInterstitialAdsEnabled.toString(),
        )
        buildConfigField(
            "String",
            "ADMOB_REWARDED_AD_UNIT_ID",
            "\"${admobRewardedAdUnitId.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_AD_UNIT_ID",
            admobInterstitialAdUnitId.let { "\"$it\"" },
        )
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        buildConfigField(
            "String",
            "GOOGLE_SIGN_IN_WEB_CLIENT_ID",
            "\"$escapedGoogleSignInWebClientId\"",
        )
        buildConfigField(
            "String",
            "PLAY_GAMES_PROJECT_ID",
            "\"$playGamesProjectId\"",
        )
        resValue(
            "string",
            "game_services_project_id",
            playGamesProjectId,
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt",
                ),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

android {
    sourceSets {
        getByName("main") {
            assets.srcDir(generatedAudioAssetsDir)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncAuthoredAudioAssets)
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:144.7559.15")
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
