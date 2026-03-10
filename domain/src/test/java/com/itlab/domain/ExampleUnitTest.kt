package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.AnalyzeNoteUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun createsDomainComponents() {
        val note = Note(id = "1", title = "Title", content = "Body")
        val repository: NotesRepository = object : NotesRepository {}
        val useCase = AnalyzeNoteUseCase()

        assertEquals("1", note.id)
        assertNotNull(repository)
        assertNotNull(useCase)
    }
}
