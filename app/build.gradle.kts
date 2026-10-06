plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.grogu.yt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.grogu.yt"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Shown in logcat at start-up so you can see which extractor build is inside the APK.
        buildConfigField("String", "EXTRACTOR_VERSION",
            "\"" + (providers.gradleProperty("extractorSnapshot").orNull?.let { "$it-SNAPSHOT" }
                ?: libs.versions.newpipe.extractor.get()) + "\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        // NewPipeExtractor needs java.nio / java.time APIs that Android < 13 lacks.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Debug-signed so `assembleRelease` installs straight away. Swap in your own signingConfig to publish.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":lame"))
    coreLibraryDesugaring(libs.desugar.nio)

    // Extractor: pinned in gradle/libs.versions.toml, or a commit snapshot via -PextractorSnapshot=<hash>
    val snapshot = providers.gradleProperty("extractorSnapshot").orNull
    if (snapshot != null) implementation("net.newpipe:extractor:$snapshot-SNAPSHOT")
    else implementation(libs.newpipe.extractor)
}
