import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
    }

fun prop(
    name: String,
    env: String,
): String = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(name, "")

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
val appVersionName = (findProperty("versionName") as String?) ?: "0.1.$appVersionCode"

android {
    namespace = "cz.crabhi.dragons.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.crabhi.dragons"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Optional defaults for a personal build. Never committed: they come from local.properties.
        // Keys can also be entered in the phone app, which is what the published APK relies on.
        buildConfigField("String", "OPENAI_API_KEY", quoted(prop("openai.apiKey", "OPENAI_API_KEY")))
        buildConfigField("String", "OPENAI_MODEL", quoted(prop("openai.model", "OPENAI_MODEL").ifEmpty { "gpt-6-sol" }))
        buildConfigField("String", "JEV_ENDPOINT", quoted(prop("jev.endpoint", "JEV_ENDPOINT")))
        buildConfigField("String", "JEV_API_KEY", quoted(prop("jev.apiKey", "JEV_API_KEY")))
    }

    signingConfigs {
        create("sideload") {
            // A stable key so every CI build installs over the previous one. Override with your own
            // keystore through the SIGNING_* environment variables (see README).
            storeFile = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "sideload.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "cars-and-dragons"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "sideload"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "cars-and-dragons"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/9/previous-compilation-data.bin")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

base {
    archivesName.set("cars-and-dragons-$appVersionName")
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
}
