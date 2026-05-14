package com.itlab.ai

interface LlmInferenceBackend {
    fun generate(
        prompt: String,
        maxNewTokens: Int,
    ): String
}

class MissingLlmRuntimeException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class UnavailableLlmBackend(
    private val reason: String = "OpenVINO GenAI backend is not configured.",
) : LlmInferenceBackend {
    override fun generate(
        prompt: String,
        maxNewTokens: Int,
    ): String = throw MissingLlmRuntimeException(reason)
}
