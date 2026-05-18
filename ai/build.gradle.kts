import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val openvinoGenAiAndroidDir = providers.gradleProperty("openvinoGenAiAndroidDir")
val openvinoAndroidPrebuildRepo =
    providers.gradleProperty("openvinoAndroidPrebuildRepo").orElse("embedded-dev-research/openvino-notes")
val openvinoAndroidPrebuildReleaseTag =
    providers.gradleProperty("openvinoAndroidPrebuildReleaseTag").orElse("openvino-android-prebuilds-nightly")
val openvinoAndroidPrebuildArtifactName =
    providers
        .gradleProperty("openvinoAndroidPrebuildArtifactName")
        .orElse("openvino-android-arm64-v8a-android-mbind-compat.zip")
val openvinoAndroidPrebuildPackageName =
    providers
        .gradleProperty("openvinoAndroidPrebuildPackageName")
        .orElse("openvino-android-arm64-v8a-android-mbind-compat")
val openvinoAndroidPrebuildDownloadDir =
    layout.buildDirectory.dir("openvino/prebuild/download/${openvinoAndroidPrebuildReleaseTag.get()}")
val openvinoAndroidPrebuildExtractDir =
    layout.buildDirectory.dir("openvino/prebuild/extracted/${openvinoAndroidPrebuildReleaseTag.get()}")
val openvinoAndroidPrebuildArchive =
    openvinoAndroidPrebuildDownloadDir.map { it.file(openvinoAndroidPrebuildArtifactName.get()) }
val openvinoAndroidPrebuildArchiveMetadata =
    openvinoAndroidPrebuildDownloadDir.map { it.file("${openvinoAndroidPrebuildArtifactName.get()}.metadata.json") }
val openvinoAndroidPrebuildPackageDir =
    openvinoAndroidPrebuildExtractDir.map { it.dir(openvinoAndroidPrebuildPackageName.get()) }
val resolvedOpenvinoGenAiAndroidDir =
    openvinoGenAiAndroidDir.orElse(openvinoAndroidPrebuildPackageDir.map { it.asFile.absolutePath })
val genaiJavaApiDir =
    providers.gradleProperty("genaiJavaApiDir").orElse(
        providers.provider {
            val checkoutDir = rootProject.layout.projectDirectory.dir("../genai-java-api")
            checkoutDir.asFile.absolutePath
        },
    )
val genaiJavaApiProjectDir = file(genaiJavaApiDir.get())
val openvinoRuntimeCmakeDir =
    resolvedOpenvinoGenAiAndroidDir.map { packageDir ->
        file(packageDir).resolve("runtime/cmake").absolutePath
    }
val openvinoAndroidAbi = "arm64-v8a"
val openvinoRuntimeAssetRootDir = layout.buildDirectory.dir("generated/openvinoRuntimeAssets")
val openvinoRuntimeAssetDir = openvinoRuntimeAssetRootDir.map { it.dir("openvino-runtime") }
val onDeviceLlmModelId =
    providers.gradleProperty("onDeviceLlmModelId").orElse("OpenVINO/Qwen3-1.7B-int4-ov")
val onDeviceLlmWeightFormat = providers.gradleProperty("onDeviceLlmWeightFormat").orElse("int4")
val onDeviceLlmPythonVenvDir = layout.buildDirectory.dir("llm/python-venv")
val onDeviceLlmExportDir = layout.buildDirectory.dir("llm/on-device-llm-openvino")
val onDeviceLlmBundleDir = layout.buildDirectory.dir("llm/model-bundles")
val onDeviceLlmBundleRepo =
    providers.gradleProperty("onDeviceLlmBundleRepo").orElse(openvinoAndroidPrebuildRepo)
val onDeviceLlmBundleReleaseTag =
    providers.gradleProperty("onDeviceLlmBundleReleaseTag").orElse("openvino-llm-models-nightly")
val onDeviceLlmBundleArtifactName =
    providers
        .gradleProperty("onDeviceLlmBundleArtifactName")
        .orElse("on-device-llm-openvino-${onDeviceLlmWeightFormat.get()}.zip")
val onDeviceLlmBundleDownloadDir =
    layout.buildDirectory.dir("llm/model-bundle/download/${onDeviceLlmBundleReleaseTag.get()}")
val onDeviceLlmBundleExtractDir =
    layout.buildDirectory.dir("llm/model-bundle/extracted/${onDeviceLlmBundleReleaseTag.get()}")
val onDeviceLlmBundleArchive =
    onDeviceLlmBundleDownloadDir.map { it.file(onDeviceLlmBundleArtifactName.get()) }
val onDeviceLlmBundleArchiveMetadata =
    onDeviceLlmBundleDownloadDir.map { it.file("${onDeviceLlmBundleArtifactName.get()}.metadata.json") }
val onDeviceLlmPreparedDir = providers.gradleProperty("onDeviceLlmPreparedDir")
val resolvedOnDeviceLlmAssetSourceDir =
    onDeviceLlmPreparedDir.orElse(onDeviceLlmBundleExtractDir.map { it.asFile.absolutePath })
val onDeviceLlmAssetRootDir = layout.buildDirectory.dir("generated/openvinoLlmAssets")
val onDeviceLlmAssetDir = onDeviceLlmAssetRootDir.map { it.dir("models/on-device-llm-openvino") }

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
                targets += "ov_genai_java_jni"
                arguments +=
                    listOf(
                        "-DOV_GENAI_JNI_MODE=REAL",
                        "-DOpenVINO_DIR=${openvinoRuntimeCmakeDir.get()}",
                        "-DOpenVINOGenAI_DIR=${openvinoRuntimeCmakeDir.get()}",
                        "-DANDROID_STL=c++_shared",
                    )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = genaiJavaApiProjectDir.resolve("CMakeLists.txt")
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
            assets.directories.add(onDeviceLlmAssetRootDir.get().asFile.absolutePath)
            java.srcDir(genaiJavaApiProjectDir.resolve("src/main/java"))
            java.srcDir(genaiJavaApiProjectDir.resolve("src/android/java"))
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
            useLegacyPackaging = true
            pickFirsts += "lib/**/libc++_shared.so"
        }
    }

    lint {
        // The OpenVINO GenAI Android prebuild used by this module is arm64-v8a only.
        disable += "ChromeOsAbiSupport"
        // genai-java-api is consumed as an external Java wrapper source; its API lint policy is owned there.
        disable +=
            listOf(
                "SyntheticAccessor",
                "UnknownNullness",
                "UnsafeDynamicallyLoadedCode",
            )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    implementation(project(":domain"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val downloadOpenVinoAndroidPrebuild by tasks.registering(Exec::class) {
    group = "ai"
    description = "Download the Android OpenVINO prebuild from the rolling GitHub prerelease."

    onlyIf { !openvinoGenAiAndroidDir.isPresent }
    inputs.file(layout.projectDirectory.file("scripts/download_openvino_prebuild.py"))
    outputs.file(openvinoAndroidPrebuildArchive)
    outputs.file(openvinoAndroidPrebuildArchiveMetadata)
    outputs.upToDateWhen { false }

    doFirst {
        openvinoAndroidPrebuildDownloadDir.get().asFile.mkdirs()
    }

    commandLine(
        "python3",
        "scripts/download_openvino_prebuild.py",
        "--repo",
        openvinoAndroidPrebuildRepo.get(),
        "--release-tag",
        openvinoAndroidPrebuildReleaseTag.get(),
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

val stageOpenVinoRuntimeAssets by tasks.registering(Exec::class) {
    group = "ai"
    description = "Stage OpenVINO runtime metadata that must live next to extracted native libraries."

    dependsOn(extractOpenVinoAndroidPrebuild)
    inputs.dir(resolvedOpenvinoGenAiAndroidDir)
    inputs.file(genaiJavaApiProjectDir.resolve("tools/stage_android_runtime_assets.py"))
    outputs.dir(openvinoRuntimeAssetDir)

    commandLine(
        "python3",
        genaiJavaApiProjectDir.resolve("tools/stage_android_runtime_assets.py").absolutePath,
        "--package-dir",
        resolvedOpenvinoGenAiAndroidDir.get(),
        "--abi",
        openvinoAndroidAbi,
        "--package-name",
        openvinoAndroidPrebuildPackageName.get(),
        "--output",
        openvinoRuntimeAssetDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("prepareOpenVinoLlmModel") {
    group = "ai"
    description = "Export the bundled on-device LLM to an OpenVINO GenAI model bundle."

    inputs.property("modelId", onDeviceLlmModelId)
    inputs.property("weightFormat", onDeviceLlmWeightFormat)
    inputs.file(layout.projectDirectory.file("scripts/prepare_openvino_llm_model.py"))
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

val downloadOpenVinoLlmModelBundle by tasks.registering(Exec::class) {
    group = "ai"
    description = "Download the on-device LLM model bundle from the rolling GitHub prerelease."

    onlyIf { !onDeviceLlmPreparedDir.isPresent }
    inputs.file(layout.projectDirectory.file("scripts/download_openvino_prebuild.py"))
    outputs.file(onDeviceLlmBundleArchive)
    outputs.file(onDeviceLlmBundleArchiveMetadata)
    outputs.upToDateWhen { false }

    doFirst {
        onDeviceLlmBundleDownloadDir.get().asFile.mkdirs()
    }

    commandLine(
        "python3",
        "scripts/download_openvino_prebuild.py",
        "--repo",
        onDeviceLlmBundleRepo.get(),
        "--release-tag",
        onDeviceLlmBundleReleaseTag.get(),
        "--artifact-name",
        onDeviceLlmBundleArtifactName.get(),
        "--output",
        onDeviceLlmBundleArchive.get().asFile.absolutePath,
    )
}

val extractOpenVinoLlmModelBundle by tasks.registering(Copy::class) {
    group = "ai"
    description = "Extract the released on-device LLM model bundle for app assets."

    onlyIf { !onDeviceLlmPreparedDir.isPresent }
    dependsOn(downloadOpenVinoLlmModelBundle)
    from({ zipTree(onDeviceLlmBundleArchive.get().asFile) })
    into(onDeviceLlmBundleExtractDir)
    outputs.dir(onDeviceLlmBundleExtractDir)
}

tasks.register<Copy>("stageOpenVinoLlmAssets") {
    group = "ai"
    description = "Copy the released OpenVINO LLM model into app assets for local packaging."
    dependsOn(extractOpenVinoLlmModelBundle)
    onlyIf {
        file(resolvedOnDeviceLlmAssetSourceDir.get()).canonicalFile != onDeviceLlmAssetDir.get().asFile.canonicalFile
    }
    outputs.dir(onDeviceLlmAssetDir)

    from({ file(resolvedOnDeviceLlmAssetSourceDir.get()) })
    into(onDeviceLlmAssetDir)

    doFirst {
        onDeviceLlmAssetDir.get().asFile.deleteRecursively()
    }
}

tasks.register<Zip>("packageOpenVinoLlmModelBundle") {
    group = "ai"
    description = "Package the prepared OpenVINO LLM model bundle with its manifest hashes."
    dependsOn("prepareOpenVinoLlmModel")

    archiveFileName.set("on-device-llm-openvino-${onDeviceLlmWeightFormat.get()}.zip")
    destinationDirectory.set(onDeviceLlmBundleDir)
    from(onDeviceLlmExportDir)
}

tasks.named("preBuild") {
    dependsOn(extractOpenVinoAndroidPrebuild)
    dependsOn(stageOpenVinoRuntimeAssets)
    dependsOn("stageOpenVinoLlmAssets")
}
