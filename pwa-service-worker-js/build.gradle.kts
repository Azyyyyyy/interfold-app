import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  kotlin("multiplatform")
}

kotlin {
  js {
    browser {
      commonWebpackConfig {
        outputFileName = "interfold-sw.js"
      }
    }
    useCommonJs()
    binaries.executable()
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions {
    optIn.add("kotlin.js.ExperimentalJsExport")
  }

  sourceSets {
    val jsMain by getting {
      kotlin.srcDir("../shared/src/appUpdatePolicy/kotlin")
    }
  }
}

val assembleInterfoldSw = tasks.register<Copy>("assembleInterfoldSw") {
  group = "build"
  description = "Copies the webpack worker bundle to build/interfold-sw/interfold-sw.js"
  dependsOn("jsBrowserProductionWebpack")
  from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) {
    include("interfold-sw.js", "interfold-sw.js.map")
  }
  into(layout.buildDirectory.dir("interfold-sw"))
}

tasks.named("jsBrowserProductionWebpack").configure {
  finalizedBy(assembleInterfoldSw)
}
