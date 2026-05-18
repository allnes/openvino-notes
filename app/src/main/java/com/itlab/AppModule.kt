package com.itlab

import com.itlab.domain.usecase.aiusecase.ReleaseNoteAiUseCase
import com.itlab.domain.usecase.aiusecase.RewriteNoteUseCase
import com.itlab.domain.usecase.aiusecase.SuggestSummaryUseCase
import com.itlab.domain.usecase.aiusecase.SuggestTagsUseCase
import com.itlab.domain.usecase.aiusecase.WarmUpNoteAiUseCase
import com.itlab.domain.usecase.folderusecase.CreateFolderUseCase
import com.itlab.domain.usecase.folderusecase.DeleteFolderUseCase
import com.itlab.domain.usecase.folderusecase.GetFolderUseCase
import com.itlab.domain.usecase.folderusecase.ObserveFoldersUseCase
import com.itlab.domain.usecase.folderusecase.UpdateFolderUseCase
import com.itlab.domain.usecase.noteusecase.ApplyRewriteUseCase
import com.itlab.domain.usecase.noteusecase.ApplySummaryUseCase
import com.itlab.domain.usecase.noteusecase.ApplyTagsUseCase
import com.itlab.domain.usecase.noteusecase.CreateNoteUseCase
import com.itlab.domain.usecase.noteusecase.DeleteNoteUseCase
import com.itlab.domain.usecase.noteusecase.GetAllFavoritesUseCase
import com.itlab.domain.usecase.noteusecase.GetNoteUseCase
import com.itlab.domain.usecase.noteusecase.GetUserIdUseCase
import com.itlab.domain.usecase.noteusecase.MoveNoteToFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesByFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesUseCase
import com.itlab.domain.usecase.noteusecase.SearchNotesUseCase
import com.itlab.domain.usecase.noteusecase.SwitchFavoriteUseCase
import com.itlab.domain.usecase.noteusecase.UpdateNoteUseCase
import com.itlab.domain.usecase.noteusecase.ValidateDuplicateNoteTitleUseCase
import com.itlab.notes.auth.AppSessionPreferences
import com.itlab.notes.auth.ClearLocalDataOnSignOut
import com.itlab.notes.onboarding.OnboardingPreferences
import com.itlab.notes.onboarding.OnboardingViewModel
import com.itlab.notes.ui.NotesUseCases
import com.itlab.notes.ui.NotesViewModel
import com.itlab.notes.ui.auth.AuthViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        single { OnboardingPreferences(androidApplication()) }
        single { AppSessionPreferences(androidApplication()) }
        factory { ValidateDuplicateNoteTitleUseCase(get()) }
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
        factory { GetUserIdUseCase(get()) }
        factory { SearchNotesUseCase(get()) }
        factory { SwitchFavoriteUseCase(get()) }
        factory { GetAllFavoritesUseCase(get()) }
        factory { GetNoteUseCase(get()) }
        factory { SuggestSummaryUseCase(get(), get()) }
        factory { SuggestTagsUseCase(get(), get()) }
        factory { RewriteNoteUseCase(get(), get()) }
        factory { WarmUpNoteAiUseCase(get()) }
        factory { ReleaseNoteAiUseCase(get()) }
        factory { ApplySummaryUseCase(get()) }
        factory { ApplyTagsUseCase(get()) }
        factory { ApplyRewriteUseCase(get()) }
        factory { UpdateFolderUseCase(get()) }
        factory { GetFolderUseCase(get()) }
        factory {
            ClearLocalDataOnSignOut(
                observeNotesUseCase = get(),
                deleteNoteUseCase = get(),
                observeFoldersUseCase = get(),
                deleteFolderUseCase = get(),
            )
        }
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
                getUserIdUseCase = get(),
                searchNotesUseCase = get(),
                switchFavoriteUseCase = get(),
                getAllFavoritesUseCase = get(),
                getNoteUseCase = get(),
                suggestSummaryUseCase = get(),
                suggestTagsUseCase = get(),
                rewriteNoteUseCase = get(),
                warmUpNoteAiUseCase = get(),
                releaseNoteAiUseCase = get(),
                applySummaryUseCase = get(),
                applyTagsUseCase = get(),
                applyRewriteUseCase = get(),
            )
        }

        viewModelOf(::NotesViewModel)
        viewModelOf(::OnboardingViewModel)
        viewModel {
            AuthViewModel(
                firebaseAuth = get(),
                app = androidApplication(),
                appSessionPreferences = get(),
                clearLocalDataOnSignOut = get(),
            )
        }
    }
