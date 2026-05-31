import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.publish)
}

group = "dev.ynagai.ondevice"

kotlin {
    androidLibrary {
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        namespace = "dev.ynagai.ondevice"
        withHostTestBuilder {}
    }
    iosArm64()
    iosSimulatorArm64()
    withSourcesJar(publish = true)

    swiftPMDependencies {
        iosMinimumDeploymentTarget.set("26.0")
        swiftPackage(
            url = url("https://github.com/uny/foundation-models-objc.git"),
            version = exact("1.0.0"),
            products = listOf(product("FoundationModelsObjC")),
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.genai.prompt)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("On-Device LLM")
        description.set("Framework-agnostic on-device LLM inference for Kotlin Multiplatform — Gemini Nano (Android) & Apple Foundation Models (iOS)")
        url.set("https://github.com/uny/ondevice-llm")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer { id.set("uny"); name.set("Yuki Nagai"); url.set("https://github.com/uny") }
        }
        scm {
            url.set("https://github.com/uny/ondevice-llm")
            connection.set("scm:git:https://github.com/uny/ondevice-llm.git")
            developerConnection.set("scm:git:https://github.com/uny/ondevice-llm.git")
        }
    }
}
