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
        val response = activeBridge.generate(preparePrompt(prompt), maxNewTokens)
        return if (config.includeReasoningOutput) {
            response
        } else {
            stripReasoningSections(response)
        }
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
        val runtime =
            OpenVinoNativeRuntime.prepare(
                context = appContext,
                notesLibraryName = config.nativeLibraryName,
            )

        val loaded =
            runtime
                .loadBridge()
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
        if (!assetDirectoryExists(config.assetModelDir)) {
            throw MissingLlmRuntimeException(
                "OpenVINO LLM model assets are missing at assets/${config.assetModelDir}. " +
                    "Gradle should run :ai:stageOpenVinoLlmAssets during preBuild.",
            )
        }

        val assetMarker = readAssetText("${config.assetModelDir}/$MODEL_MARKER_FILE")
        val targetMarker = targetDir.resolve(MODEL_MARKER_FILE).takeIf { it.isFile }?.readText()
        if (targetDir.exists() && !targetDir.list().isNullOrEmpty() && assetMarker == targetMarker) {
            return targetDir
        }

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyAssetDirectory(config.assetModelDir, targetDir)
        File(appContext.cacheDir, config.cacheDirName).deleteRecursively()
        return targetDir
    }

    private fun assetDirectoryExists(assetPath: String): Boolean =
        try {
            !appContext.assets.list(assetPath).isNullOrEmpty()
        } catch (_: IOException) {
            false
        }

    private fun readAssetText(assetPath: String): String? =
        try {
            appContext
                .assets
                .open(assetPath)
                .bufferedReader()
                .use { it.readText() }
        } catch (_: IOException) {
            null
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

    private fun preparePrompt(prompt: String): String {
        if (config.includeReasoningOutput || prompt.isBlank()) {
            return prompt
        }

        val hint = config.disableReasoningPromptHint.trim()
        if (hint.isEmpty()) {
            return prompt
        }

        val trimmedPrompt = prompt.trimEnd()
        return if (trimmedPrompt.endsWith(hint, ignoreCase = true)) {
            prompt
        } else {
            "$trimmedPrompt\n$hint"
        }
    }

    private fun stripReasoningSections(response: String): String {
        if (response.isBlank()) {
            return response
        }

        return THINKING_TAG_REGEX
            .replace(THINKING_BLOCK_REGEX.replace(response, ""), "")
            .trim()
    }

    private companion object {
        const val MODEL_MARKER_FILE = ".openvino_llm_export_complete"
        val THINKING_BLOCK_REGEX =
            Regex(
                pattern = "<think>.*?</think>",
                options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val THINKING_TAG_REGEX = Regex("</?think>", RegexOption.IGNORE_CASE)
    }
}
