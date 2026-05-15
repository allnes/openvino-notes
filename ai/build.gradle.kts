import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val openvinoGenAiAndroidDir = providers.gradleProperty("openvinoGenAiAndroidDir")
val openvinoAndroidPrebuildRepo =
    providers.gradleProperty("openvinoAndroidPrebuildRepo").orElse("embedded-dev-research/openvino-notes")
val openvinoAndroidPrebuildRunId = providers.gradleProperty("openvinoAndroidPrebuildRunId").orElse("25928695317")
val openvinoAndroidPrebuildArtifactName =
    providers
        .gradleProperty("openvinoAndroidPrebuildArtifactName")
        .orElse("openvino-android-arm64-v8a-android-mbind-compat.zip")
val openvinoAndroidPrebuildPackageName =
    providers
        .gradleProperty("openvinoAndroidPrebuildPackageName")
        .orElse("openvino-android-arm64-v8a-android-mbind-compat")
val openvinoAndroidPrebuildDownloadDir =
    layout.buildDirectory.dir("openvino/prebuild/download/${openvinoAndroidPrebuildRunId.get()}")
val openvinoAndroidPrebuildExtractDir =
    layout.buildDirectory.dir("openvino/prebuild/extracted/${openvinoAndroidPrebuildRunId.get()}")
val openvinoAndroidPrebuildArchive =
    openvinoAndroidPrebuildDownloadDir.map { it.file(openvinoAndroidPrebuildArtifactName.get()) }
val openvinoAndroidPrebuildPackageDir =
    openvinoAndroidPrebuildExtractDir.map { it.dir(openvinoAndroidPrebuildPackageName.get()) }
val resolvedOpenvinoGenAiAndroidDir =
    openvinoGenAiAndroidDir.orElse(openvinoAndroidPrebuildPackageDir.map { it.asFile.absolutePath })
val openvinoAndroidAbi = "arm64-v8a"
val openvinoRuntimeAssetRootDir = layout.buildDirectory.dir("generated/openvinoRuntimeAssets")
val openvinoRuntimeAssetDir = openvinoRuntimeAssetRootDir.map { it.dir("openvino-runtime") }
val onDeviceLlmModelId =
    providers.gradleProperty("onDeviceLlmModelId").orElse("OpenVINO/Qwen3-0.6B-int4-ov")
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

        ndk {
            abiFilters += openvinoAndroidAbi
        }

        externalNativeBuild {
            cmake {
                arguments +=
                    listOf(
                        "-DOPENVINO_GENAI_ANDROID_DIR=${resolvedOpenvinoGenAiAndroidDir.get()}",
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

    sourceSets {
        getByName("main") {
            jniLibs.directories.clear()
            jniLibs.directories.add(
                file(resolvedOpenvinoGenAiAndroidDir.get())
                    .resolve("android-jni")
                    .absolutePath,
            )
            assets.directories.add(openvinoRuntimeAssetRootDir.get().asFile.absolutePath)
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

    packaging {
        jniLibs {
            pickFirsts += "lib/**/libc++_shared.so"
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

val downloadOpenVinoAndroidPrebuild by tasks.registering(Exec::class) {
    group = "ai"
    description = "Download the Android OpenVINO prebuild artifact from PR 89."

    onlyIf { !openvinoGenAiAndroidDir.isPresent && !openvinoAndroidPrebuildArchive.get().asFile.isFile }
    outputs.file(openvinoAndroidPrebuildArchive)

    doFirst {
        openvinoAndroidPrebuildDownloadDir.get().asFile.mkdirs()
    }

    commandLine(
        "python3",
        "scripts/download_openvino_prebuild.py",
        "--repo",
        openvinoAndroidPrebuildRepo.get(),
        "--run-id",
        openvinoAndroidPrebuildRunId.get(),
        "--artifact-name",
        openvinoAndroidPrebuildArtifactName.get(),
        "--output",
        openvinoAndroidPrebuildArchive.get().asFile.absolutePath,
    )
}

val extractOpenVinoAndroidPrebuild by tasks.registering(Copy::class) {
    group = "ai"
    description = "Extract the Android OpenVINO prebuild for native linking and packaging."

    onlyIf { !openvinoGenAiAndroidDir.isPresent }
    dependsOn(downloadOpenVinoAndroidPrebuild)
    from({ zipTree(openvinoAndroidPrebuildArchive.get().asFile) })
    into(openvinoAndroidPrebuildExtractDir)
    outputs.dir(openvinoAndroidPrebuildPackageDir)
}

val stageOpenVinoRuntimeAssets by tasks.registering {
    group = "ai"
    description = "Stage OpenVINO runtime metadata that must live next to extracted native libraries."

    dependsOn(extractOpenVinoAndroidPrebuild)
    inputs.dir(resolvedOpenvinoGenAiAndroidDir)
    outputs.dir(openvinoRuntimeAssetDir)

    doLast {
        val packageDir = file(resolvedOpenvinoGenAiAndroidDir.get())
        val jniDir = packageDir.resolve("android-jni/$openvinoAndroidAbi")
        val runtimeLibDir = packageDir.resolve("runtime/lib")
        val outputRoot = openvinoRuntimeAssetDir.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val pluginXmlCandidates =
            listOf(runtimeLibDir, jniDir)
                .flatMap { root ->
                    if (root.isDirectory) {
                        val candidates =
                            root.walkTopDown().filter { candidate ->
                                candidate.isFile &&
                                    candidate.name == "plugins.xml"
                            }
                        candidates.toList()
                    } else {
                        emptyList()
                    }
                }
        val installedPluginXml = pluginXmlCandidates.firstOrNull { it.readText().contains("<plugin ") }

        val pluginDirName =
            installedPluginXml
                ?.parentFile
                ?.name
                ?.takeIf { it.startsWith("openvino-") }
                ?: runtimeLibDir
                    .listFiles()
                    ?.firstOrNull { it.isDirectory && it.name.startsWith("openvino-") }
                    ?.name
                ?: "openvino-${openvinoAndroidPrebuildPackageName.get().substringAfterLast("-")}"
        val targetPluginXml = outputRoot.resolve("$pluginDirName/plugins.xml")
        targetPluginXml.parentFile.mkdirs()

        if (installedPluginXml != null) {
            installedPluginXml.copyTo(targetPluginXml, overwrite = true)
        } else {
            val cpuPlugin = jniDir.resolve("libopenvino_arm_cpu_plugin.so")
            if (!cpuPlugin.isFile) {
                throw GradleException("OpenVINO CPU plugin is missing: $cpuPlugin")
            }
            targetPluginXml.writeText(
                """
                <ie>
                    <plugins>
                        <plugin name="CPU" location="libopenvino_arm_cpu_plugin.so"/>
                    </plugins>
                </ie>
                """.trimIndent() + "\n",
            )
        }
    }
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
        onDeviceLlmModelId.get(),
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
    dependsOn(extractOpenVinoAndroidPrebuild)
    dependsOn(stageOpenVinoRuntimeAssets)
    dependsOn("stageOpenVinoLlmAssets")
}
