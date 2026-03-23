package com.itlab.data

internal object CoverageBranches {
    fun describeStorageMode(cached: Boolean): String =
        if (cached) "cached" else "direct"
}
