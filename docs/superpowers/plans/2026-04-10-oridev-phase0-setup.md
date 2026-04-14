# Ori:Dev Phase 0: Project Setup & CLAUDE.md

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a compilable multi-module Android Gradle project with all modules, CI/CD, linting, and CLAUDE.md so subsequent phases have a solid foundation.

**Architecture:** Multi-module Gradle project using Kotlin DSL and Version Catalog. Modules: app, core-common, core-ui, core-network, core-security, domain, data, feature-filemanager, feature-terminal, feature-connections, feature-transfers, feature-proxmox, feature-editor, feature-settings, wear. MVVM + Clean Architecture. Hilt for DI.

**Tech Stack:** Kotlin 2.1.x, Compose BOM 2025.04.x, Material 3, Hilt 2.54.x, Room 2.7.x, Gradle 8.12+, AGP 8.9.x, Min SDK 34, Target SDK 36.

---

## File Structure

```
OriDev/
├── CLAUDE.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── .gitignore
├── .github/
│   ├── workflows/
│   │   ├── pr-check.yml
│   │   ├── build.yml
│   │   └── release.yml
│   └── dependabot.yml
├── detekt.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/dev/ori/app/
│           └── OriDevApplication.kt
├── core/core-common/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/core/common/
│       └── .gitkeep
├── core/core-ui/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/core/ui/
│       └── .gitkeep
├── core/core-network/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/core/network/
│       └── .gitkeep
├── core/core-security/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/core/security/
│       └── .gitkeep
├── domain/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/domain/
│       └── .gitkeep
├── data/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/data/
│       └── .gitkeep
├── feature-filemanager/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/filemanager/
│       └── .gitkeep
├── feature-terminal/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/terminal/
│       └── .gitkeep
├── feature-connections/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/connections/
│       └── .gitkeep
├── feature-transfers/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/transfers/
│       └── .gitkeep
├── feature-proxmox/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/proxmox/
│       └── .gitkeep
├── feature-editor/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/editor/
│       └── .gitkeep
├── feature-settings/
│   ├── build.gradle.kts
│   └── src/main/kotlin/dev/ori/feature/settings/
│       └── .gitkeep
└── wear/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── kotlin/dev/ori/wear/
            └── .gitkeep
```

---

### Task 0.1: Initialize Git Repository and .gitignore

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Initialize git repo**

Run: `cd /root/OriDev && git init`
Expected: `Initialized empty Git repository`

- [ ] **Step 2: Create .gitignore**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Android Studio / IntelliJ
.idea/
*.iml
local.properties

# Signing
*.keystore
*.jks
!debug.keystore

# OS
.DS_Store
Thumbs.db

# Kotlin
*.class

# APK/AAB
*.apk
*.aab

# Logs
*.log

# Secrets
.env
secrets.properties
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: initialize git repository with .gitignore"
```

---

### Task 0.2: Create Version Catalog and Root Build Files

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`

- [ ] **Step 1: Set up Gradle Wrapper**

Run: `cd /root/OriDev && gradle wrapper --gradle-version 8.12`
Expected: `gradle/wrapper/` directory created with `gradle-wrapper.jar` and `gradle-wrapper.properties`

If `gradle` is not installed, download the wrapper manually:
```bash
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties << 'PROPS'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS
```

- [ ] **Step 2: Create Version Catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
# Kotlin & Compose
kotlin = "2.1.10"
agp = "8.9.1"
compose-bom = "2025.04.00"


# AndroidX
core-ktx = "1.16.0"
lifecycle = "2.9.0"
activity-compose = "1.10.1"
navigation-compose = "2.9.0"
window = "1.4.0"
room = "2.7.1"
work = "2.10.1"
datastore = "1.1.4"
biometric = "1.4.0"
security-crypto = "1.1.0-alpha07"
splashscreen = "1.2.0-alpha02"

# Hilt
hilt = "2.54"
hilt-navigation-compose = "1.2.0"

# Networking
sshj = "0.40.0"
commons-net = "3.11.1"
okhttp = "4.12.0"
moshi = "1.16.0"

# Terminal & Editor
# Note: Termux terminal-view (Apache 2.0) -- verify license before use
sora-editor = "0.24.2"

# Image Loading
coil = "3.1.0"

# Monetization
billing = "7.1.1"
play-services-ads = "23.6.0"

# Wear OS
wear-compose = "1.5.0"
play-services-wearable = "19.0.0"
horologist = "0.6.22"

# Testing
junit5 = "5.11.4"
mockk = "1.13.16"
turbine = "1.2.0"
truth = "1.4.4"
espresso = "3.6.1"
test-runner = "1.6.2"

# Code Quality
detekt = "1.23.8"

# KSP
ksp = "2.1.10-1.0.31"

[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

# Compose BOM
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

# AndroidX Core
core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation-compose" }
window = { module = "androidx.window:window", version.ref = "window" }
splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "splashscreen" }

# Room
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }

# WorkManager
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
work-testing = { module = "androidx.work:work-testing", version.ref = "work" }

# DataStore
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Security
biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }

# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-android-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hilt-navigation-compose" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hilt-navigation-compose" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }

# Networking
sshj = { module = "com.hierynomus:sshj", version.ref = "sshj" }
commons-net = { module = "commons-net:commons-net", version.ref = "commons-net" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
moshi = { module = "com.squareup.moshi:moshi", version.ref = "moshi" }
moshi-kotlin = { module = "com.squareup.moshi:moshi-kotlin", version.ref = "moshi" }
moshi-codegen = { module = "com.squareup.moshi:moshi-kotlin-codegen", version.ref = "moshi" }

# Image Loading
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }

# Monetization
billing-ktx = { module = "com.android.billingclient:billing-ktx", version.ref = "billing" }
play-services-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "play-services-ads" }

# Wear OS
wear-compose-material3 = { module = "androidx.wear.compose:compose-material3", version.ref = "wear-compose" }
wear-compose-foundation = { module = "androidx.wear.compose:compose-foundation", version.ref = "wear-compose" }
wear-compose-navigation = { module = "androidx.wear.compose:compose-navigation", version.ref = "wear-compose" }
play-services-wearable = { module = "com.google.android.gms:play-services-wearable", version.ref = "play-services-wearable" }
horologist-compose-layout = { module = "com.google.android.horologist:horologist-compose-layout", version.ref = "horologist" }

# Testing
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
junit5-params = { module = "org.junit.jupiter:junit-jupiter-params", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version = "1.10.1" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
test-runner = { module = "androidx.test:runner", version.ref = "test-runner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 3: Create gradle.properties**

```properties
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

# AndroidX
android.useAndroidX=true

# Kotlin
kotlin.code.style=official

# Non-transitive R classes
android.nonTransitiveRClass=true

# Compose
android.enableBuildConfig=true
```

- [ ] **Step 4: Create root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
    parallel = true
    source.setFrom(files(rootProject.subprojects.map { "${it.projectDir}/src" }))
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detekt.get()}")
}
```

- [ ] **Step 5: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OriDev"

include(":app")
include(":core:core-common")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-security")
include(":domain")
include(":data")
include(":feature-filemanager")
include(":feature-terminal")
include(":feature-connections")
include(":feature-transfers")
include(":feature-proxmox")
include(":feature-editor")
include(":feature-settings")
include(":wear")
```

- [ ] **Step 6: Commit**

```bash
git add gradle/ build.gradle.kts settings.gradle.kts gradle.properties
git commit -m "chore: add Gradle version catalog, root build files, and settings"
```

---

### Task 0.3: Create App Module

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/dev/ori/app/OriDevApplication.kt`

- [ ] **Step 1: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ori.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ori.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-security"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature-connections"))
    implementation(project(":feature-filemanager"))
    implementation(project(":feature-terminal"))
    implementation(project(":feature-transfers"))
    implementation(project(":feature-proxmox"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-settings"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.splashscreen)
    implementation(libs.window)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Create app/proguard-rules.pro**

```proguard
# SSHJ
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
```

- [ ] **Step 3: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".OriDevApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.OriDev">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 4: Create OriDevApplication.kt**

```kotlin
package dev.ori.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OriDevApplication : Application()
```

- [ ] **Step 5: Create minimal resources**

Create `app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Ori:Dev</string>
</resources>
```

Create `app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.OriDev" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

Create placeholder launcher icons (required for compilation):
```bash
mkdir -p app/src/main/res/mipmap-hdpi
# Create a minimal valid PNG (1x1 pixel) as placeholder
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > app/src/main/res/mipmap-hdpi/ic_launcher.png
```

- [ ] **Step 6: Create minimal MainActivity (placeholder)**

Create `app/src/main/kotlin/dev/ori/app/MainActivity.kt`:

```kotlin
package dev.ori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Ori:Dev")
                }
            }
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat: create app module with Hilt, Compose, and placeholder MainActivity"
```

---

### Task 0.4: Create All Library Modules (Empty Shells)

**Files:**
- Create: `core/core-common/build.gradle.kts`
- Create: `core/core-ui/build.gradle.kts`
- Create: `core/core-network/build.gradle.kts`
- Create: `core/core-security/build.gradle.kts`
- Create: `domain/build.gradle.kts`
- Create: `data/build.gradle.kts`
- Create: `feature-filemanager/build.gradle.kts`
- Create: `feature-terminal/build.gradle.kts`
- Create: `feature-connections/build.gradle.kts`
- Create: `feature-transfers/build.gradle.kts`
- Create: `feature-proxmox/build.gradle.kts`
- Create: `feature-editor/build.gradle.kts`
- Create: `feature-settings/build.gradle.kts`

- [ ] **Step 1: Create core-common module (pure Kotlin, no Android)**

Create `core/core-common/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

Create `core/core-common/src/main/kotlin/dev/ori/core/common/.gitkeep` (empty file).

- [ ] **Step 2: Create domain module (pure Kotlin, no Android)**

Create `domain/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:core-common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

Create `domain/src/main/kotlin/dev/ori/domain/.gitkeep`.

- [ ] **Step 3: Create Android library helper function**

To avoid repeating the same boilerplate for every Android library module, we define each one explicitly but keep them consistent.

Create `core/core-ui/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.ori.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:core-common"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.window)

    debugImplementation(libs.compose.ui.tooling)
}
```

Create `core/core-ui/src/main/kotlin/dev/ori/core/ui/.gitkeep`.

- [ ] **Step 4: Create core-network module**

Create `core/core-network/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ori.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":core:core-common"))

    implementation(libs.sshj)
    implementation(libs.commons.net)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Create `core/core-network/src/main/kotlin/dev/ori/core/network/.gitkeep`.

- [ ] **Step 5: Create core-security module**

Create `core/core-security/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ori.core.security"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":core:core-common"))

    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
}
```

Create `core/core-security/src/main/kotlin/dev/ori/core/security/.gitkeep`.

- [ ] **Step 6: Create data module**

Create `data/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.ori.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 34

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-security"))
    implementation(project(":domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
}
```

Create `data/src/main/kotlin/dev/ori/data/.gitkeep`.

- [ ] **Step 7: Create all feature modules**

Each feature module follows the same pattern. Create these 7 build files:

**feature-connections/build.gradle.kts:**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ori.feature.connections"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-ui"))
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.navigation.compose)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
}
```

Create the same file for each remaining feature module, changing only the namespace:

- `feature-filemanager/build.gradle.kts` -> namespace `dev.ori.feature.filemanager`
- `feature-terminal/build.gradle.kts` -> namespace `dev.ori.feature.terminal`
- `feature-transfers/build.gradle.kts` -> namespace `dev.ori.feature.transfers` (also add `implementation(libs.work.runtime.ktx)` and `implementation(libs.hilt.work)`)
- `feature-proxmox/build.gradle.kts` -> namespace `dev.ori.feature.proxmox` (also add `implementation(libs.okhttp)`, `implementation(libs.moshi)`, `implementation(libs.moshi.kotlin)`)
- `feature-editor/build.gradle.kts` -> namespace `dev.ori.feature.editor`
- `feature-settings/build.gradle.kts` -> namespace `dev.ori.feature.settings` (also add `implementation(libs.billing.ktx)`)

For each module, create the source directory with `.gitkeep`:
```bash
mkdir -p feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager
mkdir -p feature-terminal/src/main/kotlin/dev/ori/feature/terminal
mkdir -p feature-connections/src/main/kotlin/dev/ori/feature/connections
mkdir -p feature-transfers/src/main/kotlin/dev/ori/feature/transfers
mkdir -p feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox
mkdir -p feature-editor/src/main/kotlin/dev/ori/feature/editor
mkdir -p feature-settings/src/main/kotlin/dev/ori/feature/settings
touch feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/.gitkeep
touch feature-terminal/src/main/kotlin/dev/ori/feature/terminal/.gitkeep
touch feature-connections/src/main/kotlin/dev/ori/feature/connections/.gitkeep
touch feature-transfers/src/main/kotlin/dev/ori/feature/transfers/.gitkeep
touch feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/.gitkeep
touch feature-editor/src/main/kotlin/dev/ori/feature/editor/.gitkeep
touch feature-settings/src/main/kotlin/dev/ori/feature/settings/.gitkeep
```

- [ ] **Step 8: Commit**

```bash
git add core/ domain/ data/ feature-*
git commit -m "chore: create all library and feature module shells with build configurations"
```

---

### Task 0.5: Create Wear OS Module

**Files:**
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/kotlin/dev/ori/wear/.gitkeep`

- [ ] **Step 1: Create wear/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ori.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ori.wear"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-security"))
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.horologist.compose.layout)
    implementation(libs.play.services.wearable)
    implementation(libs.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
}
```

- [ ] **Step 2: Create wear/src/main/AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">
    </application>

</manifest>
```

Create `wear/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Ori:Dev Watch</string>
</resources>
```

Create placeholder icon:
```bash
mkdir -p wear/src/main/res/mipmap-hdpi
cp app/src/main/res/mipmap-hdpi/ic_launcher.png wear/src/main/res/mipmap-hdpi/ic_launcher.png
```

Create `wear/src/main/kotlin/dev/ori/wear/.gitkeep`.

- [ ] **Step 3: Commit**

```bash
git add wear/
git commit -m "chore: create Wear OS companion module shell"
```

---

### Task 0.6: Create CLAUDE.md

**Files:**
- Create: `CLAUDE.md`

- [ ] **Step 1: Create CLAUDE.md**

```markdown
# CLAUDE.md -- Ori:Dev

## Project

Ori:Dev (折り Dev) is an SCP/FTP/SSH File Manager & Terminal for Android Foldables (Pixel Fold).
Multi-module Android project: Kotlin, Jetpack Compose, Material 3, Hilt, Room, MVVM + Clean Architecture.

## Architecture

- **Layers:** Presentation (Compose + ViewModel) -> Domain (Use Cases) -> Data (Room + Network)
- **Modules:** app, core/{common,ui,network,security}, domain, data, feature-{connections,filemanager,terminal,transfers,proxmox,editor,settings}, wear
- **Rule:** Feature modules NEVER depend on each other. Communication via Navigation only.
- **Rule:** domain module has NO Android dependencies (pure Kotlin/coroutines).

## Conventions

### Kotlin
- Language: Kotlin only. No Java.
- Style: ktlint + detekt. Run `./gradlew detekt` before committing.
- Coroutines: Use structured concurrency. ViewModels use `viewModelScope`. No `GlobalScope`.
- Nullability: Prefer non-null types. Use `?.let {}` over `if (x != null)`.

### Compose
- State: ViewModels expose `StateFlow<UiState>`. No LiveData.
- Side effects: `LaunchedEffect`, `rememberCoroutineScope`. No lifecycle observers in Compose.
- Theme: Always use `MaterialTheme.colorScheme.*`. No hardcoded colors.
- Preview: Add `@Preview` for all screens and major components.

### Architecture
- ViewModels: One per screen. Use `@HiltViewModel`. Expose sealed `UiState` and `UiEvent`.
- Use Cases: Single-purpose. `operator fun invoke()`. Injectable via Hilt.
- Repositories: Interface in `domain/`, implementation in `data/`. Return `Flow<T>` or `Result<T>`.
- Errors: Sealed class `AppError`. Never throw exceptions across layer boundaries.

### Naming
- Packages: `dev.ori.{module}.{layer}` (e.g., `dev.ori.feature.connections.ui`)
- Screens: `{Name}Screen.kt` (e.g., `ConnectionListScreen.kt`)
- ViewModels: `{Name}ViewModel.kt`
- Use Cases: `{Verb}{Noun}UseCase.kt` (e.g., `ConnectUseCase.kt`)
- Repositories: `{Name}Repository.kt` (interface), `{Name}RepositoryImpl.kt` (implementation)
- Room: Entities in `data/model/`, DAOs in `data/dao/`, Database in `data/db/`

### Testing
- Framework: JUnit 5 + MockK + Turbine + Truth
- Naming: `methodUnderTest_condition_expectedResult`
- Pattern: Arrange / Act / Assert
- Room: In-memory database for DAO tests
- ViewModels: Test via Turbine `test {}` block on StateFlow
- Coverage: Every public function in domain/ and data/ must have tests

### Git
- Conventional Commits: feat:, fix:, chore:, docs:, test:, ci:, refactor:
- One logical change per commit
- Branch naming: feature/, fix/, chore/ prefix

### Security
- Credentials: Android Keystore only. Never store passwords in plaintext.
- Passwords in memory: char[] not String. Zero-fill after use.
- Clipboard: Set EXTRA_IS_SENSITIVE flag. Auto-clear after 30s.
- SSH Host Keys: Trust on First Use (TOFU). Reject on mismatch.

## Build

- Debug: `./gradlew assembleDebug`
- Test: `./gradlew test`
- Lint: `./gradlew detekt`
- Wear: `./gradlew :wear:assembleDebug`

## Key Libraries

- SSH/SFTP: SSHJ (com.hierynomus:sshj)
- FTP: Apache Commons Net
- Terminal: Termux terminal-view (Apache 2.0 -- verify license)
- Editor: Sora-Editor (io.github.Rosemoe.sora-editor)
- Proxmox API: OkHttp + Moshi
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: create CLAUDE.md with project conventions and architecture"
```

---

### Task 0.7: Create detekt Configuration

**Files:**
- Create: `detekt.yml`

- [ ] **Step 1: Create detekt.yml**

```yaml
build:
  maxIssues: 0

complexity:
  LongMethod:
    threshold: 60
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 12
  TooManyFunctions:
    thresholdInFiles: 25
    thresholdInClasses: 20
    thresholdInInterfaces: 15

formatting:
  MaximumLineLength:
    maxLineLength: 120
  NoWildcardImports:
    active: true
  TrailingCommaOnCallSite:
    active: true
  TrailingCommaOnDeclarationSite:
    active: true

naming:
  FunctionNaming:
    functionPattern: '[a-zA-Z][a-zA-Z0-9]*'
  TopLevelPropertyNaming:
    constantPattern: '[A-Z][A-Za-z0-9_]*'

style:
  MagicNumber:
    active: true
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: true
    ignoreAnnotation: true
    ignoreCompanionObjectPropertyDeclaration: true
  MaxLineLength:
    maxLineLength: 120
  ReturnCount:
    max: 3
  UnusedPrivateMember:
    active: true
  WildcardImport:
    active: true
```

- [ ] **Step 2: Commit**

```bash
git add detekt.yml
git commit -m "chore: add detekt configuration for static analysis"
```

---

### Task 0.8: Create CI/CD Workflows

**Files:**
- Create: `.github/workflows/pr-check.yml`
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Create .github/workflows/pr-check.yml**

```yaml
name: PR Check

on:
  pull_request:
    branches: [main, develop]

concurrency:
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew detekt
      - run: ./gradlew lint

  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew test
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-results
          path: '**/build/reports/tests/'

  ui-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          target: google_apis
          profile: pixel_6
          script: ./gradlew connectedCheck
```

- [ ] **Step 2: Create .github/workflows/build.yml**

```yaml
name: Build

on:
  push:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        variant: [debug, release]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode Keystore
        if: matrix.variant == 'release'
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release.keystore
      - run: ./gradlew assemble${{ matrix.variant == 'release' && 'Release' || 'Debug' }}
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      - uses: actions/upload-artifact@v4
        with:
          name: apk-${{ matrix.variant }}
          path: app/build/outputs/apk/${{ matrix.variant }}/*.apk
```

- [ ] **Step 3: Create .github/workflows/release.yml**

```yaml
name: Release

on:
  push:
    tags: ['v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode Keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release.keystore
      - run: ./gradlew bundleRelease assembleRelease
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      - name: Generate Changelog
        id: changelog
        run: |
          PREV_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")
          if [ -n "$PREV_TAG" ]; then
            {
              echo "changelog<<EOF"
              git log --pretty=format:"- %s" "$PREV_TAG..HEAD"
              echo ""
              echo "EOF"
            } >> "$GITHUB_OUTPUT"
          else
            echo "changelog=Initial release" >> "$GITHUB_OUTPUT"
          fi
      - uses: softprops/action-gh-release@v2
        with:
          body: ${{ steps.changelog.outputs.changelog }}
          files: |
            app/build/outputs/bundle/release/*.aab
            app/build/outputs/apk/release/*.apk
```

- [ ] **Step 4: Create .github/dependabot.yml**

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
      day: monday
    open-pull-requests-limit: 10
    labels:
      - dependencies
    groups:
      compose:
        patterns:
          - "androidx.compose*"
      kotlin:
        patterns:
          - "org.jetbrains.kotlin*"

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    labels:
      - ci
```

- [ ] **Step 5: Commit**

```bash
git add .github/
git commit -m "ci: add PR check, build, release workflows and Dependabot config"
```

---

### Task 0.9: Verify Full Project Compiles

- [ ] **Step 1: Run Gradle sync and assembleDebug**

Run: `cd /root/OriDev && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (all modules compile, no errors)

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL (no detekt violations)

- [ ] **Step 3: If build fails, fix issues**

Common issues:
- Missing AndroidManifest.xml in library modules: AGP generates one automatically for library modules, so this should not happen.
- Version catalog resolution: Verify `libs.versions.toml` syntax.
- Hilt processor: Ensure KSP is configured in all modules that use `@Inject`.

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: resolve build issues from project setup"
```

---

## Phase 0 Completion Checklist

After all tasks are done, verify:
- [ ] `git log --oneline` shows 6-8 clean conventional commits
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew detekt` succeeds
- [ ] `./gradlew :wear:assembleDebug` succeeds
- [ ] All 15 modules are listed in `settings.gradle.kts`
- [ ] `CLAUDE.md` exists in project root
- [ ] `.github/workflows/` contains 3 workflow files + dependabot.yml
