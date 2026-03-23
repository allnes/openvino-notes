package com.itlab.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageBranchesTest {
    @Test
    fun describeThemeModeCoversBothBranches() {
        assertEquals("dynamic", CoverageBranches.describeThemeMode(dynamic = true))
        assertEquals("static", CoverageBranches.describeThemeMode(dynamic = false))
    }
}
