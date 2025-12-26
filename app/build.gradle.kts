plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// Task per incrementare il build number
abstract class IncrementBuildNumberTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    abstract val buildPropertiesFile: RegularFileProperty
    
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
    
    @TaskAction
    fun increment() {
        val file = buildPropertiesFile.get().asFile
        val buildNumber = if (file.exists()) {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            (props.getProperty("buildNumber", "0").toIntOrNull() ?: 0) + 1
        } else {
            1
        }
        
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val buildDate = dateFormat.format(Date())
        
        val props = Properties()
        props.setProperty("buildNumber", buildNumber.toString())
        props.setProperty("buildDate", buildDate)
        outputFile.get().asFile.outputStream().use { props.store(it, "Build number and date") }
        
        println("Build number incremented to: $buildNumber - $buildDate")
    }
}

tasks.register<IncrementBuildNumberTask>("incrementBuildNumber") {
    buildPropertiesFile.set(layout.projectDirectory.file("build.properties"))
    outputFile.set(layout.projectDirectory.file("build.properties"))
}

android {
    namespace = "it.srik.TypeQ25"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.srik.TypeQ25"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Leggi build number e data dal file
        val buildPropertiesFile = file("build.properties")
        val buildNumber = if (buildPropertiesFile.exists()) {
            val props = Properties()
            buildPropertiesFile.inputStream().use { props.load(it) }
            props.getProperty("buildNumber", "0").toIntOrNull() ?: 0
        } else {
            0
        }
        
        val buildDate = if (buildPropertiesFile.exists()) {
            val props = Properties()
            buildPropertiesFile.inputStream().use { props.load(it) }
            props.getProperty("buildDate", "")
        } else {
            ""
        }
        
        // Aggiungi a BuildConfig
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../TypeQ25-release-key.jks")
            storePassword = "typeq25pass"
            keyAlias = "typeq25-key"
            keyPassword = "typeq25pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // Disable lint for release to avoid file lock issues
            isDebuggable = false
        }
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    // Esegui incrementBuildNumber prima di preBuild
    tasks.named("preBuild").configure {
        dependsOn("incrementBuildNumber")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    // Accompanist Pager for tutorial screens
    implementation("com.google.accompanist:accompanist-pager:0.32.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.32.0")
    // RecyclerView per performance ottimali nella griglia emoji
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Emoji2 per supporto emoji future-proof
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}