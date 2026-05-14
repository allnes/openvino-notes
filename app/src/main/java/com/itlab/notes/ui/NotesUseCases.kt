package com.itlab.notes.ui

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

data class NotesUseCases(
    val createFolderUseCase: CreateFolderUseCase,
    val deleteFolderUseCase: DeleteFolderUseCase,
    val createNoteUseCase: CreateNoteUseCase,
    val deleteNoteUseCase: DeleteNoteUseCase,
    val updateNoteUseCase: UpdateNoteUseCase,
    val observeNotesByFolderUseCase: ObserveNotesByFolderUseCase,
    val observeFoldersUseCase: ObserveFoldersUseCase,
    val updateFolderUseCase: UpdateFolderUseCase,
    val getFolderUseCase: GetFolderUseCase,
    val moveNoteToFolderUseCase: MoveNoteToFolderUseCase,
    val observeNotesUseCase: ObserveNotesUseCase,
    val suggestSummaryUseCase: SuggestSummaryUseCase,
    val suggestTagsUseCase: SuggestTagsUseCase,
    val applySummaryUseCase: ApplySummaryUseCase,
    val applyTagsUseCase: ApplyTagsUseCase,
)
