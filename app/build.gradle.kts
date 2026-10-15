import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    providers.environmentVariable(environmentName).orNull?.takeIf(String::isNotBlank)
        ?: signingProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

val signingStorePath = signingValue("storeFile", "IP_CAMERA_KEYSTORE_PATH")
val signingStorePassword = signingValue("storePassword", "IP_CAMERA_KEYSTORE_PASSWORD")
val signingKeyAlias = signingValue("keyAlias", "IP_CAMERA_KEY_ALIAS")
val signingKeyPassword = signingValue("keyPassword", "IP_CAMERA_KEY_PASSWORD")
val signingValues = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
)
val hasSigningConfiguration = signingValues.any { it != null }

if (hasSigningConfiguration && signingValues.any { it == null }) {
    throw GradleException(
        "Incomplete signing configuration. Set all signing values in keystore.properties " +
            "or the IP_CAMERA_KEYSTORE_PATH, IP_CAMERA_KEYSTORE_PASSWORD, " +
            "IP_CAMERA_KEY_ALIAS, and IP_CAMERA_KEY_PASSWORD environment variables."
    )
}

val signingStoreFile = signingStorePath?.let { rootProject.file(it) }
if (hasSigningConfiguration && signingStoreFile?.isFile != true) {
    throw GradleException("Configured signing keystore does not exist: $signingStoreFile")
}

android {
    namespace = "com.bapi.ipcamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bapi.ipcamera"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasSigningConfiguration) {
        signingConfigs {
            create("shared") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningConfiguration) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasSigningConfiguration) {
                signingConfig = signingConfigs.getByName("shared")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.nanohttpd)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
