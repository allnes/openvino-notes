package com.itlab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageBranchesTest {
    @Test
    fun describeStorageModeCoversBothBranches() {
        assertEquals("cached", CoverageBranches.describeStorageMode(cached = true))
        assertEquals("direct", CoverageBranches.describeStorageMode(cached = false))
    }
}
