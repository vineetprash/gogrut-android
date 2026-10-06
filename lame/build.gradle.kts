// Vendored pure-Java LAME (https://github.com/nwaldispuehl/java-lame, LGPL-2.1) – the MP3 encoder.
// Android has no MP3 encoder in MediaCodec, and this needs no NDK. Never changes, so it is vendored.
plugins { alias(libs.plugins.android.library) }

android {
    namespace = "net.sourceforge.lame"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
