package com.itlab.domain

internal object CoverageBranches {
    fun describeValidation(valid: Boolean): String = if (valid) "valid" else "invalid"
}
