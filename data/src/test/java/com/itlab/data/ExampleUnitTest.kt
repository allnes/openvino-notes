package com.itlab.data

import com.itlab.data.repository.NotesRepositoryImpl
import com.itlab.data.storage.FileStorage
import com.itlab.data.storage.JsonMapper
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun createsDataComponents() {
        assertNotNull(NotesRepositoryImpl())
        assertNotNull(FileStorage())
        assertNotNull(JsonMapper())
    }
}
