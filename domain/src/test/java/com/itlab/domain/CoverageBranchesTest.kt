package com.itlab.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageBranchesTest {
    @Test
    fun describeValidationCoversBothBranches() {
        assertEquals("valid", CoverageBranches.describeValidation(valid = true))
        assertEquals("invalid", CoverageBranches.describeValidation(valid = false))
    }
}
