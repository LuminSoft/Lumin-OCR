// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        maven { url = uri("https://raw.github.com/synergian/wagon-git/releases") }
        mavenCentral()
    }
    dependencies {
        classpath("ar.com.synergian:wagon-git:0.3.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}