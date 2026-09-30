# FingerPick Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app where up to 12 people put a finger on the screen, a 3‑2‑1 countdown runs, and N random fingers stay lit; distributed as a signed APK via GitHub Releases.

**Architecture:** Single-module Kotlin/Jetpack Compose app, one Activity, no navigation library. A pure-Kotlin `GameEngine` owns the rules and is unit-tested; a `PlayController` bridges it to Compose snapshot state; a single Canvas (`GlowCanvas`) renders all lights with pre-rendered glow sprites and additive blending. GitHub Actions builds/tests on every push and publishes a signed APK on `v*` tags.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12.01 (ui/foundation/animation, no Material), JUnit 4, GitHub Actions, JDK 17.

**Spec:** `docs/superpowers/specs/2026-09-30-fingerpick-design.md`

## Global Constraints

- Package / applicationId: `com.yvanbetremieux.fingerpick`.
- minSdk 26, compileSdk 35, targetSdk 35, JVM target 17.
- Players `P` in `[2, 12]`, default 4. Winners `W` in `[1, P - 1]`, default 1. Lowering `P` clamps `W`.
- Countdown: 3, 2, 1, one second each.
- Portrait locked, screen kept on, system bars hidden during play.
- UI strings in French.
- Release APK signed with one persistent key from repo secrets; keystore never committed.
- GitHub repo: `YvanBetremieux/FingerPick`, public. Commits use the global git identity (`yvan.betremieux@gmail.com`).
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Shell env for every Gradle command:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
  ```

## Review Focus

1. App backgrounded / screen locked / call arrives mid-round → no stuck "ghost" fingers on return; a Result stays visible. (Task 3: `cancelAll` tests; Task 4: ON_STOP hook.)
2. The last finger lifts exactly as the countdown expires (before a frame tick) → reveal wins, not cancel. (Task 3: `upAtExpiryRevealsInsteadOfCancelling`.)
3. A duplicate down event for an already-down pointer id → counted once. (Task 3: `duplicateDownIsCountedOnce`.)
4. An ignored (extra) finger lifting → no effect on the round. (Task 3: `liftingAnIgnoredFingerHasNoEffect`.)
5. Stored settings out of range (e.g. W ≥ P after an upgrade) → sanitized on load. (Task 2: `sanitizedClampsBothValues`.)

---

## File Structure

```
.gitignore
README.md
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar, gradle-wrapper.properties
gradlew, gradlew.bat
.github/workflows/android.yml
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{colors.xml,themes.xml}
app/src/main/res/font/unbounded.ttf
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/yvanbetremieux/fingerpick/
  MainActivity.kt              — activity + FingerPickApp root (Setup vs Play, immersive mode)
  game/GameSettings.kt         — P/W value object with clamping (pure)
  game/GameEngine.kt           — rules state machine (pure)
  data/SettingsStore.kt        — SharedPreferences persistence
  platform/Haptics.kt          — vibration patterns
  ui/Clock.kt                  — per-frame uptime clock State
  ui/theme/Palette.kt          — colors
  ui/theme/Type.kt             — font family + text styles
  ui/glow/GlowSprite.kt        — pre-rendered glow bitmap + drawGlow/drawOrb helpers
  ui/glow/AmbientBackground.kt — drifting glows for setup screen
  ui/components/GlowButton.kt  — pill button
  ui/play/PlayController.kt    — engine ↔ Compose state bridge + ghosts
  ui/play/GlowCanvas.kt        — renders fingers/countdown/result
  ui/play/PlayScreen.kt        — touch input, HUD, result buttons
  ui/setup/SetupScreen.kt      — title, steppers, play button
app/src/test/java/com/yvanbetremieux/fingerpick/game/
  GameSettingsTest.kt
  GameEngineTest.kt
```

---

### Task 1: Project scaffold that builds locally

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, gradle wrapper files, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/colors.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/java/com/yvanbetremieux/fingerpick/MainActivity.kt`, `local.properties` (untracked)

**Interfaces:**
- Produces: a buildable `:app` module; `MainActivity` (replaced in Task 5).

- [ ] **Step 1: Verify the toolchain**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
$JAVA_HOME/bin/java -version
ls $ANDROID_HOME/platforms $ANDROID_HOME/build-tools
```
Expected: Java 17; `android-35` and `35.0.0` listed. If missing: `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-35" "build-tools;35.0.0"`.

- [ ] **Step 2: Gradle wrapper (no global Gradle install)**

```bash
cd /Users/yvan.betremieux/dev/FingerPick
mkdir -p gradle/wrapper
curl -fsSL -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
curl -fsSL -o gradlew https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew
curl -fsSL -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew.bat
chmod +x gradlew
cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
```

- [ ] **Step 3: Build files**

`.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
.DS_Store
*.jks
*.keystore
captures/
.kotlin/
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-animation = { group = "androidx.compose.animation", name = "animation" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FingerPick"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Set by CI (or locally) to sign release builds with the persistent key.
val releaseKeystore: String? = System.getenv("FP_KEYSTORE")

android {
    namespace = "com.yvanbetremieux.fingerpick"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yvanbetremieux.fingerpick"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "0.0.0-dev"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FP_KEY_ALIAS")
                keyPassword = System.getenv("FP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    testImplementation(libs.junit)
}
```

`app/proguard-rules.pro`:
```
# Compose and AndroidX ship their own consumer rules.
```

- [ ] **Step 4: Manifest, resources, icon, placeholder activity**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:label="FingerPick"
        android:theme="@style/Theme.FingerPick">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ink">#FF05060A</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.FingerPick" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/ink</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ink" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (three glowing dots inside the 66dp safe zone):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- pink -->
    <path android:pathData="M28,60a16,16 0 1,0 32,0a16,16 0 1,0 -32,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="44" android:centerY="60" android:gradientRadius="16"
                android:startColor="#FFFF4FA3" android:endColor="#00FF4FA3" />
        </aapt:attr>
    </path>
    <path android:pathData="M40,60a4,4 0 1,0 8,0a4,4 0 1,0 -8,0" android:fillColor="#FFFFFFFF" />
    <!-- cyan -->
    <path android:pathData="M50,44a14,14 0 1,0 28,0a14,14 0 1,0 -28,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="64" android:centerY="44" android:gradientRadius="14"
                android:startColor="#FF2FE6FF" android:endColor="#002FE6FF" />
        </aapt:attr>
    </path>
    <path android:pathData="M60.5,44a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0" android:fillColor="#FFFFFFFF" />
    <!-- yellow -->
    <path android:pathData="M52,70a11,11 0 1,0 22,0a11,11 0 1,0 -22,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="63" android:centerY="70" android:gradientRadius="11"
                android:startColor="#FFFFD83D" android:endColor="#00FFD83D" />
        </aapt:attr>
    </path>
    <path android:pathData="M60,70a3,3 0 1,0 6,0a3,3 0 1,0 -6,0" android:fillColor="#FFFFFFFF" />
</vector>
```

`app/src/main/java/com/yvanbetremieux/fingerpick/MainActivity.kt` (placeholder):
```kotlin
package com.yvanbetremieux.fingerpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BasicText("FingerPick") }
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.
If a version in the catalog can't be resolved, bump to the nearest available stable and note it in the commit message.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "chore: scaffold Android project"
```

---

### Task 2: GameSettings (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/yvanbetremieux/fingerpick/game/GameSettings.kt`
- Test: `app/src/test/java/com/yvanbetremieux/fingerpick/game/GameSettingsTest.kt`

**Interfaces:**
- Produces: `const val MIN_PLAYERS = 2`, `const val MAX_PLAYERS = 12` (top-level in `GameSettings.kt`); `data class GameSettings(val players: Int = 4, val winners: Int = 1)` with `withPlayers(Int): GameSettings`, `withWinners(Int): GameSettings`, `companion fun sanitized(players: Int, winners: Int): GameSettings`.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.yvanbetremieux.fingerpick.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {
    @Test fun defaultsAreFourPlayersOneWinner() {
        assertEquals(GameSettings(4, 1), GameSettings())
    }

    @Test fun withPlayersClampsToRange() {
        assertEquals(2, GameSettings().withPlayers(1).players)
        assertEquals(12, GameSettings().withPlayers(13).players)
    }

    @Test fun loweringPlayersClampsWinners() {
        val s = GameSettings(6, 5).withPlayers(3)
        assertEquals(GameSettings(3, 2), s)
    }

    @Test fun withWinnersClampsBetweenOneAndPlayersMinusOne() {
        assertEquals(1, GameSettings(4, 2).withWinners(0).winners)
        assertEquals(3, GameSettings(4, 1).withWinners(4).winners)
    }

    @Test fun sanitizedClampsBothValues() {
        assertEquals(GameSettings(12, 11), GameSettings.sanitized(40, 40))
        assertEquals(GameSettings(2, 1), GameSettings.sanitized(-1, 9))
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests '*GameSettingsTest'`
Expected: FAIL (unresolved reference `GameSettings`).

- [ ] **Step 3: Implement**

```kotlin
package com.yvanbetremieux.fingerpick.game

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 12

/** Round configuration. Always valid: 2..12 players, 1..players-1 winners. */
data class GameSettings(val players: Int = 4, val winners: Int = 1) {
    fun withPlayers(value: Int): GameSettings {
        val p = value.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        return GameSettings(p, winners.coerceIn(1, p - 1))
    }

    fun withWinners(value: Int): GameSettings = copy(winners = value.coerceIn(1, players - 1))

    companion object {
        fun sanitized(players: Int, winners: Int): GameSettings =
            GameSettings().withPlayers(players).withWinners(winners)
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew testDebugUnitTest --tests '*GameSettingsTest'`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add GameSettings with clamping"
```

---

### Task 3: GameEngine (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/yvanbetremieux/fingerpick/game/GameEngine.kt`
- Test: `app/src/test/java/com/yvanbetremieux/fingerpick/game/GameEngineTest.kt`

**Interfaces:**
- Consumes: `MIN_PLAYERS`, `MAX_PLAYERS` from Task 2.
- Produces:
  ```kotlin
  const val PALETTE_SIZE = 12
  enum class Phase { Waiting, Countdown, Result }
  data class Finger(val id: Long, val x: Float, val y: Float, val colorIndex: Int, val downAt: Long)
  data class GameState(phase: Phase = Waiting, fingers: List<Finger> = emptyList(), countdown: Int = 0,
                       winnerIds: Set<Long> = emptySet(), phaseStartedAt: Long = 0L)
  class GameEngine(players: Int, winners: Int, random: Random = Random.Default, countdownMillis: Long = 3_000L) {
      val state: GameState
      fun fingerDown(id: Long, x: Float, y: Float, now: Long)
      fun fingerMove(id: Long, x: Float, y: Float, now: Long)
      fun fingerUp(id: Long, now: Long)
      fun tick(now: Long)
      fun reset(now: Long)      // new round; fingers currently down are ignored until lifted
      fun cancelAll(now: Long)  // app backgrounded: forget all pointers; keeps a Result on screen
  }
  ```
  `countdown` is 3/2/1 during Countdown, 0 otherwise. `winnerIds` non-empty only in Result. In Result, `fingers` holds all P tracked fingers frozen at their reveal position.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.yvanbetremieux.fingerpick.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun engine(players: Int = 3, winners: Int = 1, seed: Int = 42) =
        GameEngine(players, winners, Random(seed))

    private fun GameEngine.downAll(vararg ids: Long, now: Long = 0L) =
        ids.forEach { fingerDown(it, it * 10f, it * 20f, now) }

    private fun GameEngine.ids() = state.fingers.map { it.id }

    @Test fun startsEmptyAndWaiting() {
        val s = engine().state
        assertEquals(Phase.Waiting, s.phase)
        assertTrue(s.fingers.isEmpty())
        assertEquals(0, s.countdown)
    }

    @Test fun countdownStartsExactlyWhenAllPlayersAreDown() {
        val e = engine(players = 3)
        e.downAll(1, 2)
        assertEquals(Phase.Waiting, e.state.phase)
        e.fingerDown(3, 0f, 0f, now = 500)
        assertEquals(Phase.Countdown, e.state.phase)
        assertEquals(3, e.state.countdown)
        assertEquals(500L, e.state.phaseStartedAt)
    }

    @Test fun countdownGoesThreeTwoOneThenResult() {
        val e = engine(players = 2)
        e.downAll(1, 2, now = 0)
        e.tick(999); assertEquals(3, e.state.countdown)
        e.tick(1000); assertEquals(2, e.state.countdown)
        e.tick(2000); assertEquals(1, e.state.countdown)
        e.tick(2999); assertEquals(1, e.state.countdown)
        e.tick(3000)
        assertEquals(Phase.Result, e.state.phase)
        assertEquals(0, e.state.countdown)
    }

    @Test fun liftingDuringCountdownCancelsAndKeepsOthers() {
        val e = engine(players = 3)
        e.downAll(1, 2, 3)
        e.fingerUp(2, now = 1500)
        assertEquals(Phase.Waiting, e.state.phase)
        assertEquals(listOf(1L, 3L), e.ids())
        e.fingerDown(4, 0f, 0f, now = 1600)
        assertEquals(Phase.Countdown, e.state.phase)
        assertEquals(1600L, e.state.phaseStartedAt)
    }

    @Test fun movingDuringCountdownUpdatesPositionWithoutCancelling() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerMove(1, 111f, 222f, now = 100)
        assertEquals(Phase.Countdown, e.state.phase)
        val f = e.state.fingers.first { it.id == 1L }
        assertEquals(111f, f.x); assertEquals(222f, f.y)
    }

    @Test fun extraFingersAreIgnoredEvenAfterATrackedLift() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerDown(3, 0f, 0f, now = 10)       // extra during countdown
        assertEquals(listOf(1L, 2L), e.ids())
        e.fingerUp(1, now = 20)                  // cancel
        assertEquals(Phase.Waiting, e.state.phase)
        assertEquals(listOf(2L), e.ids())        // 3 still ignored
        e.fingerMove(3, 5f, 5f, now = 30)
        assertEquals(listOf(2L), e.ids())
        e.fingerDown(4, 0f, 0f, now = 40)
        assertEquals(listOf(2L, 4L), e.ids())
        assertEquals(Phase.Countdown, e.state.phase)
    }

    @Test fun liftingAnIgnoredFingerHasNoEffect() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerDown(3, 0f, 0f, now = 10)
        val before = e.state
        e.fingerUp(3, now = 20)
        assertEquals(before, e.state)
    }

    @Test fun duplicateDownIsCountedOnce() {
        val e = engine(players = 3)
        e.fingerDown(1, 0f, 0f, now = 0)
        e.fingerDown(1, 0f, 0f, now = 5)
        assertEquals(listOf(1L), e.ids())
    }

    @Test fun unknownUpIsANoop() {
        val e = engine()
        e.downAll(1)
        val before = e.state
        e.fingerUp(99, now = 10)
        assertEquals(before, e.state)
    }

    @Test fun upAtExpiryRevealsInsteadOfCancelling() {
        val e = engine(players = 2)
        e.downAll(1, 2, now = 0)
        e.fingerUp(1, now = 3000)                // no tick happened at 3000
        assertEquals(Phase.Result, e.state.phase)
        assertEquals(listOf(1L, 2L), e.ids())
    }

    @Test fun resultHasExactlyWinnersDistinctAmongTracked() {
        repeat(50) { seed ->
            val e = engine(players = 5, winners = 2, seed = seed)
            e.downAll(1, 2, 3, 4, 5)
            e.tick(3000)
            val w = e.state.winnerIds
            assertEquals(2, w.size)
            assertTrue(w.all { it in 1L..5L })
        }
    }

    @Test fun everyFingerCanWin() {
        val winners = (0 until 200).flatMap { seed ->
            val e = engine(players = 4, winners = 1, seed = seed)
            e.downAll(1, 2, 3, 4)
            e.tick(3000)
            e.state.winnerIds
        }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L), winners)
    }

    @Test fun resultFreezesFingersAndIgnoresInput() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        val frozen = e.state
        e.fingerMove(1, 999f, 999f, now = 3100)
        e.fingerUp(1, now = 3200)
        e.fingerUp(2, now = 3200)
        e.fingerDown(7, 0f, 0f, now = 3300)
        assertEquals(frozen, e.state)
    }

    @Test fun resetStartsNewRoundIgnoringFingersStillDown() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        e.fingerUp(2, now = 3100)                // 1 still resting on screen
        e.reset(now = 4000)
        assertEquals(Phase.Waiting, e.state.phase)
        assertTrue(e.state.fingers.isEmpty())
        assertTrue(e.state.winnerIds.isEmpty())
        e.fingerMove(1, 3f, 3f, now = 4100)
        assertTrue(e.state.fingers.isEmpty())    // 1 ignored until lifted
        e.fingerDown(5, 0f, 0f, now = 4200)
        assertEquals(listOf(5L), e.ids())
    }

    @Test fun colorsAreUniqueAmongSimultaneousFingers() {
        val e = engine(players = 12, winners = 1)
        (1L..11L).forEach { e.fingerDown(it, 0f, 0f, now = 0) }
        e.fingerUp(4, now = 1)
        e.fingerDown(20, 0f, 0f, now = 2)
        e.fingerDown(21, 0f, 0f, now = 3)
        val colors = e.state.fingers.map { it.colorIndex }
        assertEquals(12, colors.size)
        assertEquals(12, colors.toSet().size)
        assertTrue(colors.all { it in 0 until PALETTE_SIZE })
    }

    @Test fun cancelAllForgetsFingersOutsideResult() {
        val e = engine(players = 3)
        e.downAll(1, 2, 3)
        e.cancelAll(now = 100)
        assertEquals(Phase.Waiting, e.state.phase)
        assertTrue(e.state.fingers.isEmpty())
        e.fingerUp(1, now = 200)                 // stale up after cancel
        e.fingerDown(1, 0f, 0f, now = 300)       // same id comes back: tracked
        assertEquals(listOf(1L), e.ids())
    }

    @Test fun cancelAllKeepsResultVisible() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        val result = e.state
        e.cancelAll(now = 3100)
        assertEquals(result, e.state)
        e.reset(now = 3200)
        e.fingerDown(1, 0f, 0f, now = 3300)      // no longer considered "still down"
        assertEquals(listOf(1L), e.ids())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWinnersNotBelowPlayers() {
        GameEngine(3, 3)
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests '*GameEngineTest'`
Expected: FAIL (unresolved `GameEngine`).

- [ ] **Step 3: Implement**

```kotlin
package com.yvanbetremieux.fingerpick.game

import kotlin.random.Random

const val PALETTE_SIZE = MAX_PLAYERS

enum class Phase { Waiting, Countdown, Result }

data class Finger(val id: Long, val x: Float, val y: Float, val colorIndex: Int, val downAt: Long)

data class GameState(
    val phase: Phase = Phase.Waiting,
    val fingers: List<Finger> = emptyList(),
    /** 3, 2, 1 during [Phase.Countdown]; 0 otherwise. */
    val countdown: Int = 0,
    /** Non-empty only in [Phase.Result]. */
    val winnerIds: Set<Long> = emptySet(),
    val phaseStartedAt: Long = 0L,
)

/**
 * Rules of a round. Pure Kotlin: callers pass pointer events and a monotonic clock.
 *
 * Only the first [players] fingers are tracked; any other finger is ignored until it lifts.
 */
class GameEngine(
    private val players: Int,
    private val winners: Int,
    private val random: Random = Random.Default,
    private val countdownMillis: Long = 3_000L,
) {
    init {
        require(players in MIN_PLAYERS..MAX_PLAYERS) { "players must be in $MIN_PLAYERS..$MAX_PLAYERS" }
        require(winners in 1 until players) { "winners must be in 1..${players - 1}" }
    }

    private val tracked = LinkedHashMap<Long, Finger>()
    private val down = HashSet<Long>()
    private var phase = Phase.Waiting
    private var phaseStartedAt = 0L
    private var winnerIds: Set<Long> = emptySet()

    var state: GameState = GameState()
        private set

    fun fingerDown(id: Long, x: Float, y: Float, now: Long) {
        advance(now)
        if (!down.add(id)) return
        if (phase == Phase.Waiting && tracked.size < players) {
            val used = tracked.values.mapTo(HashSet()) { it.colorIndex }
            val color = (0 until PALETTE_SIZE).filter { it !in used }.random(random)
            tracked[id] = Finger(id, x, y, color, now)
            if (tracked.size == players) enter(Phase.Countdown, now)
        }
        publish(now)
    }

    fun fingerMove(id: Long, x: Float, y: Float, now: Long) {
        advance(now)
        if (phase != Phase.Result) {
            val f = tracked[id]
            if (f != null && (f.x != x || f.y != y)) tracked[id] = f.copy(x = x, y = y)
        }
        publish(now)
    }

    fun fingerUp(id: Long, now: Long) {
        advance(now)
        if (down.remove(id) && phase != Phase.Result && tracked.remove(id) != null && phase == Phase.Countdown) {
            enter(Phase.Waiting, now)
        }
        publish(now)
    }

    fun tick(now: Long) {
        advance(now)
        publish(now)
    }

    fun reset(now: Long) {
        tracked.clear()
        winnerIds = emptySet()
        enter(Phase.Waiting, now)
        publish(now)
    }

    fun cancelAll(now: Long) {
        down.clear()
        if (phase != Phase.Result) {
            tracked.clear()
            enter(Phase.Waiting, now)
        }
        publish(now)
    }

    private fun advance(now: Long) {
        if (phase == Phase.Countdown && now - phaseStartedAt >= countdownMillis) {
            winnerIds = tracked.keys.shuffled(random).take(winners).toSet()
            enter(Phase.Result, now)
        }
    }

    private fun enter(next: Phase, now: Long) {
        phase = next
        phaseStartedAt = now
    }

    private fun publish(now: Long) {
        val countdown = if (phase == Phase.Countdown) {
            val remaining = countdownMillis - (now - phaseStartedAt)
            ((remaining + 999) / 1000).toInt().coerceAtLeast(1)
        } else 0
        val next = GameState(
            phase = phase,
            fingers = tracked.values.toList(),
            countdown = countdown,
            winnerIds = if (phase == Phase.Result) winnerIds else emptySet(),
            phaseStartedAt = phaseStartedAt,
        )
        if (next != state) state = next
    }
}
```

Why extra fingers stay ignored: every pointer goes into `down`, only the first P into `tracked`. While a pointer stays in `down`, a repeated `fingerDown` returns early and `fingerMove` finds no tracked entry, so it can never become tracked until it lifts. `reset()` keeps `down` intact, which is exactly what makes fingers resting on the screen ignored in the new round.

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew testDebugUnitTest`
Expected: all GameEngineTest and GameSettingsTest pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add GameEngine round state machine"
```

---

### Task 4: Play screen — lights, countdown, reveal

**Before writing code in this task, invoke the `frontend-design:frontend-design` skill** and apply it to the visual choices below (palette, glow layers, motion curves, HUD typography). The code below is the baseline; the skill may refine values but must keep the interfaces.

**Files:**
- Create: `ui/theme/Palette.kt`, `ui/theme/Type.kt`, `res/font/unbounded.ttf`, `ui/Clock.kt`, `ui/glow/GlowSprite.kt`, `platform/Haptics.kt`, `ui/components/GlowButton.kt`, `ui/play/PlayController.kt`, `ui/play/GlowCanvas.kt`, `ui/play/PlayScreen.kt` (all under `app/src/main/java/com/yvanbetremieux/fingerpick/` except the font)
- Modify: `MainActivity.kt` (temporarily launch `PlayScreen(GameSettings(), …)` to verify)

**Interfaces:**
- Consumes: `GameEngine`, `GameState`, `Phase`, `Finger`, `GameSettings`.
- Produces:
  - `val NeonPalette: List<Color>` (12), `Ink`, `InkRaised`, `Mist`, `MistDim`
  - `val Unbounded: FontFamily`; `TitleStyle`, `CountdownStyle`, `NumberStyle`, `LabelStyle`, `CaptionStyle`, `CounterStyle`
  - `@Composable fun rememberUptimeClock(): State<Long>`
  - `fun createGlowSprite(size: Int = 256): ImageBitmap`; `fun DrawScope.drawGlow(sprite, tint: ColorFilter, center: Offset, diameter: Float, alpha: Float)`; `fun DrawScope.drawOrb(sprite, tint, color: Color, center, radius, alpha)`
  - `class Haptics(context: Context) { fun touch(); fun tick(); fun reveal() }`
  - `@Composable fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true)`
  - `@Composable fun PlayScreen(settings: GameSettings, haptics: Haptics, onExit: () -> Unit)`

- [ ] **Step 1: Font**

```bash
mkdir -p app/src/main/res/font
curl -fsSL -o app/src/main/res/font/unbounded.ttf "https://github.com/google/fonts/raw/main/ofl/unbounded/Unbounded%5Bwght%5D.ttf"
file app/src/main/res/font/unbounded.ttf
```
Expected: `TrueType Font data`. Also store the licence (res dirs reject .txt):
```bash
mkdir -p licenses && curl -fsSL -o licenses/Unbounded-OFL.txt https://github.com/google/fonts/raw/main/ofl/unbounded/OFL.txt
```

- [ ] **Step 2: Theme files**

`ui/theme/Palette.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.theme

import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF05060A)
val InkRaised = Color(0xFF10131D)
val Mist = Color(0xFFEDEFF7)
val MistDim = Color(0x99EDEFF7)

/** One hue every ~30°, saturated for additive glow on black. Index = Finger.colorIndex. */
val NeonPalette = listOf(
    Color(0xFFFF4545), // red
    Color(0xFFFF8A1F), // orange
    Color(0xFFFFD83D), // yellow
    Color(0xFFB6FF3B), // lime
    Color(0xFF3DFF6E), // green
    Color(0xFF2BFFC6), // mint
    Color(0xFF2FE6FF), // cyan
    Color(0xFF3DA9FF), // sky
    Color(0xFF5B6BFF), // blue
    Color(0xFF9B5BFF), // violet
    Color(0xFFE24BFF), // magenta
    Color(0xFFFF4FA3), // pink
)
```

`ui/theme/Type.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yvanbetremieux.fingerpick.R

@OptIn(ExperimentalTextApi::class)
private fun unbounded(weight: FontWeight) = Font(
    R.font.unbounded,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Unbounded = FontFamily(
    unbounded(FontWeight.Light),
    unbounded(FontWeight.Normal),
    unbounded(FontWeight.SemiBold),
    unbounded(FontWeight.Black),
)

val TitleStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 58.sp, lineHeight = 60.sp, letterSpacing = (-1).sp)
val CountdownStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 180.sp, color = Mist)
val NumberStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, color = Mist)
val LabelStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 3.sp, color = Mist)
val CaptionStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 1.sp, color = MistDim)
val CounterStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Light, fontSize = 22.sp, letterSpacing = 2.sp, color = Mist)
```

- [ ] **Step 3: Clock, glow sprite, haptics**

`ui/Clock.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis

/** SystemClock.uptimeMillis(), updated every frame. Read it only inside draw lambdas. */
@Composable
fun rememberUptimeClock(): State<Long> = produceState(SystemClock.uptimeMillis()) {
    while (true) withFrameMillis { value = SystemClock.uptimeMillis() }
}
```

`ui/glow/GlowSprite.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.glow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** White radial falloff, tinted at draw time. Drawing a bitmap is far cheaper than a gradient per frame. */
fun createGlowSprite(size: Int = 256): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val c = size / 2f
    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(c, c),
            radius = c,
            colors = listOf(Color.White, Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.09f), Color.Transparent),
            colorStops = listOf(0f, 0.16f, 0.48f, 1f),
        )
    }
    Canvas(bitmap).drawCircle(Offset(c, c), c, paint)
    return bitmap
}

fun tintFor(color: Color): ColorFilter = ColorFilter.tint(color, BlendMode.Modulate)

fun DrawScope.drawGlow(sprite: ImageBitmap, tint: ColorFilter, center: Offset, diameter: Float, alpha: Float) {
    val d = diameter.roundToInt()
    if (d <= 0 || alpha <= 0f) return
    drawImage(
        image = sprite,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset((center.x - d / 2f).roundToInt(), (center.y - d / 2f).roundToInt()),
        dstSize = IntSize(d, d),
        alpha = alpha.coerceIn(0f, 1f),
        colorFilter = tint,
        blendMode = BlendMode.Plus,
    )
}

/** A finger light: wide halo, dense glow, colored core, white-hot center. */
fun DrawScope.drawOrb(sprite: ImageBitmap, tint: ColorFilter, color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawGlow(sprite, tint, center, radius * 4.6f, alpha * 0.42f)
    drawGlow(sprite, tint, center, radius * 2.1f, alpha * 0.9f)
    drawCircle(color, radius * 0.55f, center, alpha = alpha * 0.55f, blendMode = BlendMode.Plus)
    drawCircle(Color.White, radius * 0.26f, center, alpha = alpha * 0.9f, blendMode = BlendMode.Plus)
}
```

`platform/Haptics.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun touch() = vibrate(VibrationEffect.createOneShot(12, 90))
    fun tick() = vibrate(VibrationEffect.createOneShot(28, 180))
    fun reveal() = vibrate(
        VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 180), intArrayOf(0, 255, 0, 255), -1),
    )

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(effect)
    }
}
```

- [ ] **Step 4: GlowButton**

`ui/components/GlowButton.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yvanbetremieux.fingerpick.ui.theme.InkRaised
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val fill = if (primary) Brush.horizontalGradient(listOf(NeonPalette[11], NeonPalette[9])) else SolidColor(InkRaised)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .height(64.dp)
            .then(if (primary) Modifier.shadow(28.dp, shape, ambientColor = NeonPalette[11], spotColor = NeonPalette[9]) else Modifier)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (primary) Color.White.copy(alpha = 0.28f) else Mist.copy(alpha = 0.14f), shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text.uppercase(), style = LabelStyle.copy(color = if (primary) Color.White else Mist))
    }
}
```

- [ ] **Step 5: PlayController**

`ui/play/PlayController.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yvanbetremieux.fingerpick.game.Finger
import com.yvanbetremieux.fingerpick.game.GameEngine
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.GameState
import com.yvanbetremieux.fingerpick.game.Phase

/** A light fading out after its finger lifted. */
data class Ghost(val finger: Finger, val liftedAt: Long)

const val GHOST_MILLIS = 320L

/** Bridges the pure [GameEngine] to Compose snapshot state. */
@Stable
class PlayController(val settings: GameSettings) {
    private val engine = GameEngine(settings.players, settings.winners)

    var state: GameState by mutableStateOf(engine.state)
        private set

    val ghosts = mutableStateListOf<Ghost>()

    /** Returns true when the finger became a tracked player (for haptics). */
    fun down(id: Long, x: Float, y: Float, now: Long): Boolean {
        val before = engine.state.fingers.size
        engine.fingerDown(id, x, y, now)
        sync(now)
        return engine.state.phase != Phase.Result && engine.state.fingers.size > before
    }

    fun move(id: Long, x: Float, y: Float, now: Long) { engine.fingerMove(id, x, y, now); sync(now) }
    fun up(id: Long, now: Long) { engine.fingerUp(id, now); sync(now) }
    fun tick(now: Long) { engine.tick(now); sync(now) }
    fun replay(now: Long) { engine.reset(now); sync(now) }
    fun cancelAll(now: Long) { engine.cancelAll(now); sync(now) }

    private fun sync(now: Long) {
        val next = engine.state
        if (next == state) return
        if (state.phase != Phase.Result && next.phase != Phase.Result) {
            for (f in state.fingers) if (next.fingers.none { it.id == f.id }) ghosts += Ghost(f, now)
        }
        ghosts.removeAll { now - it.liftedAt > GHOST_MILLIS }
        state = next
    }
}
```

- [ ] **Step 6: GlowCanvas**

`ui/play/GlowCanvas.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.play

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yvanbetremieux.fingerpick.game.Phase
import com.yvanbetremieux.fingerpick.ui.glow.createGlowSprite
import com.yvanbetremieux.fingerpick.ui.glow.drawGlow
import com.yvanbetremieux.fingerpick.ui.glow.drawOrb
import com.yvanbetremieux.fingerpick.ui.glow.tintFor
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import kotlin.math.sin

private const val SPAWN_MS = 380f
private const val LOSER_FADE_MS = 450f

/**
 * Draws every light. Reads [controller] state and [clock] only inside the draw lambda,
 * so finger moves and animation frames redraw without recomposing.
 */
@Composable
fun GlowCanvas(controller: PlayController, clock: State<Long>, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val sprite = createGlowSprite()
            val tints = NeonPalette.map(::tintFor)
            val r = 52.dp.toPx()
            val ring = Stroke(width = 2.5.dp.toPx())
            val tether = 1.5.dp.toPx()
            val vignette = Brush.radialGradient(
                listOf(Color(0xFF0C0F1A), Ink),
                center = center,
                radius = size.maxDimension * 0.75f,
            )
            onDrawBehind {
                drawRect(vignette)
                val s = controller.state
                val now = clock.value
                val t = (now - s.phaseStartedAt).toFloat()

                when (s.phase) {
                    Phase.Waiting, Phase.Countdown -> {
                        val counting = s.phase == Phase.Countdown
                        val beat = if (counting) (t % 1000f) / 1000f else 0f
                        if (counting && s.fingers.isNotEmpty()) {
                            var cx = 0f; var cy = 0f
                            for (f in s.fingers) { cx += f.x; cy += f.y }
                            val centroid = Offset(cx / s.fingers.size, cy / s.fingers.size)
                            val tetherAlpha = 0.10f + 0.25f * (t / 3000f).coerceIn(0f, 1f)
                            for (f in s.fingers) {
                                drawLine(NeonPalette[f.colorIndex], Offset(f.x, f.y), centroid, tether, alpha = tetherAlpha, blendMode = BlendMode.Plus)
                            }
                        }
                        for (f in s.fingers) {
                            val c = Offset(f.x, f.y)
                            val color = NeonPalette[f.colorIndex]
                            val spawn = easeOutBack((now - f.downAt) / SPAWN_MS)
                            val breathe = 1f + 0.05f * sin(now / 380f + f.id * 1.7f)
                            drawOrb(sprite, tints[f.colorIndex], color, c, r * spawn * breathe, 1f)
                            val ringScale = if (counting) 1.9f - 0.7f * easeOutCubic(beat) else 1.25f + 0.08f * sin(now / 300f + f.id)
                            val ringAlpha = if (counting) 0.35f + 0.6f * beat else 0.55f
                            drawCircle(color, r * ringScale * spawn, c, alpha = ringAlpha, style = ring, blendMode = BlendMode.Plus)
                        }
                    }
                    Phase.Result -> {
                        for (f in s.fingers) {
                            val c = Offset(f.x, f.y)
                            val color = NeonPalette[f.colorIndex]
                            val tint = tints[f.colorIndex]
                            if (f.id in s.winnerIds) {
                                drawGlow(sprite, tint, c, r * 16f * easeOutCubic(t / 900f), 0.22f)
                                for (k in 0..1) {
                                    val w = (t - k * 220f) / 1200f
                                    if (w in 0f..1f) {
                                        val e = easeOutCubic(w)
                                        val width = (10f - 9f * e).dp.toPx()
                                        drawCircle(color, r * (1f + e * 8f), c, alpha = (1f - w) * 0.85f, style = Stroke(width), blendMode = BlendMode.Plus)
                                    }
                                }
                                val bloom = easeOutBack(t / 650f)
                                val breathe = 1f + 0.06f * sin(now / 420f + f.id)
                                val radius = r * (1f + 0.35f * bloom) * breathe
                                drawOrb(sprite, tint, color, c, radius, 1f)
                                drawCircle(color, radius * 1.35f, c, alpha = 0.7f, style = ring, blendMode = BlendMode.Plus)
                            } else {
                                val fade = 1f - (t / LOSER_FADE_MS).coerceIn(0f, 1f)
                                if (fade > 0f) drawOrb(sprite, tint, color, c, r * (0.6f + 0.4f * fade), fade)
                            }
                        }
                    }
                }

                for (g in controller.ghosts) {
                    val fade = 1f - (now - g.liftedAt) / GHOST_MILLIS.toFloat()
                    if (fade <= 0f) continue
                    val f = g.finger
                    drawOrb(sprite, tints[f.colorIndex], NeonPalette[f.colorIndex], Offset(f.x, f.y), r * (1f + 0.3f * (1f - fade)), fade * 0.8f)
                }
            }
        },
    )
}

private fun easeOutCubic(v: Float): Float {
    val x = 1f - v.coerceIn(0f, 1f)
    return 1f - x * x * x
}

private fun easeOutBack(v: Float): Float {
    val x = v.coerceIn(0f, 1f) - 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
```

- [ ] **Step 7: PlayScreen**

`ui/play/PlayScreen.kt`:
```kotlin
package com.yvanbetremieux.fingerpick.ui.play

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.Phase
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.components.GlowButton
import com.yvanbetremieux.fingerpick.ui.rememberUptimeClock
import com.yvanbetremieux.fingerpick.ui.theme.CaptionStyle
import com.yvanbetremieux.fingerpick.ui.theme.CountdownStyle
import com.yvanbetremieux.fingerpick.ui.theme.CounterStyle
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.MistDim
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun PlayScreen(settings: GameSettings, haptics: Haptics, onExit: () -> Unit) {
    val controller = remember(settings) { PlayController(settings) }
    val clock = rememberUptimeClock()
    val phase by remember(controller) { derivedStateOf { controller.state.phase } }
    val count by remember(controller) { derivedStateOf { controller.state.fingers.size } }
    val countdown by remember(controller) { derivedStateOf { controller.state.countdown } }
    var buttonsVisible by remember(controller) { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.cancelAll(SystemClock.uptimeMillis())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, phase) {
        if (phase == Phase.Countdown) {
            while (true) withFrameMillis { controller.tick(SystemClock.uptimeMillis()) }
        }
    }
    LaunchedEffect(countdown) { if (countdown > 0) haptics.tick() }
    LaunchedEffect(controller, phase) {
        buttonsVisible = false
        if (phase == Phase.Result) {
            haptics.reveal()
            delay(900)
            buttonsVisible = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val now = SystemClock.uptimeMillis()
                        for (c in event.changes) {
                            val id = c.id.value
                            when {
                                c.changedToDownIgnoreConsumed() ->
                                    if (controller.down(id, c.position.x, c.position.y, now)) haptics.touch()
                                c.changedToUpIgnoreConsumed() -> controller.up(id, now)
                                c.pressed -> controller.move(id, c.position.x, c.position.y, now)
                            }
                        }
                    }
                }
            },
    ) {
        GlowCanvas(controller, clock)

        Column(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val headline = when (phase) {
                Phase.Result -> if (settings.winners == 1) "L'ÉLU" else "LES ÉLUS"
                else -> "$count / ${settings.players}"
            }
            BasicText(headline, style = CounterStyle)
            BasicText(if (settings.winners == 1) "1 à choisir" else "${settings.winners} à choisir", style = CaptionStyle)
        }

        AnimatedVisibility(
            visible = phase == Phase.Waiting && count == 0,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(200)),
        ) {
            BasicText(
                "POSEZ VOS DOIGTS",
                style = LabelStyle.copy(color = MistDim),
                modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * sin(clock.value / 500f) },
            )
        }

        if (phase == Phase.Countdown) {
            AnimatedContent(
                targetState = countdown,
                modifier = Modifier.align(Alignment.Center),
                transitionSpec = {
                    (scaleIn(tween(420), initialScale = 1.8f) + fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(300), targetScale = 0.6f) + fadeOut(tween(300)))
                },
                label = "countdown",
            ) { value ->
                BasicText("$value", style = CountdownStyle, modifier = Modifier.graphicsLayer { alpha = 0.92f })
            }
        }

        AnimatedVisibility(
            visible = buttonsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 2 },
            exit = fadeOut(tween(150)),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowButton("Réglages", onClick = onExit, modifier = Modifier.weight(1f), primary = false)
                GlowButton("Rejouer", onClick = { controller.replay(SystemClock.uptimeMillis()) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
```

- [ ] **Step 8: Temporarily wire MainActivity to PlayScreen and build**

```kotlin
package com.yvanbetremieux.fingerpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.play.PlayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val haptics = Haptics(this)
        setContent { PlayScreen(GameSettings(), haptics, onExit = ::finish) }
    }
}
```

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, no errors (warnings OK).

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: add play screen with glowing lights, countdown and reveal"
```

---

### Task 5: Setup screen, persistence, app shell

**Files:**
- Create: `data/SettingsStore.kt`, `ui/glow/AmbientBackground.kt`, `ui/setup/SetupScreen.kt`
- Modify: `MainActivity.kt` (final version)

**Interfaces:**
- Consumes: `GameSettings`, `MIN_PLAYERS`, `MAX_PLAYERS`, `PlayScreen`, `GlowButton`, `Haptics`, theme, glow helpers, `rememberUptimeClock`.
- Produces: `class SettingsStore(context) { fun load(): GameSettings; fun save(GameSettings) }`; `@Composable fun SetupScreen(settings, onChange: (GameSettings) -> Unit, onPlay: () -> Unit)`.

Continue applying the `frontend-design` direction chosen in Task 4.

- [ ] **Step 1: SettingsStore**

```kotlin
package com.yvanbetremieux.fingerpick.data

import android.content.Context
import com.yvanbetremieux.fingerpick.game.GameSettings

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): GameSettings {
        val d = GameSettings()
        return GameSettings.sanitized(prefs.getInt(KEY_PLAYERS, d.players), prefs.getInt(KEY_WINNERS, d.winners))
    }

    fun save(settings: GameSettings) {
        prefs.edit().putInt(KEY_PLAYERS, settings.players).putInt(KEY_WINNERS, settings.winners).apply()
    }

    private companion object {
        const val KEY_PLAYERS = "players"
        const val KEY_WINNERS = "winners"
    }
}
```

- [ ] **Step 2: AmbientBackground**

```kotlin
package com.yvanbetremieux.fingerpick.ui.glow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import kotlin.math.cos
import kotlin.math.sin

private val AMBIENT_COLORS = intArrayOf(11, 9, 6, 1, 3)

/** Slow drifting colored glows behind the setup screen. */
@Composable
fun AmbientBackground(clock: State<Long>, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val sprite = createGlowSprite()
            val tints = AMBIENT_COLORS.map { tintFor(NeonPalette[it]) }
            val d = size.minDimension * 1.2f
            onDrawBehind {
                drawRect(Ink)
                val t = clock.value / 1000f
                for (i in tints.indices) {
                    val x = size.width * (0.5f + 0.40f * sin(t * (0.07f + i * 0.013f) + i * 1.3f))
                    val y = size.height * (0.5f + 0.42f * cos(t * (0.05f + i * 0.011f) + i * 2.1f))
                    drawGlow(sprite, tints[i], Offset(x, y), d, 0.17f)
                }
            }
        },
    )
}
```

- [ ] **Step 3: SetupScreen**

```kotlin
package com.yvanbetremieux.fingerpick.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.MAX_PLAYERS
import com.yvanbetremieux.fingerpick.game.MIN_PLAYERS
import com.yvanbetremieux.fingerpick.ui.components.GlowButton
import com.yvanbetremieux.fingerpick.ui.glow.AmbientBackground
import com.yvanbetremieux.fingerpick.ui.rememberUptimeClock
import com.yvanbetremieux.fingerpick.ui.theme.CaptionStyle
import com.yvanbetremieux.fingerpick.ui.theme.InkRaised
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.MistDim
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import com.yvanbetremieux.fingerpick.ui.theme.NumberStyle
import com.yvanbetremieux.fingerpick.ui.theme.TitleStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SetupScreen(settings: GameSettings, onChange: (GameSettings) -> Unit, onPlay: () -> Unit) {
    val clock = rememberUptimeClock()
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(clock)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            BasicText(
                "FINGER\nPICK",
                style = TitleStyle.copy(brush = Brush.linearGradient(listOf(NeonPalette[11], NeonPalette[9], NeonPalette[6]))),
            )
            Spacer(Modifier.height(12.dp))
            BasicText("Posez vos doigts. Le hasard choisit.", style = CaptionStyle)
            Spacer(Modifier.weight(1f))
            StepperCard(
                label = "JOUEURS",
                value = settings.players,
                min = MIN_PLAYERS,
                max = MAX_PLAYERS,
                onValueChange = { onChange(settings.withPlayers(it)) },
                dotsTotal = MAX_PLAYERS,
                dotsLit = settings.players,
            )
            Spacer(Modifier.height(12.dp))
            StepperCard(
                label = "À CHOISIR",
                value = settings.winners,
                min = 1,
                max = settings.players - 1,
                onValueChange = { onChange(settings.withWinners(it)) },
                dotsTotal = settings.players,
                dotsLit = settings.winners,
            )
            Spacer(Modifier.height(24.dp))
            GlowButton("Jouer", onClick = onPlay, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    dotsTotal: Int,
    dotsLit: Int,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InkRaised.copy(alpha = 0.72f))
            .border(1.dp, Mist.copy(alpha = 0.08f), shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BasicText(label, style = LabelStyle.copy(color = MistDim, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("−", enabled = value > min) { onValueChange(value - 1) }
            AnimatedContent(
                targetState = value,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    } else {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }
                },
                label = label,
            ) { v ->
                BasicText("$v", style = NumberStyle.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            }
            RoundButton("+", enabled = value < max) { onValueChange(value + 1) }
        }
        Spacer(Modifier.height(10.dp))
        DotRow(total = dotsTotal, lit = dotsLit)
    }
}

@Composable
private fun RoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.25f }
            .clip(CircleShape)
            .border(1.dp, Mist.copy(alpha = 0.18f), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(symbol, style = NumberStyle.copy(fontSize = 28.sp))
    }
}

/** One dot per seat; lit dots take their palette color. */
@Composable
private fun DotRow(total: Int, lit: Int) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val step = size.width / MAX_PLAYERS
        val radius = 4.dp.toPx()
        for (i in 0 until total) {
            val c = Offset(step * (i + 0.5f), size.height / 2f)
            if (i < lit) {
                drawCircle(NeonPalette[i], radius * 2.2f, c, alpha = 0.25f)
                drawCircle(NeonPalette[i], radius, c)
            } else {
                drawCircle(Mist, radius, c, alpha = 0.12f)
            }
        }
    }
}
```

- [ ] **Step 4: Final MainActivity with immersive play**

```kotlin
package com.yvanbetremieux.fingerpick

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yvanbetremieux.fingerpick.data.SettingsStore
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.play.PlayScreen
import com.yvanbetremieux.fingerpick.ui.setup.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = SettingsStore(this)
        val haptics = Haptics(this)
        setContent {
            FingerPickApp(store, haptics, onImmersive = ::setImmersive)
        }
    }

    private fun setImmersive(immersive: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun FingerPickApp(store: SettingsStore, haptics: Haptics, onImmersive: (Boolean) -> Unit) {
    var settings by remember { mutableStateOf(store.load()) }
    var playing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playing) { onImmersive(playing) }
    Crossfade(targetState = playing, animationSpec = tween(350), label = "screen") { isPlaying ->
        if (isPlaying) {
            PlayScreen(settings, haptics, onExit = { playing = false })
        } else {
            SetupScreen(
                settings = settings,
                onChange = { settings = it; store.save(it) },
                onPlay = { playing = true },
            )
        }
    }
}
```

- [ ] **Step 5: Build and test**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add setup screen, settings persistence and app shell"
```

---

### Task 6: CI, signing, GitHub release

**Files:**
- Create: `.github/workflows/android.yml`, `README.md`
- Outside repo: `~/.config/fingerpick/release.jks`, `~/.config/fingerpick/keystore.env`

**Interfaces:**
- Consumes: `app/build.gradle.kts` env vars `FP_KEYSTORE`, `FP_KEYSTORE_PASSWORD`, `FP_KEY_ALIAS`, `FP_KEY_PASSWORD`, `GITHUB_RUN_NUMBER`, `GITHUB_REF_NAME`.
- Produces: repo `YvanBetremieux/FingerPick`, secrets `FP_KEYSTORE_BASE64`, `FP_KEYSTORE_PASSWORD`, `FP_KEY_ALIAS`, `FP_KEY_PASSWORD`, Release `v0.1.0` with `FingerPick-v0.1.0.apk`.

- [ ] **Step 1: Workflow**

`.github/workflows/android.yml` (personal repo, not papernest: GitHub-hosted runner is correct here):
```yaml
name: Android

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - name: Unit tests and debug build
        run: ./gradlew --no-daemon testDebugUnitTest assembleDebug

      - name: Build signed release APK
        if: startsWith(github.ref, 'refs/tags/v')
        env:
          FP_KEYSTORE_BASE64: ${{ secrets.FP_KEYSTORE_BASE64 }}
          FP_KEYSTORE_PASSWORD: ${{ secrets.FP_KEYSTORE_PASSWORD }}
          FP_KEY_ALIAS: ${{ secrets.FP_KEY_ALIAS }}
          FP_KEY_PASSWORD: ${{ secrets.FP_KEY_PASSWORD }}
        run: |
          if [ -z "$FP_KEYSTORE_BASE64" ]; then echo "::error::FP_KEYSTORE_BASE64 secret missing"; exit 1; fi
          echo "$FP_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"
          export FP_KEYSTORE="$RUNNER_TEMP/release.jks"
          ./gradlew --no-daemon assembleRelease
          cp app/build/outputs/apk/release/app-release.apk "FingerPick-${GITHUB_REF_NAME}.apk"

      - name: Publish GitHub Release
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v2
        with:
          files: FingerPick-${{ github.ref_name }}.apk
          generate_release_notes: true
```

- [ ] **Step 2: README**

```markdown
# FingerPick

Tout le monde pose un doigt sur l'écran, 3‑2‑1… et le hasard choisit.

## Installer sur Android

1. Sur le téléphone, ouvrir la dernière release : https://github.com/YvanBetremieux/FingerPick/releases/latest
2. Télécharger `FingerPick-vX.Y.Z.apk` et l'ouvrir.
3. La première fois, autoriser le navigateur à « installer des applis inconnues ».

Les nouvelles versions s'installent par-dessus l'ancienne.

## Développer

JDK 17 + Android SDK 35. `./gradlew testDebugUnitTest assembleDebug`.
Une release est publiée par la CI à chaque tag `v*`.

Police : Unbounded (SIL Open Font License, voir `licenses/`).
```

Commit:
```bash
git add -A && git commit -m "ci: build, test and publish signed APK on tags"
```

- [ ] **Step 3: Generate the persistent signing key (outside the repo)**

```bash
mkdir -p ~/.config/fingerpick && chmod 700 ~/.config/fingerpick
PASS=$(openssl rand -base64 24 | tr -d '/+=')
$JAVA_HOME/bin/keytool -genkeypair -v -keystore ~/.config/fingerpick/release.jks -storetype PKCS12 \
  -alias fingerpick -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Yvan Betremieux, O=FingerPick" -storepass "$PASS" -keypass "$PASS"
printf 'FP_KEYSTORE=%s\nFP_KEYSTORE_PASSWORD=%s\nFP_KEY_ALIAS=fingerpick\nFP_KEY_PASSWORD=%s\n' \
  ~/.config/fingerpick/release.jks "$PASS" "$PASS" > ~/.config/fingerpick/keystore.env
chmod 600 ~/.config/fingerpick/*
```
(PKCS12 uses one password for store and key.) Tell the user to back up `~/.config/fingerpick/` — losing it means future APKs can't update installed ones.

- [ ] **Step 4: Verify a signed release build locally**

```bash
set -a; source ~/.config/fingerpick/keystore.env; set +a
./gradlew assembleRelease
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | head -3
```
Expected: `BUILD SUCCESSFUL`; signer DN `CN=Yvan Betremieux, O=FingerPick` (not `CN=Android Debug`). This also proves R8 minification doesn't break the build.

- [ ] **Step 5: Create repo, secrets, push (personal account)**

```bash
gh auth switch --user YvanBetremieux
gh repo create YvanBetremieux/FingerPick --public --source . --remote origin --description "Finger chooser for Android — put your fingers down, fate picks." 
set -a; source ~/.config/fingerpick/keystore.env; set +a
base64 -i ~/.config/fingerpick/release.jks | gh secret set FP_KEYSTORE_BASE64 -R YvanBetremieux/FingerPick
gh secret set FP_KEYSTORE_PASSWORD -R YvanBetremieux/FingerPick --body "$FP_KEYSTORE_PASSWORD"
gh secret set FP_KEY_ALIAS -R YvanBetremieux/FingerPick --body "$FP_KEY_ALIAS"
gh secret set FP_KEY_PASSWORD -R YvanBetremieux/FingerPick --body "$FP_KEY_PASSWORD"
git push -u origin main
```

- [ ] **Step 6: Watch main CI**

```bash
gh run watch -R YvanBetremieux/FingerPick $(gh run list -R YvanBetremieux/FingerPick -L1 --json databaseId -q '.[0].databaseId') --exit-status
```
Expected: success. On failure: `gh run view --log-failed`, fix (systematic-debugging), push, re-watch.

- [ ] **Step 7: Tag release and watch**

```bash
git tag v0.1.0 && git push origin v0.1.0
# watch the tag run as in Step 6
gh release view v0.1.0 -R YvanBetremieux/FingerPick
```
Expected: release `v0.1.0` with asset `FingerPick-v0.1.0.apk`.

- [ ] **Step 8: Restore gh account**

```bash
gh auth switch --user Yvan-Betremieux
```

---

## Manual on-device checklist (owner)

- Setup: steppers bounded (2–12, 1–P−1); lowering players clamps "à choisir"; settings survive app restart.
- 1 finger lights instantly with its own color; lifting fades it out.
- With P fingers: 3‑2‑1 with vibration each second; lifting one cancels; extra fingers never light.
- Reveal: losers fade, winners bloom and stay after lifting; Rejouer starts a clean round; Réglages goes back.
- Home button mid-countdown then return: no stuck lights.
- Smooth with many fingers.
