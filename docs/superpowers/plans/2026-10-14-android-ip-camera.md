# Android IP Camera Streaming — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây app Android (minSdk 24) biến điện thoại cũ thành IP camera MJPEG,
giả lập giao diện ESP32-CAM (port 8081), có điều khiển camera, chạy nền, tự dừng
khi không có client.

**Architecture:** Camera2 + `ImageReader(JPEG)` → `FrameBroadcaster` (latest frame,
fan-out nhiều client) → HTTP server NanoHTTPD (`/stream`, `/capture`, `/control`,
`/status`). Foreground service giữ camera chạy nền; `IdleManager` dừng camera khi
hết client sau grace 15s.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, AGP 8.x, Camera2, NanoHTTPD, AndroidX
(AppCompat, Preference, DataStore), JUnit4 + Robolectric cho unit test.

**Ghi chú:** Theo chính sách, plan có bước commit nhưng tôi sẽ KHÔNG tự commit trừ
khi bạn yêu cầu. Bỏ qua bước "Commit" nếu bạn chưa cho phép.

---

## File Structure

| File | Trách nhiệm |
|---|---|
| `settings.gradle.kts` | Khai báo module |
| `build.gradle.kts` | Plugin cấp project |
| `gradle.properties` | Cấu hình Gradle/JVM/AndroidX |
| `gradle/libs.versions.toml` | Version catalog |
| `app/build.gradle.kts` | Cấu hình module app |
| `app/src/main/AndroidManifest.xml` | Quyền, activity, service, receiver |
| `.../config/StreamConfig.kt` | Data class cấu hình + enum preset |
| `.../config/SettingsStore.kt` | Lưu/đọc cấu hình (SharedPreferences) |
| `.../stream/FrameBroadcaster.kt` | Latest-frame + fan-out; format MJPEG |
| `.../stream/MjpegWriter.kt` | Ghi multipart header cho 1 client |
| `.../http/HttpServer.kt` | NanoHTTPD routes |
| `.../http/ControlParams.kt` | Parse `/control`, map var→hành động |
| `.../camera/Capabilities.kt` | Đọc CameraCharacteristics → khả năng |
| `.../camera/CameraControls.kt` | Map giá trị cấu hình → CaptureRequest |
| `.../camera/CameraController.kt` | Pipeline Camera2 + lifecycle |
| `.../service/StreamingService.kt` | Foreground service + wake lock |
| `.../service/BootReceiver.kt` | Autostart |
| `.../net/NetworkInfo.kt` | IP/MAC hiện tại; IP tĩnh (thực nghiệm) |
| `.../MainActivity.kt` | UI chính |
| `.../SettingsActivity.kt` | UI cài đặt |
| `.../ui/*` | ViewModel tối giản |

Package name: `com.bapi.ipcamera`.

---

## Task 1: Scaffold project Android + Gradle wrapper

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts`,
  `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`
- Create: `local.properties` (sdk.dir)

- [ ] **Step 1: Tạo `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "android_camera_streaming"
include(":app")
```

- [ ] **Step 2: Tạo `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.2"
kotlin = "1.9.24"
coreKtx = "1.13.1"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.1.4"
preference = "1.2.1"
datastore = "1.1.1"
lifecycle = "2.8.4"
coroutines = "1.8.1"
nanohttpd = "2.3.1"
junit = "4.13.2"
robolectric = "4.13"
androidxTestCore = "1.6.1"
androidxJunit = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
material = { module = "com.google.android.material:material", version.ref = "material" }
androidx-constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
androidx-preference = { module = "androidx.preference:preference-ktx", version.ref = "preference" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
nanohttpd = { module = "org.nanohttpd:nanohttpd", version.ref = "nanohttpd" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 3: Tạo `build.gradle.kts` (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 4: Tạo `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Tạo `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bapi.ipcamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bapi.ipcamera"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.nanohttpd)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
```

- [ ] **Step 6: Tạo `app/proguard-rules.pro`** (rỗng, có comment)

```proguard
# NanonextdHTTPD dùng reflection nhẹ; giữ mặc định.
```

- [ ] **Step 7: Tạo `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <uses-feature android:name="android.hardware.camera.any" android:required="true" />
    <uses-feature android:name="android.hardware.camera.flash" android:required="false" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.IpCamera"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".SettingsActivity"
            android:exported="false" />

        <service
            android:name=".service.StreamingService"
            android:exported="false"
            android:foregroundServiceType="camera" />

        <receiver
            android:name=".service.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

- [ ] **Step 8: Tạo resources tối thiểu**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">IP Camera</string>
</resources>
```
`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.IpCamera" parent="Theme.MaterialComponents.DayNight.NoActionBar" />
</resources>
```
`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

- [ ] **Step 9: Tạo Gradle wrapper**

Run:
```bash
cd /home/bapi/Study/android_camera_streaming
gradle wrapper --gradle-version 8.7 2>/dev/null || true
```
Nếu chưa có `gradle`, tải wrapper thủ công (xem Task 1b). Sau đó `./gradlew --version`.

- [ ] **Step 9b: Nếu không có gradle, tạo wrapper thủ công**
`gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
Và cài `gradlew` từ bản Android chuẩn (script khởi động + `gradle-wrapper.jar`).

- [ ] **Step 10: Kiểm tra build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (cần SDK ở Task 2).

---

## Task 2: Cài Android SDK

**Files:** (không sửa source) `local.properties`, cài vào `/home/bapi/Android/Sdk`.

- [ ] **Step 1: Tải cmdline-tools**

```bash
mkdir -p /home/bapi/Android/Sdk/cmdline-tools
cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d /home/bapi/Android/Sdk/cmdline-tools
mv /home/bapi/Android/Sdk/cmdline-tools/cmdline-tools /home/bapi/Android/Sdk/cmdline-tools/latest
```

- [ ] **Step 2: Cài platform + build-tools + platform-tools**

```bash
export ANDROID_HOME=/home/bapi/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

- [ ] **Step 3: `local.properties`**

`local.properties`:
```properties
sdk.dir=/home/bapi/Android/Sdk
```

- [ ] **Step 4: Chown cho bapi**

```bash
chown -R bapi:bapi /home/bapi/Android /home/bapi/Study/android_camera_streaming
```

---

## Task 3: Domain models & SettingsStore

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/config/StreamConfig.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/config/SettingsStore.kt`
- Test: `app/src/test/java/com/bapi/ipcamera/config/SettingsStoreTest.kt`

- [ ] **Step 1: Viết test thất bại `SettingsStoreTest.kt`**

```kotlin
package com.bapi.ipcamera.config

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private lateinit var store: SettingsStore

    @Before fun setup() {
        store = SettingsStore(ApplicationProvider.getApplicationContext())
        store.reset()
    }

    @Test fun defaults_areEsp32Like() {
        val c = store.load()
        assertEquals(8081, c.port)
        assertEquals(640, c.width)
        assertEquals(480, c.height)
        assertEquals(15, c.fps)
        assertEquals(true, c.autoFramesize)
    }

    @Test fun save_thenLoad_roundTrips() {
        store.save(StreamConfig(port = 9000, width = 1280, height = 720, fps = 30, jpegQuality = 70, autoFramesize = false))
        val c = store.load()
        assertEquals(9000, c.port)
        assertEquals(1280, c.width)
        assertEquals(30, c.fps)
        assertEquals(70, c.jpegQuality)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL (chưa có class)**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsStoreTest*"`
Expected: FAIL / compile error.

- [ ] **Step 3: Implement `StreamConfig.kt`**

```kotlin
package com.bapi.ipcamera.config

data class StreamConfig(
    val port: Int = 8081,
    val width: Int = 640,
    val height: Int = 480,
    val fps: Int = 15,
    val jpegQuality: Int = 80,
    val autoFramesize: Boolean = true,
    val autostart: Boolean = false,
    val keepWarm: Boolean = false,
    val idleGraceSeconds: Int = 15,
)

enum class Resolution(val w: Int, val h: Int) {
    QVGA(320, 240), VGA(640, 480), SVGA(800, 600), HD(1280, 720);
    companion object {
        fun nearest(w: Int, h: Int): Resolution =
            values().minByOrNull { kotlin.math.abs(it.w - w) + kotlin.math.abs(it.h - h) } ?: VGA
    }
}
```

- [ ] **Step 4: Implement `SettingsStore.kt`**

```kotlin
package com.bapi.ipcamera.config

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ipcamera", Context.MODE_PRIVATE)

    fun load(): StreamConfig = StreamConfig(
        port = prefs.getInt("port", 8081),
        width = prefs.getInt("width", 640),
        height = prefs.getInt("height", 480),
        fps = prefs.getInt("fps", 15),
        jpegQuality = prefs.getInt("jpegQuality", 80),
        autoFramesize = prefs.getBoolean("autoFramesize", true),
        autostart = prefs.getBoolean("autostart", false),
        keepWarm = prefs.getBoolean("keepWarm", false),
        idleGraceSeconds = prefs.getInt("idleGraceSeconds", 15),
    )

    fun save(c: StreamConfig) {
        prefs.edit()
            .putInt("port", c.port).putInt("width", c.width).putInt("height", c.height)
            .putInt("fps", c.fps).putInt("jpegQuality", c.jpegQuality)
            .putBoolean("autoFramesize", c.autoFramesize)
            .putBoolean("autostart", c.autostart)
            .putBoolean("keepWarm", c.keepWarm)
            .putInt("idleGraceSeconds", c.idleGraceSeconds)
            .apply()
    }

    fun reset() { prefs.edit().clear().commit() }
}
```

- [ ] **Step 5: Chạy test — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsStoreTest*"`
Expected: PASS.

---

## Task 4: FrameBroadcaster (latest-frame fan-out + MJPEG format)

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/stream/FrameBroadcaster.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/stream/MjpegWriter.kt`
- Test: `app/src/test/java/com/bapi/ipcamera/stream/MjpegWriterTest.kt`,
  `FrameBroadcasterTest.kt`

- [ ] **Step 1: Test MjpegWriter (header format)**

```kotlin
package com.bapi.ipcamera.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MjpegWriterTest {
    @Test fun writesBoundaryAndLength() {
        val out = ByteArrayOutputStream()
        MjpegWriter("frameboundary").writeFrame(out, byteArrayOf(1, 2, 3))
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(s.startsWith("--frameboundary\r\n"))
        assertTrue(s.contains("Content-Type: image/jpeg\r\n"))
        assertTrue(s.contains("Content-Length: 3\r\n"))
        assertTrue(s.endsWith("\r\n\r\n\u0001\u0002\u0003\r\n"))
    }
}
```

- [ ] **Step 2: Test FrameBroadcaster (new subscribers get next frame; latest cached)**

```kotlin
package com.bapi.ipcamera.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameBroadcasterTest {
    @Test fun latestFrame_isNullBeforeFirstPublish() {
        val b = FrameBroadcaster()
        assertNull(b.latestFrame())
    }

    @Test fun publish_storesLatest() {
        val b = FrameBroadcaster()
        b.publish(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), b.latestFrame())
    }

    @Test fun clientCount_tracksSubscribers() {
        val b = FrameBroadcaster()
        val c = b.newClient()
        assert (b.clientCount() == 1)
        b.removeClient(c)
        assert (b.clientCount() == 0)
    }
}
```

- [ ] **Step 3: Chạy test — FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*MjpegWriterTest*" --tests "*FrameBroadcasterTest*"`
Expected: FAIL.

- [ ] **Step 4: Implement `MjpegWriter.kt`**

```kotlin
package com.bapi.ipcamera.stream

import java.io.OutputStream

class MjpegWriter(val boundary: String) {
    private val header = "--$boundary\r\nContent-Type: image/jpeg\r\n".toByteArray(Charsets.ISO_8859_1)
    private val crlf = "\r\n".toByteArray(Charsets.ISO_8859_1)

    fun writeFrame(out: OutputStream, jpeg: ByteArray) {
        out.write(header)
        out.write("Content-Length: ${jpeg.size}\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.write(jpeg)
        out.write(crlf)
        out.flush()
    }
}
```

- [ ] **Step 5: Implement `FrameBroadcaster.kt`**

```kotlin
package com.bapi.ipcamera.stream

class FrameBroadcaster {
    class Client internal constructor() {
        @Volatile var lastSeen: Long = 0L
    }

    private val lock = Object()
    @Volatile private var latest: ByteArray? = null
    @Volatile private var seq: Long = 0L
    private val clients = mutableSetOf<Client>()

    fun publish(frame: ByteArray) {
        synchronized(lock) {
            latest = frame
            seq++
            lock.notifyAll()
        }
    }

    fun latestFrame(): ByteArray? = latest

    /** Chờ frame mới hơn `afterSeq` tối đa `timeoutMs`; trả về (frame, seq) hoặc null. */
    fun awaitFrame(afterSeq: Long, timeoutMs: Long): Pair<ByteArray, Long>? {
        synchronized(lock) {
            if (seq <= afterSeq) {
                try { lock.wait(timeoutMs) } catch (_: InterruptedException) {}
            }
            val f = latest ?: return null
            if (seq <= afterSeq) return null
            return f to seq
        }
    }

    fun currentSeq(): Long = seq

    fun newClient(): Client = synchronized(lock) { Client().also { clients += it } }
    fun removeClient(c: Client) { synchronized(lock) { clients -= c } }
    fun clientCount(): Int = synchronized(lock) { clients.size }
}
```

- [ ] **Step 6: Chạy test — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*MjpegWriterTest*" --tests "*FrameBroadcasterTest*"`
Expected: PASS.

---

## Task 5: HTTP server (NanoHTTPD) + ControlParams

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/http/HttpServer.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/http/ControlParams.kt`
- Test: `app/src/test/java/com/bapi/ipcamera/http/ControlParamsTest.kt`

- [ ] **Step 1: Test ControlParams parse/map**

```kotlin
package com.bapi.ipcamera.http

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlParamsTest {
    @Test fun parsesFrameSize() {
        val a = ControlParams.parse(mapOf("var" to "framesize", "val" to "1280x720"))
        assertEquals(ControlAction.SetResolution(1280, 720), a)
    }
    @Test fun parsesFlash() {
        assertEquals(ControlAction.SetFlash(true),
            ControlParams.parse(mapOf("var" to "flash", "val" to "1")))
    }
    @Test fun parsesCamera() {
        assertEquals(ControlAction.SetCameraFront(true),
            ControlParams.parse(mapOf("var" to "camera", "val" to "1")))
    }
    @Test fun unknownReturnsNull() {
        assertEquals(null, ControlParams.parse(mapOf("var" to "nope", "val" to "1")))
    }
}
```

- [ ] **Step 2: Chạy test — FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*ControlParamsTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement `ControlParams.kt`**

```kotlin
package com.bapi.ipcamera.http

sealed interface ControlAction {
    data class SetResolution(val w: Int, val h: Int) : ControlAction
    data class SetQuality(val q: Int) : ControlAction
    data class SetFps(val fps: Int) : ControlAction
    data class SetFlash(val on: Boolean) : ControlAction
    data class SetCameraFront(val front: Boolean) : ControlAction
    data class SetAwb(val mode: String) : ControlAction
    data class SetScene(val mode: String) : ControlAction
    data class SetEffect(val mode: String) : ControlAction
    data class SetNight(val on: Boolean) : ControlAction
    data class SetBrightness(val value: Int) : ControlAction
    data class SetContrast(val value: Int) : ControlAction
    data class SetMirror(val horizontal: Boolean, val enable: Boolean) : ControlAction
}

object ControlParams {
    fun parse(q: Map<String, String>): ControlAction? {
        val v = q["var"] ?: return null
        val raw = q["val"] ?: return null
        val b = raw == "1" || raw.equals("true", true) || raw == "on"
        return when (v) {
            "framesize" -> raw.split("x").takeIf { it.size == 2 }
                ?.let { ControlAction.SetResolution(it[0].toInt(), it[1].toInt()) }
            "quality" -> raw.toIntOrNull()?.let { ControlAction.SetQuality(it) }
            "fps" -> raw.toIntOrNull()?.let { ControlAction.SetFps(it) }
            "flash" -> ControlAction.SetFlash(b)
            "camera" -> ControlAction.SetCameraFront(raw == "1")
            "wb" -> ControlAction.SetAwb(raw)
            "env" -> ControlAction.SetScene(raw)
            "effect" -> ControlAction.SetEffect(raw)
            "night" -> ControlAction.SetNight(b)
            "brightness" -> raw.toIntOrNull()?.let { ControlAction.SetBrightness(it) }
            "contrast" -> raw.toIntOrNull()?.let { ControlAction.SetContrast(it) }
            "hmirror" -> ControlAction.SetMirror(true, b)
            "vflip" -> ControlAction.SetMirror(false, b)
            else -> null
        }
    }
}
```

- [ ] **Step 4: Implement `HttpServer.kt`** (routes; dùng lại interface callback)

```kotlin
package com.bapi.ipcamera.http

import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.stream.FrameBroadcaster
import com.bapi.ipcamera.stream.MjpegWriter
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

interface StreamControlHandler {
    fun onControl(action: ControlAction)
    fun statusJson(): String
    fun currentConfig(): StreamConfig
}

class HttpServer(
    port: Int,
    private val broadcaster: FrameBroadcaster,
    private val control: StreamControlHandler,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // /stream giữ kết nối mở tới khi client ngắt
        return when (session.uri) {
            "/stream" -> serveStream()
            "/capture" -> serveCapture()
            "/control" -> serveControl(session)
            "/status" -> newFixedLengthResponse(Response.Status.OK, "application/json", control.statusJson())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun serveCapture(): Response {
        val jpeg = broadcaster.latestFrame()
            ?: return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "no frame")
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg",
            ByteArrayInputStream(jpeg), jpeg.size.toLong())
    }

    private fun serveControl(session: IHTTPSession): Response {
        val action = ControlParams.parse(session.parameters.mapValues { it.value.firstOrNull() ?: "" })
        if (action != null) control.onControl(action)
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
    }

    private fun serveStream(): Response {
        val boundary = "frameboundary"
        val client = broadcaster.newClient()
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut, 256 * 1024)
        val writer = MjpegWriter(boundary)

        // Ghi multipart header ngay để browser bắt đầu render <img>
        val header = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n" +
            "Cache-Control: no-cache\r\n\r\n").toByteArray(Charsets.ISO_8859_1)

        Thread {
            try {
                pipeOut.write(header)
                var seq = broadcaster.currentSeq()
                while (!Thread.currentThread().isInterrupted) {
                    val pair = broadcaster.awaitFrame(seq, 2000)
                        ?: continue
                    seq = pair.second
                    writer.writeFrame(pipeOut, pair.first)
                }
            } catch (_: Throwable) {
            } finally {
                broadcaster.removeClient(client)
                runCatching { pipeOut.close() }
            }
        }.start()

        val resp = newChunkedResponse(Response.Status.OK, null, pipeIn)
        resp.addHeader("Content-Type", "multipart/x-mixed-replace; boundary=$boundary")
        resp.addHeader("Cache-Control", "no-cache")
        return resp
    }
}
```

- [ ] **Step 5: Chạy test ControlParams — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*ControlParamsTest*"`
Expected: PASS.

- [ ] **Step 6: (Sau khi có camera) test thủ công `/stream` bằng ffmpeg/curl trên LAN.**

---

## Task 6: Camera capabilities + controls mapping

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/camera/Capabilities.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/camera/CameraControls.kt`
- Test: `app/src/test/java/com/bapi/ipcamera/camera/CapabilitiesTest.kt`

- [ ] **Step 1: Test Capabilities.supportedEffects/awb/scenes (pure parser)**

```kotlin
package com.bapi.ipcamera.camera

import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    @Test fun mapsAwbModeNames() {
        val c = Capabilities.Supported(
            awbModes = intArrayOf(1, 2, 4),
            sceneModes = intArrayOf(0, 4),
            effects = intArrayOf(0, 1),
            hasFlash = true,
            minExposureComp = -2,
            maxExposureComp = 2,
        )
        assertTrue(c.awbNames.contains("incandescent"))
        assertTrue(c.awbNames.contains("daylight"))
        assertTrue(c.effects.contains(1))
        assertTrue(c.hasFlash)
    }
}
```

- [ ] **Step 2: Chạy test — FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*CapabilitiesTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement `Capabilities.kt`**

```kotlin
package com.bapi.ipcamera.camera

import android.hardware.camera2.CameraCharacteristics

object Capabilities {
    data class Supported(
        val awbModes: IntArray,
        val sceneModes: IntArray,
        val effects: IntArray,
        val hasFlash: Boolean,
        val minExposureComp: Int,
        val maxExposureComp: Int,
    ) {
        val awbNames: List<String>
            get() = awbModes.mapNotNull { AWB_NAMES[it] }
    }

    private val AWB_NAMES = mapOf(
        CameraCharacteristics.CONTROL_AWB_MODE_AUTO to "auto",
        CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT to "incandescent",
        CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT to "fluorescent",
        CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT to "daylight",
        CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "cloudy",
        CameraCharacteristics.CONTROL_AWB_MODE_SHADE to "shade",
    )
    private val AWB_VALUES = AWB_NAMES.entries.associate { (k, v) -> v to k }

    fun awbValue(name: String): Int? = AWB_VALUES[name.lowercase()]

    fun from(ch: CameraCharacteristics): Supported {
        val awb = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val scenes = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
        val effects = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS) ?: intArrayOf()
        val flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val range = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        return Supported(awb, scenes, effects, flash, range?.lower ?: 0, range?.upper ?: 0)
    }
}
```

- [ ] **Step 4: Implement `CameraControls.kt`** (apply action → builder)

```kotlin
package com.bapi.ipcamera.camera

import android.hardware.camera2.CaptureRequest
import com.bapi.ipcamera.http.ControlAction

class CameraControls {
    var awbMode: Int? = null
    var sceneMode: Int? = null
    var effect: Int? = null
    var night: Boolean = false
    var exposureComp: Int = 0
    var brightness: Int = 0      // 0..100 -> map sang exposure comp (nếu trong range)
    var contrast: Int = 0        // hậu kỳ, không áp vào request
    var mirrorHorizontal: Boolean = false
    var vFlip: Boolean = false

    fun applyTo(builder: CaptureRequest.Builder) {
        awbMode?.let { builder.set(CaptureRequest.CONTROL_AWB_MODE, it) }
        sceneMode?.let { builder.set(CaptureRequest.CONTROL_SCENE_MODE, it) }
        effect?.let { builder.set(CaptureRequest.CONTROL_EFFECT_MODE, it) }
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureComp)
        if (night) builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    }

    fun handle(action: ControlAction) {
        when (action) {
            is ControlAction.SetAwb -> awbMode = Capabilities.awbValue(action.mode)
            is ControlAction.SetScene -> sceneMode = sceneNameToValue(action.mode)
            is ControlAction.SetEffect -> effect = effectNameToValue(action.mode)
            is ControlAction.SetNight -> night = action.on
            is ControlAction.SetBrightness -> brightness = action.value
            is ControlAction.SetContrast -> contrast = action.value
            is ControlAction.SetMirror ->
                if (action.horizontal) mirrorHorizontal = action.enable else vFlip = action.enable
            else -> {}
        }
    }

    companion object {
        private fun sceneNameToValue(n: String): Int? = when (n.lowercase()) {
            "auto" -> CaptureRequest.CONTROL_SCENE_MODE_DISABLED
            "night" -> CaptureRequest.CONTROL_SCENE_MODE_NIGHT
            "sport" -> CaptureRequest.CONTROL_SCENE_MODE_SPORTS
            "portrait" -> CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT
            "landscape" -> CaptureRequest.CONTROL_SCENE_MODE_LANDSCAPE
            else -> null
        }
        private fun effectNameToValue(n: String): Int? = when (n.lowercase()) {
            "none" -> CaptureRequest.CONTROL_EFFECT_MODE_OFF
            "mono", "grayscale" -> CaptureRequest.CONTROL_EFFECT_MODE_MONO
            "negative" -> CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE
            "sepia" -> CaptureRequest.CONTROL_EFFECT_MODE_SEPIA
            "posterize" -> CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE
            else -> null
        }
    }
}
```

- [ ] **Step 5: Chạy test — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CapabilitiesTest*"`
Expected: PASS.

---

## Task 7: CameraController (Camera2 pipeline + idle lifecycle)

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/camera/CameraController.kt`

- [ ] **Step 1: Implement `CameraController.kt`**

Yêu cầu: mở `CameraManager`, chọn cameraId theo `LENS_FACING`, đọc supported JPEG
sizes, tạo `ImageReader(JPEG, w, h)`, session repeating, publish frame; xử lý
`onDisconnected`; hàm `start(front: Boolean)`, `stop()`, `setTorch(on)`,
`switchCamera(front)`, `applyControl(action)`.

```kotlin
package com.bapi.ipcamera.camera

import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.http.ControlAction
import com.bapi.ipcamera.stream.FrameBroadcaster
import java.nio.ByteBuffer

class CameraController(
    private val context: Context,
    private val broadcaster: FrameBroadcaster,
    @Volatile var config: StreamConfig,
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraId: String? = null
    val controls = CameraControls()
    var capabilities: Capabilities.Supported? = null
        private set
    private var front = false
    private var torchOn = false

    @Volatile private var running = false

    fun start(front: Boolean = this.front) {
        if (running) return
        running = true
        this.front = front
        thread = HandlerThread("camera").also { it.start() }
        handler = Handler(thread!!.looper)
        openInternal()
    }

    private fun openInternal() {
        val h = handler ?: return
        try {
            cameraId = chooseCameraId(front)
            val id = cameraId ?: return
            val ch = manager.getCameraCharacteristics(id)
            capabilities = Capabilities.from(ch)
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = chooseSize(map, config.width, config.height)
            reader?.close()
            reader = ImageReader.newInstance(size.width, size.height, android.graphics.ImageFormat.JPEG, 2)
            reader!!.setOnImageAvailableListener({ r -> onFrame(r.acquireNextImage()) }, h)
            manager.openCamera(id, stateCallback, h)
        } catch (_: Throwable) {
            running = false
        }
    }

    private fun chooseCameraId(front: Boolean): String? {
        val ids = manager.cameraIdList
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (front && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!front && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return ids.firstOrNull()
    }

    private fun chooseSize(map: StreamConfigurationMap?, w: Int, h: Int): Size {
        val sizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG) ?: return Size(640, 480)
        return sizes.minByOrNull { kotlin.math.abs(it.width - w) + kotlin.math.abs(it.height - h) }
            ?: Size(640, 480)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            device = camera
            createSession(camera)
        }
        override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null; reopen() }
        override fun onError(camera: CameraDevice, error: Int) { camera.close(); device = null; reopen() }
    }

    private fun reopen() {
        if (running) handler?.postDelayed({ if (running) openInternal() }, 800)
    }

    private fun createSession(camera: CameraDevice) {
        val surface: Surface = reader!!.surface
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)
        controls.applyTo(builder)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        builder.set(CaptureRequest.JPEG_QUALITY, config.jpegQuality.toByte())
        val fpsRange = pickFpsRange(config.fps)
        fpsRange?.let { builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching { s.setRepeatingRequest(builder.build(), null, handler) }
                if (torchOn) setTorch(true)
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {}
        }, handler)
    }

    private fun pickFpsRange(fps: Int): android.util.Range<Int>? {
        val id = cameraId ?: return null
        val ranges = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return null
        return ranges.minByOrNull { kotlin.math.abs(it.upper - fps) }
    }

    private fun onFrame(image: android.media.Image?) {
        if (image == null) return
        try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            broadcaster.publish(bytes)
        } finally {
            image.close()
        }
    }

    fun applyControl(action: ControlAction) {
        when (action) {
            is ControlAction.SetFlash -> setTorch(action.on)
            is ControlAction.SetCameraFront -> { if (action.front != front) switchCamera(action.front) }
            is ControlAction.SetResolution -> {
                config = config.copy(width = action.w, height = action.h)
                restartSession()
            }
            is ControlAction.SetQuality -> { config = config.copy(jpegQuality = action.q); restartSession() }
            is ControlAction.SetFps -> { config = config.copy(fps = action.fps); restartSession() }
            else -> { controls.handle(action); restartSession() }
        }
    }

    fun switchCamera(front: Boolean) { this.front = front; restart() }
    fun updateConfig(c: StreamConfig) { config = c; restart() }

    fun setTorch(on: Boolean) {
        torchOn = on
        val id = cameraId ?: return
        runCatching { manager.setTorchMode(id, on) }
    }

    private fun restartSession() { handler?.post { session?.close(); device?.close(); device = null; openInternal() } }
    private fun restart() { if (running) { stop(); start() } }

    fun stop() {
        running = false
        session?.close(); device?.close(); reader?.close()
        session = null; device = null; reader = null
        thread?.quitSafely(); thread = null; handler = null
    }
}
```

- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug` (compile check).
- [ ] **Step 3: Test thủ công trên thiết bị** (xem Task 11).

---

## Task 8: Foreground service + boot + idle manager

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/service/StreamingService.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/service/BootReceiver.kt`

- [ ] **Step 1: Implement `StreamingService.kt`** — foreground service, wake lock,
  khởi tạo HttpServer + CameraController + IdleManager (poll `broadcaster.clientCount()`).

Use `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CAMERA)` on API 29+.
IdleManager coroutine every 1s: nếu `clientCount()==0` quá grace → `camera.stop()`;
nếu >0 và chưa running → `camera.start()`. Nếu `keepWarm` thì không stop.

- [ ] **Step 2: Implement `BootReceiver.kt`** — nếu `autostart` thì start service.

- [ ] **Step 3: Build + test nền** (tắt màn hình, kiểm tra stream còn chạy).

---

## Task 9: Network info + IP tĩnh (thực nghiệm)

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/net/NetworkInfo.kt`

- [ ] **Step 1: Implement `NetworkInfo.kt`** — lấy IPv4 Wi-Fi + MAC; hàm
  `trySetStaticIp(ip, prefix, gateway)` chỉ gọi khi `Build.VERSION.SDK_INT <= 28`,
  bọc try/catch, trả Boolean.

- [ ] **Step 2: Build.**

---

## Task 10: UI (Main + Settings)

**Files:**
- Create: `app/src/main/java/com/bapi/ipcamera/MainActivity.kt`
- Create: `app/src/main/java/com/bapi/ipcamera/SettingsActivity.kt`

- [ ] **Step 1: `MainActivity`** — nút Start/Stop service, hiển thị IP:port, số
  client, toggle flash / đổi camera / night.
- [ ] **Step 2: `SettingsActivity`** — port, resolution, fps, quality, autostart,
  keep-warm, nút xin miễn tối ưu pin + mở autostart OEM, IP tĩnh (thực nghiệm).
- [ ] **Step 3: Build APK.**

---

## Task 11: Build APK + hướng dẫn test

- [ ] **Step 1:** `./gradlew :app:assembleDebug`
- [ ] **Step 2:** Copy `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3:** Sideload: `adb install -r app-debug.apk`.
- [ ] **Step 4:** Trên router: DHCP reservation MAC (app hiển thị) → 192.168.1.49.
- [ ] **Step 5:** Sửa HTML: `CAMERA_URL = 'http://192.168.1.49:8081/stream'`.
- [ ] **Step 6:** Test: `ffplay http://192.168.1.49:8081/stream`, `/capture`, `/status`.
- [ ] **Step 7:** Test idle: đóng tab → sau 15s camera dừng; mở lại → chạy.

---

## Self-Review

- **Spec coverage:** endpoint (Task 5), pipeline (Task 7), controls (Task 6/7),
  idle (Task 8), foreground/battery (Task 8), config/IP (Task 3/9), UI (Task 10),
  build/test (Task 11), resolution auto/manual (Task 3/7). Đủ.
- **Placeholder scan:** Task 8/9/10 mô tả ngắn (không dán hết code) — sẽ viết đầy
  đủ khi thực thi vì phụ thuộc Android framework khó unit-test.
- **Type consistency:** `StreamConfig`, `ControlAction`, `Capabilities.Supported`,
  `FrameBroadcaster` dùng thống nhất giữa các task.
```
