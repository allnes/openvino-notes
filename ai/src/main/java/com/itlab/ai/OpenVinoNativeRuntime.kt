package com.itlab.ai

import android.content.Context
import java.io.File

internal class OpenVinoNativeRuntime private constructor(
    private val runtimeDir: File,
    private val notesLibrary: File,
) {
    fun loadBridge(): Result<NativeLlmBridge> =
        runCatching {
            preferredLibraryLoadOrder
                .map { File(runtimeDir, it) }
                .filter { it.isFile }
                .forEach { System.load(it.absolutePath) }
            System.load(notesLibrary.absolutePath)
            NativeLlmBridge()
        }

    companion object {
        private const val RUNTIME_ASSET_DIR = "openvino-runtime"

        private val preferredLibraryLoadOrder =
            listOf(
                "libc++_shared.so",
                "libtbb.so",
                "libtbbmalloc.so",
                "libtbbmalloc_proxy.so",
                "libopenvino.so",
                "libopenvino_c.so",
                "libopenvino_ir_frontend.so",
                "libopenvino_tokenizers.so",
                "libopenvino_arm_cpu_plugin.so",
                "libopenvino_auto_plugin.so",
                "libopenvino_hetero_plugin.so",
                "libopenvino_auto_batch_plugin.so",
                "libopenvino_genai.so",
                "libopenvino_genai_c.so",
            )

        fun prepare(
            context: Context,
            notesLibraryName: String,
        ): OpenVinoNativeRuntime {
            val appContext = context.applicationContext
            val nativeLibraryDir =
                File(appContext.applicationInfo.nativeLibraryDir)
                    .takeIf { it.isDirectory }
                    ?: throw MissingLlmRuntimeException(
                        "Android native library directory is not available. " +
                            "The app must use legacy JNI packaging for OpenVINO GenAI.",
                    )
            val notesLibrary = nativeLibraryDir.resolve("lib$notesLibraryName.so")
            if (!notesLibrary.isFile) {
                throw MissingLlmRuntimeException("Native LLM bridge is missing: ${notesLibrary.absolutePath}")
            }

            val runtimeDir = File(appContext.filesDir, "openvino-runtime/${nativeLibraryDir.name}")
            runtimeDir.mkdirs()
            copyOpenVinoLibraries(nativeLibraryDir, runtimeDir)
            copyAssetDirectory(appContext, RUNTIME_ASSET_DIR, runtimeDir)
            copyPluginLibrariesToVersionDirs(runtimeDir)
            return OpenVinoNativeRuntime(runtimeDir, notesLibrary)
        }

        private fun copyOpenVinoLibraries(
            nativeLibraryDir: File,
            runtimeDir: File,
        ) {
            val libraryFiles =
                nativeLibraryDir.listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(".so") &&
                        (
                            file.name.startsWith("libopenvino") ||
                                file.name.startsWith("libtbb") ||
                                file.name == "libc++_shared.so"
                        )
                }
            val libraries = libraryFiles?.toList().orEmpty()
            if (libraries.none { it.name == "libopenvino.so" }) {
                throw MissingLlmRuntimeException("OpenVINO runtime library is missing in $nativeLibraryDir")
            }
            if (libraries.none { it.name == "libopenvino_genai.so" }) {
                throw MissingLlmRuntimeException("OpenVINO GenAI library is missing in $nativeLibraryDir")
            }

            libraries.forEach { source ->
                source.copyToIfChanged(runtimeDir.resolve(source.name))
            }
        }

        private fun copyPluginLibrariesToVersionDirs(runtimeDir: File) {
            val pluginLibraryFiles =
                runtimeDir.listFiles { file ->
                    file.isFile &&
                        file.name.startsWith("libopenvino_") &&
                        file.name.endsWith("_plugin.so")
                }
            val pluginLibraries = pluginLibraryFiles?.toList().orEmpty()
            if (pluginLibraries.isEmpty()) {
                return
            }

            runtimeDir
                .listFiles { file -> file.isDirectory && file.name.startsWith("openvino-") }
                ?.forEach { pluginDir ->
                    pluginLibraries.forEach { library ->
                        library.copyToIfChanged(pluginDir.resolve(library.name))
                    }
                }
        }

        private fun copyAssetDirectory(
            context: Context,
            assetPath: String,
            targetDir: File,
        ) {
            val children =
                context.assets.list(assetPath)
                    ?: throw MissingLlmRuntimeException("Unable to list runtime asset directory: $assetPath")
            if (children.isEmpty()) {
                throw MissingLlmRuntimeException("OpenVINO runtime assets are missing at assets/$assetPath")
            }

            children.forEach { child ->
                val childAssetPath = "$assetPath/$child"
                val childTarget = targetDir.resolve(child)
                val nestedChildren = context.assets.list(childAssetPath)
                if (nestedChildren.isNullOrEmpty()) {
                    context.assets.open(childAssetPath).use { input ->
                        childTarget.parentFile?.mkdirs()
                        childTarget.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    childTarget.mkdirs()
                    copyAssetDirectory(context, childAssetPath, childTarget)
                }
            }
        }

        private fun File.copyToIfChanged(target: File) {
            if (target.isFile && target.length() == length()) {
                return
            }

            target.parentFile?.mkdirs()
            copyTo(target, overwrite = true)
        }
    }
}
