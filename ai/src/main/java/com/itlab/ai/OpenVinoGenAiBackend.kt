package com.itlab.ai

import android.content.Context
import java.io.File
import java.io.IOException

class OpenVinoGenAiBackend(
    context: Context,
    private val config: OnDeviceLlmConfig = OnDeviceLlmConfig.defaultAndroid(),
) : LlmInferenceBackend,
    AutoCloseable {
    private val appContext = context.applicationContext
    private var bridge: NativeLlmBridge? = null

    @Synchronized
    override fun generate(
        prompt: String,
        maxNewTokens: Int,
    ): String {
        val activeBridge = bridge ?: createBridge()
        return activeBridge.generate(prompt, maxNewTokens)
    }

    @Synchronized
    override fun close() {
        bridge?.close()
        bridge = null
    }

    private fun createBridge(): NativeLlmBridge {
        val modelDir = ensureModelDirectory()
        val cacheDir =
            File(appContext.cacheDir, config.cacheDirName)
                .apply { mkdirs() }

        val loaded =
            NativeLlmBridge
                .load(config.nativeLibraryName)
                .getOrElse { cause ->
                    throw MissingLlmRuntimeException(
                        "OpenVINO GenAI native library '${config.nativeLibraryName}' is not packaged.",
                        cause,
                    )
                }

        loaded.init(
            modelDir = modelDir.absolutePath,
            cacheDir = cacheDir.absolutePath,
            device = config.device,
        )
        bridge = loaded
        return loaded
    }

    private fun ensureModelDirectory(): File {
        val targetDir = File(appContext.filesDir, "models/${config.modelDirName}")
        if (targetDir.exists() && !targetDir.list().isNullOrEmpty()) {
            return targetDir
        }

        if (!assetDirectoryExists(config.assetModelDir)) {
            throw MissingLlmRuntimeException(
                "OpenVINO LLM model assets are missing at assets/${config.assetModelDir}. " +
                    "Gradle should run :ai:stageOpenVinoLlmAssets during preBuild.",
            )
        }

        targetDir.mkdirs()
        copyAssetDirectory(config.assetModelDir, targetDir)
        return targetDir
    }

    private fun assetDirectoryExists(assetPath: String): Boolean =
        try {
            !appContext.assets.list(assetPath).isNullOrEmpty()
        } catch (_: IOException) {
            false
        }

    private fun copyAssetDirectory(
        assetPath: String,
        targetDir: File,
    ) {
        val children =
            appContext.assets.list(assetPath)
                ?: throw MissingLlmRuntimeException("Unable to list model asset directory: $assetPath")

        children.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            val nestedChildren = appContext.assets.list(childAssetPath)
            if (nestedChildren.isNullOrEmpty()) {
                appContext.assets.open(childAssetPath).use { input ->
                    childTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                childTarget.mkdirs()
                copyAssetDirectory(childAssetPath, childTarget)
            }
        }
    }
}
