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
        fun defaultAndroid(): OnDeviceLlmConfig =
            OnDeviceLlmConfig(
                modelId = "OpenVINO/Qwen3-0.6B-int4-ov",
                assetModelDir = "models/on-device-llm-openvino",
                modelDirName = "on-device-llm-openvino",
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
