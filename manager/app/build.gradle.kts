@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion: Int = rootProject.extra.get("androidCompileSdkVersion") as Int
val androidCompileNdkVersion: String = rootProject.extra.get("androidCompileNdkVersion") as String
val androidBuildToolsVersion: String = rootProject.extra.get("androidBuildToolsVersion") as String
val androidMinSdkVersion: Int = rootProject.extra.get("androidMinSdkVersion") as Int
val androidTargetSdkVersion: Int = rootProject.extra.get("androidTargetSdkVersion") as Int
val managerVersionCode: Int = rootProject.extra.get("managerVersionCode") as Int
val managerVersionName: String = rootProject.extra.get("managerVersionName") as String
val kpmmVersion: String = rootProject.extra.get("kpmmVersion") as String

val ccache = System.getenv("PATH")?.split(File.pathSeparator)
    ?.map { File(it, "ccache") }?.firstOrNull { it.exists() }?.absolutePath

val baseFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fno-rtti", "-fvisibility=hidden",
    "-fvisibility-inlines-hidden", "-fno-exceptions", "-fno-stack-protector",
    "-fomit-frame-pointer", "-Wno-builtin-macro-redefined", "-Wno-unused-value",
    "-D__FILE__=__FILE_NAME__",
    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-Wno-unused", "-Wno-unused-parameter",
    "-Wno-unused-command-line-argument", "-Wno-incompatible-function-pointer-types",
    "-U_FORTIFY_SOURCE", "-D_FORTIFY_SOURCE=0"
)

val baseArgs = mutableListOf(
    "-DANDROID_STL=none", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
    "-DCMAKE_CXX_STANDARD=23", "-DCMAKE_C_STANDARD=23",
    "-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON", "-DCMAKE_VISIBILITY_INLINES_HIDDEN=ON",
    "-DCMAKE_CXX_VISIBILITY_PRESET=hidden", "-DCMAKE_C_VISIBILITY_PRESET=hidden"
).apply { if (ccache != null) add("-DANDROID_CCACHE=$ccache") }

android {
    namespace = "just.yervant.kpmmanager"

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            multiDexEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            externalNativeBuild {
                cmake {
                    val relFlags = listOf(
                        "-flto", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                        "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-Wl,--exclude-libs,ALL",
                        "-Ofast", "-fmerge-all-constants", "-flto=full", "-ffat-lto-objects",
                        "-fno-semantic-interposition", "-fno-threadsafe-statics"
                    )
                    cppFlags += relFlags
                    cFlags += relFlags
                    arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_CXX_FLAGS_RELEASE=-O3 -DNDEBUG", "-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG")
                }
            }
        }
    }

    dependenciesInfo.includeInApk = false

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
        prefab = true
    }

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName
        ndk.abiFilters.addAll(arrayOf("arm64-v8a"))
        externalNativeBuild {
            cmake {
                cppFlags += baseFlags + "-std=c++2b"
                cFlags += baseFlags + "-std=c2x"
                arguments += baseArgs
                abiFilters("arm64-v8a")
            }
        }
        buildConfigField("String", "buildKpmmV", "\"$kpmmVersion\"")
        base.archivesName = "KPM-Manager_${managerVersionCode}_${managerVersionName}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "**"
            merges += "META-INF/com/google/android/**"
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.28.0+"
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileSdk = androidCompileSdkVersion
    ndkVersion = androidCompileNdkVersion
    buildToolsVersion = androidBuildToolsVersion

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    android.sourceSets.named("main") {
        kotlin.directories += "build/generated/ksp/$name/kotlin"
        jniLibs.directories += "libs"
    }
}

// https://stackoverflow.com/a/77745844
tasks.withType<PackageAndroidArtifact> {
    doFirst { appMetadata.asFile.orNull?.writeText("") }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
    var urlStr = initialUrl
    var connection: HttpURLConnection
    var redirects = 0
    while (redirects < 10) {
        val url = URI.create(urlStr).toURL()
        connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

        val responseCode = connection.responseCode
        if (responseCode in 300..399) {
            val location = connection.getHeaderField("Location")
            if (!location.isNullOrEmpty()) {
                val nextUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URI.create(urlStr).resolve(location).toString()
                }
                connection.disconnect()
                urlStr = nextUrl
                redirects++
                continue
            }
        }
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw GradleException("HTTP error $responseCode when requesting $urlStr")
        }
        return connection
    }
    throw GradleException("Too many redirects for $initialUrl")
}

fun isFileUpdated(url: String, localFile: File): Boolean {
    if (!localFile.exists() || localFile.length() == 0L) return true
    return try {
        val connection = openConnectionWithRedirects(url)
        try {
            val remoteLastModified = connection.lastModified
            val remoteContentLength = connection.contentLengthLong
            if (remoteContentLength > 0 && localFile.length() != remoteContentLength) {
                true
            } else if (remoteLastModified > 0L && remoteLastModified > localFile.lastModified()) {
                true
            } else {
                false
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        false
    }
}

fun downloadFile(url: String, destFile: File) {
    destFile.parentFile?.mkdirs()
    val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

    val connection = openConnectionWithRedirects(url)
    try {
        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (tempFile.length() == 0L) {
            tempFile.delete()
            throw GradleException("Downloaded file is empty: $url")
        }
        if (destFile.exists()) {
            destFile.delete()
        }
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
    } finally {
        connection.disconnect()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}

fun registerDownloadTask(
    taskName: String, srcUrl: String, destPath: String, project: Project
) {
    project.tasks.register(taskName) {
        val destFile = File(destPath)
        outputs.file(destFile)

        doLast {
            if (!destFile.exists() || destFile.length() == 0L || isFileUpdated(srcUrl, destFile)) {
                println(" - Downloading $srcUrl to ${destFile.absolutePath}")
                downloadFile(srcUrl, destFile)
                println(" - Download completed.")
            } else {
                println(" - File is up-to-date, skipping download.")
            }
        }
    }
}

registerDownloadTask(
    taskName = "downloadKpimg",
    srcUrl = "https://github.com/Yervant7/KPM-Manager/releases/download/$kpmmVersion/kpimg",
    destPath = "${project.projectDir}/src/main/assets/kpimg",
    project = project
)

registerDownloadTask(
    taskName = "downloadKptools",
    srcUrl = "https://github.com/Yervant7/KPM-Manager/releases/download/$kpmmVersion/kptools",
    destPath = "${project.projectDir}/libs/arm64-v8a/libkptools.so",
    project = project
)

tasks.named("preBuild") {
    dependsOn("downloadKpimg", "downloadKptools")
}

ksp {
    arg("compose-destinations.defaultTransitions", "none")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime.livedata)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.compose.destinations.core)
    ksp(libs.compose.destinations.ksp)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.nio)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.sheet.compose.dialogs.core)
    implementation(libs.sheet.compose.dialogs.list)
    implementation(libs.sheet.compose.dialogs.input)

    implementation(libs.markdown)

    implementation(libs.ini4j)

    compileOnly(libs.cxx)
    implementation(libs.miuix.ui)
    implementation(libs.material.kolor)
}


