package com.itlab.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.itlab.ai.OpenVinoGenAiBackend
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class OpenVinoLlmInstrumentedTest {
    @Test(timeout = 10 * 60 * 1000)
    fun backendGeneratesTextOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prompt = "Reply with one short word: ok"

        OpenVinoGenAiBackend(context).use { backend ->
            val response = backend.generate(prompt, maxNewTokens = 8)

            assertTrue("OpenVINO LLM response must not be blank", response.isNotBlank())
        }
    }
}
