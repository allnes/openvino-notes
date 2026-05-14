package com.itlab.ai

data class OnDeviceLlmConfig(
    val modelId: String,
    val assetModelDir: String,
    val modelDirName: String,
    val device: String,
    val nativeLibraryName: String,
    val cacheDirName: String,
    val maxInputChars: Int,
    val summaryMaxNewTokens: Int,
    val tagsMaxNewTokens: Int,
    val maxTags: Int,
) {
    companion object {
        fun gemma3SmallIt(): OnDeviceLlmConfig =
            OnDeviceLlmConfig(
                modelId = "google/gemma-3-270m-it",
                assetModelDir = "models/gemma3-270m-it-openvino",
                modelDirName = "gemma3-270m-it-openvino",
                device = "CPU",
                nativeLibraryName = "notes_llm",
                cacheDirName = "openvino-genai-cache",
                maxInputChars = 6_000,
                summaryMaxNewTokens = 96,
                tagsMaxNewTokens = 48,
                maxTags = 6,
            )
    }
}
