import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val qaBuild = providers.gradleProperty("LUDOPAWS_QA_BUILD").orElse("false").get().toBooleanStrict()
val devBuild = providers.gradleProperty("LUDOPAWS_DEV_BUILD").orElse("false").get().toBooleanStrict()
val testAds = providers.gradleProperty("LUDOPAWS_TEST_ADS").orElse("false").get().toBooleanStrict()
require(!testAds || (!qaBuild && !devBuild)) { "Full test ads require the original-package debug build." }
require(!testAds || gradle.startParameter.taskNames.none { it.contains("release", ignoreCase = true) }) {
    "Test ads are debug-only. Release tasks require LUDOPAWS_TEST_ADS=false."
}
require(!(qaBuild && devBuild)) { "Choose either isolated QA or developer preview mode." }
require(!devBuild || gradle.startParameter.taskNames.none { it.contains("release", ignoreCase = true) }) {
    "Developer preview mode is debug-only; release tasks require LUDOPAWS_DEV_BUILD=false."
}
require(!qaBuild || gradle.startParameter.taskNames.none { it.contains("release", ignoreCase = true) }) {
    "QA isolation is for debug device tests only; release tasks require LUDOPAWS_QA_BUILD=false."
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
require(!qaBuild || !(ludoProofBillingEnabled || ludoProofAdsEnabled || ludoProofInterstitialAdsEnabled)) {
    "Isolated QA builds cannot enable purchases or ads. Use the signed internal Play build."
}
require(!devBuild || !(ludoProofBillingEnabled || ludoProofAdsEnabled || ludoProofInterstitialAdsEnabled)) {
    "The .dev package uses preview tools and demo ads only. Use integration debug for Play Billing."
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
if (testAds) {
    require(admobAppId == "ca-app-pub-3940256099942544~3347511713" &&
        admobRewardedAdUnitId == "ca-app-pub-3940256099942544/5224354917" &&
        admobInterstitialAdUnitId == "ca-app-pub-3940256099942544/1033173712") {
        "Debug test-ad mode must use only Google's demo app and ad unit IDs."
    }
}
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
        testAds || (admobAppId != "ca-app-pub-3940256099942544~3347511713" &&
            admobRewardedAdUnitId != "ca-app-pub-3940256099942544/5224354917"),
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
        testAds || (admobAppId != "ca-app-pub-3940256099942544~3347511713" &&
            admobInterstitialAdUnitId != "ca-app-pub-3940256099942544/1033173712"),
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
        versionCode = providers.gradleProperty("LUDOPAWS_VERSION_CODE").orElse("4").get().toInt().also { require(it >= 4) }
        versionName = providers.gradleProperty("LUDOPAWS_VERSION_NAME").orElse("1.0.0-rc4").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "LUDOPROOF_API_BASE_URL",
            "\"$escapedApiBaseUrl\"",
        )
        // All players retain family communications; reviewed builds can use Play commerce.
        buildConfigField("boolean", "LUDOPAWS_CONNECTED_FEATURES_ENABLED", "true")
        buildConfigField("boolean", "LUDOPAWS_FAMILY_MODE", "true")
        buildConfigField("boolean", "LUDOPAWS_QA_BUILD", qaBuild.toString())
        buildConfigField("boolean", "LUDOPAWS_TEST_ADS", testAds.toString())
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
            if (testAds) {
                resValue("string", "app_name", "LudoPaws Test")
                versionNameSuffix = "-test"
            }
            if (devBuild) {
                applicationIdSuffix = ".dev"
                versionNameSuffix = "-dev"
                resValue("string", "app_name", "LudoPaws Preview")
                manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            }
            if (qaBuild) {
                applicationIdSuffix = ".qa"
                versionNameSuffix = "-qa"
                buildConfigField("String", "LUDOPROOF_API_BASE_URL", "\"https://127.0.0.1\"")
            }
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

    if (qaBuild) sourceSets.getByName("debug").res.srcDir("src/qa/res")

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

// Aggregate tasks must not accidentally package a release with demo ad configuration.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (testAds) variant.enable = false
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncAuthoredAudioAssets)
}

dependencies {
    constraints {
        implementation("com.google.guava:guava:33.4.8-android") {
            because("Avoid vulnerable temporary-file implementations in transitive Ads dependencies")
        }
    }
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")
    implementation("com.google.android.play:age-signals:0.0.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// Resolve the exact shipped variant for release evidence; a source-only list is not an SBOM.
tasks.register("writeReleaseDependencyInventory") {
    val inventory = rootProject.layout.buildDirectory.file("reports/release-dependencies.json")
    outputs.file(inventory)
    inputs.property("versionName", android.defaultConfig.versionName.orEmpty())
    inputs.property("versionCode", android.defaultConfig.versionCode ?: 0)
    val runtime = configurations.named("releaseRuntimeClasspath")
    inputs.files(runtime)
    doLast {
        fun digest(file: java.io.File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        val configuration = runtime.get()
        val artifacts = configuration.resolvedConfiguration.resolvedArtifacts
        val components = artifacts.groupBy { it.moduleVersion.id }.map { (id, files) ->
            mapOf("group" to id.group, "name" to id.name, "version" to id.version,
                "artifacts" to files.map { mapOf("name" to it.file.name, "sha256" to digest(it.file)) })
        }.sortedBy { it["group"].toString() + ":" + it["name"] }
        fun reference(id: org.gradle.api.artifacts.ModuleVersionIdentifier) = "${id.group}:${id.name}:${id.version}"
        val graph = configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
            val id = component.moduleVersion ?: return@mapNotNull null
            mapOf("ref" to reference(id), "dependsOn" to component.dependencies.mapNotNull {
                (it as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.selected?.moduleVersion?.let(::reference)
            }.distinct().sorted())
        }
        val direct = configuration.incoming.resolutionResult.root.dependencies.mapNotNull {
            (it as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.selected?.moduleVersion?.let(::reference)
        }.distinct().sorted()
        val data = mapOf("schemaVersion" to 1, "configuration" to "releaseRuntimeClasspath",
            "versionName" to android.defaultConfig.versionName, "versionCode" to android.defaultConfig.versionCode,
            "components" to components, "dependencies" to graph, "directDependencies" to direct)
        inventory.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(data)) + "\n")
        }
    }
}
