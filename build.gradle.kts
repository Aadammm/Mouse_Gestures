import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.mousegesture"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

intellij {
    version.set("2024.2")
    type.set("IC") // IntelliJ IDEA Community Edition
    plugins.set(listOf())
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("999.*")
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
