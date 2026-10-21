@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("com.android.application")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("kotlin-parcelize")
  kotlin("plugin.serialization")
}

dependencies {
  implementation(project(":shared"))
  val decomposeVersion = "3.4.0"
  implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
  implementation("com.google.firebase:firebase-messaging")
  implementation("androidx.browser:browser:1.9.0")
  implementation("com.arkivanov.decompose:decompose:$decomposeVersion")

  implementation("com.nimbusds:nimbus-jose-jwt:10.8")

  val composeVersion = findProperty("compose.version") as String
  val material3Version = findProperty("compose.material3.version") as String

  // Widget
  implementation("androidx.work:work-runtime-ktx:2.11.1")
  implementation("io.coil-kt.coil3:coil:3.4.0")
  implementation("io.coil-kt.coil3:coil-network-ktor3:3.4.0")
  implementation("androidx.glance:glance-appwidget:1.2.0")
  implementation("androidx.glance:glance-material3:1.2.0")
  debugImplementation("androidx.glance:glance-preview:1.2.0")
  debugImplementation("androidx.glance:glance-appwidget-preview:1.2.0")
  implementation("com.materialkolor:material-kolor:2.1.1")
  implementation("org.jetbrains.compose.material3:material3:$material3Version")
  implementation("org.jetbrains.compose.components:components-resources:$composeVersion")

  val ktorVersion = "3.4.0"

  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

  // HTTP client
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-client-logging:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test:rules:1.7.0")
  androidTestImplementation("androidx.test:core:1.7.0")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  androidTestImplementation("io.ktor:ktor-client-okhttp:$ktorVersion")
  androidTestImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  androidTestImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}

// ---------------------------------------------------------------------------
// Release signing (CI only)
// ---------------------------------------------------------------------------
// Credentials are read from environment variables (preferred, matches the
// mobile-release.yml workflow) with a Gradle-property fallback for anyone
// wanting to test a signed local build via ~/.gradle/gradle.properties.
// If any input is missing the `release` build type falls back to unsigned
// output so local `./gradlew :androidApp:assembleRelease` still works
// without a keystore checked in anywhere.
// ---------------------------------------------------------------------------
val releaseKeystoreFile = (System.getenv("INTERFOLD_KEYSTORE_FILE")
  ?: findProperty("interfold.keystoreFile") as String?)?.let(::file)
val releaseKeystorePassword = System.getenv("INTERFOLD_KEYSTORE_PASSWORD")
  ?: findProperty("interfold.keystorePassword") as String?
val releaseKeyAlias = System.getenv("INTERFOLD_KEY_ALIAS")
  ?: findProperty("interfold.keyAlias") as String?
val releaseKeyPassword = System.getenv("INTERFOLD_KEY_PASSWORD")
  ?: findProperty("interfold.keyPassword") as String?

val hasReleaseSigning = releaseKeystoreFile?.exists() == true &&
  !releaseKeystorePassword.isNullOrEmpty() &&
  !releaseKeyAlias.isNullOrEmpty() &&
  !releaseKeyPassword.isNullOrEmpty()

android {
  compileSdk = (findProperty("android.compileSdk") as String).toInt()
  namespace = "app.interfold"

  sourceSets["main"].assets.srcDirs(layout.buildDirectory.dir("generated/shared-assets"))

  defaultConfig {
    applicationId = "app.interfold.InterfoldApp"
    minSdk = (findProperty("android.minSdk") as String).toInt()
    targetSdk = (findProperty("android.targetSdk") as String).toInt()
    // Version + name are computed centrally in the root build.gradle.kts;
    // override per-build via -PappVersion.tag / -PappVersion.runNumber.
    versionCode = rootProject.extra["app.versionCode"] as Int
    versionName = rootProject.extra["app.versionName"] as String
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets.getByName("androidTest").assets.directories.add(
    layout.buildDirectory.get().asFile.resolve("generated/android-it-assets").absolutePath,
  )

  testOptions {
    animationsDisabled = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = releaseKeystorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    getByName("debug") {
      // applicationIdSuffix = ".debug"
    }
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }

    create("debugMinified") {
      initWith(getByName("release"))
      matchingFallbacks.add("release")

      signingConfig = signingConfigs.getByName("debug")
    }

    create("benchmark") {
      initWith(getByName("release"))
      matchingFallbacks.add("release")
      signingConfig = signingConfigs.getByName("debug")
      isShrinkResources = false
      isMinifyEnabled = false
    }
  }
  buildFeatures {
    viewBinding = true
  }

  /*kotlinAndroid {
    // any Kotlin Android specific config
  }*/
}

dependencies {
  implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}

val copyComposeResources by tasks.creating {
  val modules = listOf("shared" to "interfoldapp.shared.resources", "krop" to "interfoldapp.krop.resources")
  
  modules.forEach { (moduleName, targetFolder) ->
    val copyTask = tasks.create("copy${moduleName.capitalize()}Resources", Copy::class) {
      from(project(":$moduleName").layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
      into(layout.buildDirectory.dir("generated/shared-assets/composeResources/$targetFolder"))
      dependsOn(":$moduleName:prepareComposeResourcesTaskForCommonMain")
    }
    dependsOn(copyTask)
  }
}

tasks.matching {
  it.name.contains("Assets") ||
    it.name.contains("JavaRes") ||
    it.name.contains("Resources") ||
    it.name.contains("lint", ignoreCase = true)
}.configureEach {
  if (this != copyComposeResources && !this.name.startsWith("copy") && !this.name.endsWith("Resources")) {
    dependsOn(copyComposeResources)
  }
}

// ---------------------------------------------------------------------------
// Android Friend-front FCM receive IT
// ---------------------------------------------------------------------------
// `:androidApp:androidDeviceIntegrationTest` starts the same in-memory
// backend as wasm IT, writes minted JWTs into androidTest assets, `adb
// reverse`s 18080 so the emulator uses http://127.0.0.1:18080, then runs
// connectedDebugAndroidTest. The instrumented test skips when those
// assets (or Play Services) are missing.
// ---------------------------------------------------------------------------
val androidItPort = "18080"
val androidItAssetsDir = layout.buildDirectory.dir("generated/android-it-assets")

fun androidSdkDir(): File {
  val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
  if (!env.isNullOrBlank()) return File(env)
  val local = System.getenv("LOCALAPPDATA")
  if (!local.isNullOrBlank()) {
    val candidate = File("$local/Android/Sdk")
    if (candidate.isDirectory) return candidate
  }
  return File("${System.getProperty("user.home")}/AppData/Local/Android/Sdk")
}

fun adbExecutable(): File {
  val exe = if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
    "adb.exe"
  } else {
    "adb"
  }
  return androidSdkDir().resolve("platform-tools").resolve(exe)
}

fun emulatorExecutable(): File {
  val exe = if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
    "emulator.exe"
  } else {
    "emulator"
  }
  return androidSdkDir().resolve("emulator").resolve(exe)
}

fun adbOutput(vararg args: String): String {
  val adb = adbExecutable()
  if (!adb.isFile) return ""
  val process = ProcessBuilder(adb.absolutePath, *args)
    .redirectErrorStream(true)
    .start()
  val text = process.inputStream.bufferedReader().readText()
  process.waitFor()
  return text
}

val writeAndroidItAssets = tasks.register("writeAndroidItAssets") {
  group = "verification"
  description = "Writes minted backend URL/JWTs into androidTest assets when the wasm IT backend is up."
  val urlIn = project(":shared").layout.buildDirectory.file("wasm-it/backend-url.txt")
  val outDir = androidItAssetsDir
  inputs.files(urlIn).withPropertyName("wasmBackendUrl").optional()
  outputs.dir(outDir)
  outputs.upToDateWhen { false }
  mustRunAfter(":shared:startWasmIntegrationBackend")
  doLast {
    val dest = outDir.get().asFile
    dest.mkdirs()
    val props = File(dest, "interfold-it.properties")
    val urlFile = urlIn.get().asFile
    if (!urlFile.exists()) {
      props.writeText("")
      return@doLast
    }
    val dir = urlFile.parentFile
    fun sibling(name: String): String =
      File(dir, name).takeIf { it.isFile }?.readText()?.trim().orEmpty()
    val url = urlFile.readText().trim()
      .replace("http://127.0.0.1:", "http://10.0.2.2:")
    props.writeText(
      buildString {
        appendLine("backendUrl=$url")
        appendLine("backendToken=${sibling("backend-token.txt")}")
        appendLine("backendSystemId=${sibling("backend-system-id.txt")}")
        appendLine("backendTokenA=${sibling("backend-token-a.txt")}")
        appendLine("backendSystemIdA=${sibling("backend-system-id-a.txt")}")
      },
    )
    logger.lifecycle("[android-it] wrote {}", props.absolutePath)
  }
}

val ensureAndroidEmulator = tasks.register("ensureAndroidEmulator") {
  group = "verification"
  description = "Starts Pixel_7 (or ANDROID_IT_AVD) if no emulator/device is connected."
  notCompatibleWithConfigurationCache("Talks to adb / starts a long-lived emulator")
  doLast {
    val adb = adbExecutable()
    check(adb.isFile) { "adb not found at ${adb.absolutePath}. Install Android platform-tools." }
    val devices = adbOutput("devices").lineSequence()
      .drop(1)
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("*") && it.contains("\tdevice") }
      .toList()
    if (devices.isNotEmpty()) {
      logger.lifecycle("[android-it] using device {}", devices.first().substringBefore('\t'))
      return@doLast
    }
    val emulator = emulatorExecutable()
    check(emulator.isFile) { "emulator not found at ${emulator.absolutePath}." }
    val avd = System.getenv("ANDROID_IT_AVD")?.trim().orEmpty().ifEmpty { "Pixel_7" }
    logger.lifecycle("[android-it] starting emulator -avd {}", avd)
    ProcessBuilder(emulator.absolutePath, "-avd", avd, "-netdelay", "none", "-netspeed", "full")
      .redirectErrorStream(true)
      .redirectOutput(layout.buildDirectory.file("android-it-emulator.log").get().asFile.apply {
        parentFile.mkdirs()
      })
      .start()
    val deadline = System.currentTimeMillis() + 180_000
    var booted = false
    while (System.currentTimeMillis() < deadline) {
      val ready = adbOutput("devices").contains("\tdevice")
      if (ready) {
        val boot = adbOutput("shell", "getprop", "sys.boot_completed").trim()
        if (boot == "1") {
          booted = true
          break
        }
      }
      Thread.sleep(2_000)
    }
    check(booted) { "Timed out waiting for emulator $avd to boot. See build/android-it-emulator.log" }
    logger.lifecycle("[android-it] emulator {} booted", avd)
  }
}

tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
  dependsOn(writeAndroidItAssets)
  mustRunAfter(":shared:startWasmIntegrationBackend")
  mustRunAfter(ensureAndroidEmulator)
  doFirst {
    val adb = adbExecutable()
    if (adb.isFile) {
      ProcessBuilder(adb.absolutePath, "reverse", "tcp:$androidItPort", "tcp:$androidItPort")
        .redirectErrorStream(true)
        .start()
        .waitFor()
      ProcessBuilder(
        adb.absolutePath,
        "shell",
        "pm",
        "grant",
        "app.interfold.InterfoldApp",
        "android.permission.ACCESS_LOCAL_NETWORK",
      ).redirectErrorStream(true).start().waitFor()
      logger.lifecycle("[android-it] adb reverse tcp:{} tcp:{}", androidItPort, androidItPort)
    }
  }
}

tasks.matching { it.name.contains("AndroidTestAssets", ignoreCase = true) }.configureEach {
  dependsOn(writeAndroidItAssets)
}

tasks.register("androidDeviceIntegrationTest") {
  group = "verification"
  description = "Starts the in-memory backend and runs connectedDebugAndroidTest (Friend-front FCM receive)."
  notCompatibleWithConfigurationCache("Depends on a long-lived Testcontainers backend + emulator")
  dependsOn(":shared:startWasmIntegrationBackend")
  dependsOn(ensureAndroidEmulator)
  dependsOn(writeAndroidItAssets)
  dependsOn("connectedDebugAndroidTest")
  finalizedBy(":shared:stopWasmIntegrationBackend")
}