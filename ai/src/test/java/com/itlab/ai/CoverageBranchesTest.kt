package com.itlab.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageBranchesTest {
    @Test
    fun describeConfidenceCoversBothBranches() {
        assertEquals("confident", CoverageBranches.describeConfidence(confident = true))
        assertEquals("tentative", CoverageBranches.describeConfidence(confident = false))
    }
}
