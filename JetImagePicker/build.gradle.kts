plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("maven-publish")
    id("signing")
}

group = "io.github.nerojust"
version = "1.0.0"

android {
    namespace = "com.nerojust.jetimagepicker"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar() // Optional: adds empty javadoc.jar if you don’t have one
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"]) // This needs to be inside afterEvaluate

                groupId = "io.github.nerojust"
                artifactId = "jetimagepicker"
                version = "1.0.0"

                pom {
                    name.set("JetImagePicker")
                    description.set("A Jetpack Compose image picker library")
                    url.set("https://github.com/nerojust/JetImagePicker")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("nerojust")
                            name.set("Adjekughene Nyerhovwo")
                            email.set("nerojust4@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/nerojust/JetImagePicker.git")
                        developerConnection.set("scm:git:ssh://github.com/nerojust/JetImagePicker.git")
                        url.set("https://github.com/nerojust/JetImagePicker")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.coil.compose)
}