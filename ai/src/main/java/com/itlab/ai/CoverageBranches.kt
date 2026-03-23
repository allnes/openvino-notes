package com.itlab.ai

internal object CoverageBranches {
    fun describeConfidence(confident: Boolean): String =
        if (confident) "confident" else "tentative"
}
