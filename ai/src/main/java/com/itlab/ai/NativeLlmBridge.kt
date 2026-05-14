package com.itlab.ai

class NativeLlmBridge private constructor() : AutoCloseable {
    external fun init(
        modelDir: String,
        cacheDir: String,
        device: String,
    )

    external fun generate(
        prompt: String,
        maxNewTokens: Int,
    ): String

    external override fun close()

    companion object {
        fun load(libraryName: String): Result<NativeLlmBridge> =
            runCatching {
                System.loadLibrary(libraryName)
                NativeLlmBridge()
            }
    }
}
