import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Read API key from ../.env (or from environment variables in CI) ----
val dotEnv = Properties().apply {
    val f = rootProject.file(".env")
    if (f.exists()) {
        f.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
                val k = line.substringBefore("=").trim()
                val v = line.substringAfter("=").trim().trim('"', '\'')
                setProperty(k, v)
            }
        }
    }
}

fun envOrDotEnv(name: String, default: String = ""): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: dotEnv.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: default

fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ---- Signing: keystore/keystore.properties (committed) or env vars (CI secrets) ----
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signProp(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)

val signStoreFile: File? = signProp("KEYSTORE_PATH", "storeFile")?.let { p ->
    val abs = File(p)
    if (abs.isAbsolute) abs else rootProject.file("keystore/$p")
}?.takeIf { it.exists() }
val signStorePassword = signProp("KEYSTORE_PASSWORD", "storePassword")
val signKeyAlias = signProp("KEY_ALIAS", "keyAlias")
val signKeyPassword = signProp("KEY_PASSWORD", "keyPassword")
val canSign = signStoreFile != null && signStorePassword != null &&
    signKeyAlias != null && signKeyPassword != null

android {
    namespace = "com.variza.smsforwarder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.variza.smsforwarder"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "2.0.0"

        buildConfigField("String", "API_KEY", quote(envOrDotEnv("API_KEY")))
        buildConfigField("String", "API_KEY_HEADER", quote(envOrDotEnv("API_KEY_HEADER", "X-API-Key")))
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = signStoreFile
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (canSign) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
