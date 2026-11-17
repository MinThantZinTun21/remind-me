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

    testImplementation("junit:junit:4.13.2")
}
