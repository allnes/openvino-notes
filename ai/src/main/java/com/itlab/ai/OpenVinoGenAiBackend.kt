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
        val response = activeBridge.generate(preparePrompt(prompt, config), maxNewTokens)
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
        if (!appContext.assetDirectoryExists(config.assetModelDir)) {
            throw MissingLlmRuntimeException(
                "OpenVINO LLM model assets are missing at assets/${config.assetModelDir}. " +
                    "Gradle should run :ai:stageOpenVinoLlmAssets during preBuild.",
            )
        }

        val assetMarker = appContext.readAssetText("${config.assetModelDir}/$MODEL_MARKER_FILE")
        val targetMarker = targetDir.resolve(MODEL_MARKER_FILE).takeIf { it.isFile }?.readText()
        if (targetDir.exists() && !targetDir.list().isNullOrEmpty() && assetMarker == targetMarker) {
            return targetDir
        }

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        appContext.copyAssetDirectory(config.assetModelDir, targetDir)
        File(appContext.cacheDir, config.cacheDirName).deleteRecursively()
        return targetDir
    }

    private companion object {
        const val MODEL_MARKER_FILE = ".openvino_llm_export_complete"
    }
}

private fun Context.assetDirectoryExists(assetPath: String): Boolean =
    try {
        !assets.list(assetPath).isNullOrEmpty()
    } catch (_: IOException) {
        false
    }

private fun Context.readAssetText(assetPath: String): String? =
    try {
        assets
            .open(assetPath)
            .bufferedReader()
            .use { it.readText() }
    } catch (_: IOException) {
        null
    }

private fun Context.copyAssetDirectory(
    assetPath: String,
    targetDir: File,
) {
    val children =
        assets.list(assetPath)
            ?: throw MissingLlmRuntimeException("Unable to list model asset directory: $assetPath")

    children.forEach { child -> copyAssetChild(assetPath, targetDir, child) }
}

private fun Context.copyAssetChild(
    assetPath: String,
    targetDir: File,
    child: String,
) {
    val childAssetPath = "$assetPath/$child"
    val childTarget = File(targetDir, child)
    val nestedChildren = assets.list(childAssetPath)
    if (nestedChildren.isNullOrEmpty()) {
        copyAssetFile(childAssetPath, childTarget)
    } else {
        childTarget.mkdirs()
        copyAssetDirectory(childAssetPath, childTarget)
    }
}

private fun Context.copyAssetFile(
    assetPath: String,
    targetFile: File,
) {
    assets.open(assetPath).use { input ->
        targetFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

private fun preparePrompt(
    prompt: String,
    config: OnDeviceLlmConfig,
): String {
    val hint = config.disableReasoningPromptHint.trim()
    val trimmedPrompt = prompt.trimEnd()
    val shouldAppendHint =
        !config.includeReasoningOutput &&
            prompt.isNotBlank() &&
            hint.isNotEmpty() &&
            !trimmedPrompt.endsWith(hint, ignoreCase = true)

    return if (shouldAppendHint) {
        "$trimmedPrompt\n$hint"
    } else {
        prompt
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

private val THINKING_BLOCK_REGEX =
    Regex(
        pattern = "<think>.*?</think>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val THINKING_TAG_REGEX = Regex("</?think>", RegexOption.IGNORE_CASE)
