plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------------------------
// Versions
// ---------------------------------------------------------------------------------------------

// In gradle.properties, shared with the Play modules (see the root build file, which sets the Kotlin JDK alike)
val sdkCompile = property("sdkCompile").toString().toInt()
val sdkTarget = property("sdkTarget").toString().toInt()
val sdkMin = property("sdkMin").toString().toInt()
/** Bytecode level; Java and Kotlin must match. */
val jvm = JavaVersion.toVersion(property("jvmTarget").toString())

// Passed by the workflows: a release's from its tag (v<gplayCode>_<versionName>, build-app.yml), the tests' from
// their commit (tests.yml). Else the day's n-th build here, <YY.MM.DD>-b<n> (buildSrc's AppVersion.kt), version code 1
val appVersionName: String? = providers.gradleProperty("versionName").orNull
/** 1 when not passed: AGP and bundletool won't take 0 (nor none). */
val appVersionCode = providers.gradleProperty("gplayCode").map(String::toInt).getOrElse(1)

/** The parts of the Linux subsystem, each a Play module (features/<name>, see settings.gradle.kts). */
@Suppress("UNCHECKED_CAST") // settings.gradle.kts': the modules with a part's archive (not a tool's pages alone)
val linuxPackages = (gradle.extra["linuxParts"] as List<String>).sorted()
/** Every Play module's path (one per part). */
val featureModules = rootProject.project(":features").childProjects.keys.sorted().map { ":features:$it" }
/** Where PRoot goes: the core's module (every part builds on it). */
val prootLibsDir: File = rootProject.file("features/core/src/main/jniLibs")

// ---------------------------------------------------------------------------------------------

android {
    namespace = "org.lukemech.lmtoolbox"
    compileSdk { version = release(sdkCompile) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "org.lukemech.lmtoolbox"
        minSdk = sdkMin
        targetSdk = sdkTarget

        versionCode = appVersionCode
        // Without one passed, a stand-in for what reads the DSL (the IDE): the variants get the day's build's below
        versionName = appVersionName ?: "dev"

        // The end-to-end tests (src/androidTest), against the app installed with its Play modules (tests.yml)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = jvm
        targetCompatibility = jvm
    }
    buildTypes {
        debug {
            // Debug package name override
            applicationIdSuffix = ".dbg"
            isDebuggable = true

            versionNameSuffix = ".dbg"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["appLabel"] = "@string/app_name"
        }
    }
    sourceSets {
        // Resources of each tool (strings, drawables, widget layouts...) in its own folder, as its code:
        // src/main/res-features/<category>/<route>/{drawable,layout,xml,values*}, also the title of a tool
        // whose pages are in a Play module (its own strings are there, in the same folders). res/values
        // keeps the app's strings and those several tools share, and the Play modules' titles (dist:title)
        getByName("main") {
            file("src/main/res-features").listFiles { it.isDirectory }.orEmpty().forEach { category ->
                category.listFiles { it.isDirectory }.orEmpty().forEach { res.directories.add(it.path) }
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Extract .so files to nativeLibraryDir: proot is exec'd from there (not just dlopen'd). The app
            // has none, but Android extracts every split's (the core module's PRoot) as the main APK says
            useLegacyPackaging = true
        }
    }
    // Every language in the app's main APK, as the app switches them itself (per-app language, search)
    @Suppress("UnstableApiUsage")
    bundle {
        language {
            enableSplit = false
        }
    }
    // A tool's pages and its Linux programs, installed from Play on its first use (see the root build file)
    dynamicFeatures.addAll(featureModules)
}

dependencies {
    // Jetpack Compose BOM (manages versions for all Compose artifacts)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    // GPS altitude is above the ellipsoid; this turns it into above sea level (as Android 14+ does)
    implementation(libs.androidx.core.location.altitude)
    // The image resizer's EXIF copy (JPEG, PNG, WebP on every Android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material3)
    // Large artifact, but R8 strips every icon the app doesn't use from release builds
    implementation(libs.androidx.material.icons.extended)

    // The Linux packages are shipped xz-compressed; Android has no built-in xz decoder
    implementation(libs.xz)
    // The tools' Play modules (FeatureDelivery)
    implementation(libs.play.feature.delivery)
    constraints {
        // Its dependencies, lifted to current versions (see libs.versions.toml)
        implementation(libs.androidx.fragment)
        implementation(libs.play.services.basement)
        implementation(libs.play.services.tasks)
        implementation(libs.play.core.common)
        implementation(libs.kotlinx.coroutines.android)
    }

    // The end-to-end tests: the app driven as a user would (Compose), the system's pickers answered (Espresso-Intents)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

// The day's build's name, set on every variant once dailyVersionName has counted it (its build type's suffix after it)
if (appVersionName == null) {
    val daily = dailyVersionName(registerDailyVersionName())
    androidComponents.onVariants { variant ->
        val suffix = android.buildTypes.getByName(variant.buildType!!).versionNameSuffix.orEmpty()
        variant.outputs.forEach { it.versionName.set(daily.map { name -> name + suffix }) }
    }
}

// The Linux packages and PRoot of the release gradle.properties pins, before every build; updateLinuxRelease
// pins the newest (buildSrc's LinuxPackages.kt)
registerLinuxPackageTasks(linuxPackages, prootLibsDir)
// The app and its Play modules on a device as from Play (buildSrc's LocalTesting.kt)
registerInstallLocalTesting(android.defaultConfig.applicationId + ".dbg")
