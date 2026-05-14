package com.itlab.notes.di

import com.itlab.domain.usecase.aiusecase.SuggestSummaryUseCase
import com.itlab.domain.usecase.aiusecase.SuggestTagsUseCase
import com.itlab.domain.usecase.folderusecase.CreateFolderUseCase
import com.itlab.domain.usecase.folderusecase.DeleteFolderUseCase
import com.itlab.domain.usecase.folderusecase.GetFolderUseCase
import com.itlab.domain.usecase.folderusecase.ObserveFoldersUseCase
import com.itlab.domain.usecase.folderusecase.UpdateFolderUseCase
import com.itlab.domain.usecase.noteusecase.ApplySummaryUseCase
import com.itlab.domain.usecase.noteusecase.ApplyTagsUseCase
import com.itlab.domain.usecase.noteusecase.CreateNoteUseCase
import com.itlab.domain.usecase.noteusecase.DeleteNoteUseCase
import com.itlab.domain.usecase.noteusecase.MoveNoteToFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesByFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesUseCase
import com.itlab.domain.usecase.noteusecase.UpdateNoteUseCase
import com.itlab.notes.ui.NotesUseCases
import com.itlab.notes.ui.NotesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        factory { CreateNoteUseCase(get()) }
        factory { CreateFolderUseCase(get()) }
        factory { DeleteFolderUseCase(get(), get()) }
        factory { DeleteNoteUseCase(get()) }
        factory { UpdateNoteUseCase(get()) }
        factory { UpdateFolderUseCase(get()) }
        factory { GetFolderUseCase(get()) }
        factory { ObserveNotesByFolderUseCase(get()) }
        factory { ObserveFoldersUseCase(get()) }
        factory { MoveNoteToFolderUseCase(get(), get()) }
        factory { ObserveNotesUseCase(get()) }
        factory { SuggestSummaryUseCase(get(), get()) }
        factory { SuggestTagsUseCase(get(), get()) }
        factory { ApplySummaryUseCase(get()) }
        factory { ApplyTagsUseCase(get()) }
        factory {
            NotesUseCases(
                createFolderUseCase = get(),
                deleteFolderUseCase = get(),
                createNoteUseCase = get(),
                deleteNoteUseCase = get(),
                updateNoteUseCase = get(),
                observeNotesByFolderUseCase = get(),
                observeFoldersUseCase = get(),
                updateFolderUseCase = get(),
                getFolderUseCase = get(),
                moveNoteToFolderUseCase = get(),
                observeNotesUseCase = get(),
                suggestSummaryUseCase = get(),
                suggestTagsUseCase = get(),
                applySummaryUseCase = get(),
                applyTagsUseCase = get(),
            )
        }

        viewModel {
            NotesViewModel(
                useCases = get(),
            )
        }
    }
