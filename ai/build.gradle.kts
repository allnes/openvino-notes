import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val openvinoGenAiAndroidDir = providers.gradleProperty("openvinoGenAiAndroidDir")
val onDeviceLlmModelId = "Qwen/Qwen2.5-0.5B-Instruct"
val onDeviceLlmWeightFormat = providers.gradleProperty("onDeviceLlmWeightFormat").orElse("int4")
val onDeviceLlmPythonVenvDir = layout.buildDirectory.dir("llm/python-venv")
val onDeviceLlmExportDir = layout.buildDirectory.dir("llm/on-device-llm-openvino")
val onDeviceLlmAssetDir = layout.projectDirectory.dir("src/main/assets/models/on-device-llm-openvino")

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

tasks.register<Exec>("prepareOpenVinoLlmModel") {
    group = "ai"
    description = "Export the bundled on-device LLM to an OpenVINO GenAI model bundle."

    inputs.property("modelId", onDeviceLlmModelId)
    inputs.property("weightFormat", onDeviceLlmWeightFormat)
    outputs.dir(onDeviceLlmExportDir)

    commandLine(
        "python3",
        "scripts/prepare_openvino_llm_model.py",
        "--model-id",
        onDeviceLlmModelId,
        "--weight-format",
        onDeviceLlmWeightFormat.get(),
        "--output",
        onDeviceLlmExportDir.get().asFile.absolutePath,
        "--venv",
        onDeviceLlmPythonVenvDir.get().asFile.absolutePath,
        "--install-deps",
    )
}

tasks.register<Copy>("stageOpenVinoLlmAssets") {
    group = "ai"
    description = "Copy the prepared OpenVINO LLM model into app assets for local packaging."
    dependsOn("prepareOpenVinoLlmModel")

    from(onDeviceLlmExportDir)
    into(onDeviceLlmAssetDir)
}

tasks.named("preBuild") {
    dependsOn("stageOpenVinoLlmAssets")
}
