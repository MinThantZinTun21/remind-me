plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.remindme"
    compileSdk = 36

    defaultConfig {
        // The ID ends in ".alarm" on purpose: see docs/adr/0002.
        applicationId = "app.remindme.alarm"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        // The on-phone model runtime ships native code; the one phone this app is for is arm64.
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

}

kotlin { jvmToolchain(21) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    // The agent's model runtime. 0.18.1 is the version the LFM3-1.7B model file was built and tested for.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")

    testImplementation("junit:junit:4.13.2")

    // Runs the real model on the phone over a list of sentences: see AgentOnPhoneTest.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
