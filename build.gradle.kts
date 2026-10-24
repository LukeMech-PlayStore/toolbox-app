// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// The app and its Play modules: Kotlin compiled by the same JDK, to the same bytecode (gradle.properties)
subprojects {
    plugins.withType<com.android.build.gradle.api.AndroidBasePlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            // Provisioned by the foojay resolver (settings.gradle.kts)
            jvmToolchain(property("jdkVersion").toString().toInt())
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(property("jvmTarget").toString()))
            }
        }
    }
}

// The on-demand Play modules (features/<name>), one per Linux package: its archive
// (libpackage-<name>.tar.xz.so, put into its jniLibs by the app's updateLinuxPackages, buildSrc's LinuxPackages.kt) and, for the
// package a tool is built on, that tool's pages
subprojects {
    if (!path.startsWith(":features:")) return@subprojects
    val module = name
    plugins.withId("com.android.dynamic-feature") {
        extensions.configure<com.android.build.api.dsl.DynamicFeatureExtension> {
            namespace = "org.lukemech.lmtoolbox.features.$module"
            // As the app (gradle.properties)
            compileSdk { version = release(property("sdkCompile").toString().toInt()) { minorApiLevel = 0 } }
            defaultConfig { minSdk = property("sdkMin").toString().toInt() }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(property("jvmTarget").toString())
                targetCompatibility = JavaVersion.toVersion(property("jvmTarget").toString())
            }
            // Its package's archive is read straight from the module's APK, or from nativeLibraryDir.
            // As the app's: Android extracts every split's native libraries as the app's main APK says (the app's
            // must be, PRoot is exec'd from nativeLibraryDir), whatever a split's own manifest says (tried)
            packaging { jniLibs { useLegacyPackaging = true } }
            testOptions { unitTests.isReturnDefaultValues = true }
            // Its tools' resources, as the app's (src/main/res-features/<category>/<route>)
            sourceSets.getByName("main") {
                file("src/main/res-features").listFiles { it.isDirectory }.orEmpty().forEach { category ->
                    category.listFiles { it.isDirectory }.orEmpty().forEach { res.directories.add(it.path) }
                }
            }
        }
        dependencies {
            "implementation"(dependencies.project(":app"))
            "testImplementation"(libs.junit)
            // What the app's tests use too: Android's own JSONObject is a stub there
            "testImplementation"(libs.org.json)
        }
        tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(":app:updateLinuxPackages") }
    }
    // Modules with pages
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        dependencies {
            "implementation"(platform(libs.androidx.compose.bom))
            "implementation"(libs.androidx.core.ktx)
            "implementation"(libs.androidx.activity.compose)
            "implementation"(libs.androidx.ui)
            "implementation"(libs.androidx.foundation)
            "implementation"(libs.androidx.material3)
            "implementation"(libs.androidx.material.icons.extended)
        }
    }
}
