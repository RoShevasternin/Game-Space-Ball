import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlinx-serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Ключі TikTok лежать у gradle.properties (комітиться — проєкт відновлюється з git без
// ручних кроків). local.properties, якщо там є ці ключі, має пріоритет — для локальних підмін.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(key: String): String =
    localProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: ""

val tiktokAppId     = secret("tiktok.app.id")
val tiktokAppSecret = secret("tiktok.app.secret")

android {
    namespace = "com.rostislav.spaceball"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rostislav.spaceball"
        minSdk = 24
        targetSdk = 37
        versionCode = 21
        versionName = "21.3.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TIKTOK_APP_ID", "\"$tiktokAppId\"")
        buildConfigField("String", "TIKTOK_APP_SECRET", "\"$tiktokAppSecret\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("main") {
            jniLibs { srcDir("libs") }
            res { srcDirs("src\\main\\res", "src\\main\\res\\launcher") }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

val natives: Configuration = configurations.create("natives") {
    isCanBeConsumed = false   // цю конфігурацію не публікуємо назовні
    isCanBeResolved = true    // але самі резолвимо, щоб дістати .so з jar-ів
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    val gdxVersion = "1.14.2"
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
    implementation("com.badlogicgames.gdx:gdx-box2d:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-x86_64")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("space.earlygrey:shapedrawer:2.6.0")
    implementation("com.airbnb.android:lottie:6.7.1")
    implementation("com.github.tommyettinger:textratypist:2.4.0")

    // Other ------------------------------------------------------------------------
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")

    // TikTok
    implementation("com.github.tiktok:tiktok-business-android-sdk:1.6.1")

    // Billing
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Install Referrer
    implementation("com.android.installreferrer:installreferrer:2.2")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:25.4.0")

    // Google Play Services v2
    implementation("com.google.android.gms:play-services-games-v2:22.0.0")

}

tasks.register("copyAndroidNatives") {
    description = "Розпаковує .so з natives-jar у libs/<abi>"
    doFirst {
        natives.files.forEach { jar ->
            val outputDir = file("libs/" + jar.nameWithoutExtension.substringAfterLast("natives-"))
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}
tasks.configureEach {
    if ("package" in name) {
        dependsOn("copyAndroidNatives")
    }
    // Release без ключів TikTok зібрався б мовчки і не рахував би встановлення з реклами
    if (name == "preReleaseBuild") {
        doFirst {
            if (tiktokAppId.isBlank() || tiktokAppSecret.isBlank()) {
                throw GradleException("У gradle.properties немає tiktok.app.id / tiktok.app.secret — див. docs/MONETIZATION.md")
            }
        }
    }
}