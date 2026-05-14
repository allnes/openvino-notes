import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val openvinoGenAiAndroidDir = providers.gradleProperty("openvinoGenAiAndroidDir")

android {
    namespace = "com.itlab.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    if (openvinoGenAiAndroidDir.isPresent) {
        defaultConfig {
            externalNativeBuild {
                cmake {
                    arguments +=
                        listOf(
                            "-DOPENVINO_GENAI_ANDROID_DIR=${openvinoGenAiAndroidDir.get()}",
                            "-DANDROID_STL=c++_shared",
                        )
                }
            }
        }

        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(project(":domain"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register<Exec>("prepareGemma3OpenVinoModel") {
    group = "ai"
    description = "Export google/gemma-3-270m-it to an INT4 OpenVINO GenAI model bundle."

    val outputDir = layout.buildDirectory.dir("gemma3/gemma3-270m-it-openvino")
    commandLine(
        "python3",
        "scripts/prepare_gemma3_openvino_model.py",
        "--output",
        outputDir.get().asFile.absolutePath,
    )
}

tasks.register<Copy>("stageGemma3OpenVinoAssets") {
    group = "ai"
    description = "Copy the prepared Gemma 3 OpenVINO model into app assets for local packaging."
    dependsOn("prepareGemma3OpenVinoModel")

    from(layout.buildDirectory.dir("gemma3/gemma3-270m-it-openvino"))
    into(layout.projectDirectory.dir("src/main/assets/models/gemma3-270m-it-openvino"))
}
