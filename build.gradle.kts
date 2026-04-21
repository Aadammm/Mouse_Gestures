import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.mousegesture"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        pluginVerifier()
    }
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set(provider { null })
    }
    buildSearchableOptions {
        enabled = false
    }

    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}

kotlin {
    jvmToolchain(21)
}
