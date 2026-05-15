package com.itlab.ai

class NativeLlmBridge internal constructor() : AutoCloseable {
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
}
