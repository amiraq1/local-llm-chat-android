import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val enableBundledTvm4j = providers.gradleProperty("enableBundledTvm4j")
    .map(String::toBoolean)
    .orElse(false)

android {
    namespace = "ai.mlc.mlcllm"
    compileSdk = 35

    defaultConfig {
        minSdk = 22
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("output")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    if (enableBundledTvm4j.get()) {
        // Opt-in only. The vendored TVM jar may be built with a newer Java target
        // than the default Android test/CI toolchain.
        implementation(fileTree(mapOf("dir" to "output", "include" to listOf("tvm4j_core.jar"))))
    }

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
