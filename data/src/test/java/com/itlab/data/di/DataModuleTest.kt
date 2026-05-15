package com.itlab.data.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DataModuleTest : KoinTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `verify dataModule dependencies`() {
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockk(relaxed = true)

        koinApplication {
            androidContext(mockk<Context>(relaxed = true))
            modules(dataModule)
        }.checkModules()
    }
}
