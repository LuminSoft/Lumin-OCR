plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.luminsoft.ocr"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.android)
    implementation(project(":ocr:enroll_nationalid_detection"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.face.detection)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.play.services.vision.common)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation(libs.coil.compose)
    implementation(libs.androidx.camera.core.v110)
    implementation(libs.androidx.camera.camera2.v110)
    implementation(libs.androidx.camera.lifecycle.v110)
    implementation(libs.androidx.camera.view.v110)
    implementation(libs.androidx.camera.extensions.v110)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.luminsoft"
            artifactId = "ocr"
            version = "1.0.0"
            artifact("$buildDir/outputs/aar/ocr-release.aar")

            /*       androidComponents {
                       onVariants(selector().withBuildType("release")) { variant ->
                           artifact(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.AAR))
                       }
                   }*/
        }
    }
    repositories {
        maven {
            name = "LocalMaven"
            url = uri("${rootProject.buildDir}/maven-repo") // local folder
        }
/*        maven {
            name = "Lumin-OCR-SDK-Android"
            url =
                uri("https://Andrew_Samir7@bitbucket.org/ExcelSystemsEgypt/lumin-ocr-sdk-android.git")
            credentials {
                username = "Andrew_Samir7"
                password = "ATBBPFQH6k96W6PmpSwHpK7HfFMf249C1E5D"
            }
        }*/
        /*        maven {
                    url =
                        uri("git:releases://git@bitbucket.org:ExcelSystemsEgypt/lumin-ocr-sdk-android.git")
                    credentials {
                        username ="ExcelSystemsEgypt" *//*providers.gradleProperty("bitbucketUser").getOrElse("")*//*
                password = "ATBBPFQH6k96W6PmpSwHpK7HfFMf249C1E5D"*//*providers.gradleProperty("bitbucketPassword").getOrElse("")*//*
            }
        }*/
    }
}