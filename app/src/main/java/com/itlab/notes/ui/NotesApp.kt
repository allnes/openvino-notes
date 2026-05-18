package com.itlab.notes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.itlab.notes.onboarding.LocalOnboardingRegistrar
import com.itlab.notes.onboarding.OnboardingViewModel
import com.itlab.notes.onboarding.coachMarkOverlay
import com.itlab.notes.onboarding.welcomeOnboardingScreen
import com.itlab.notes.ui.auth.AuthViewModel
import com.itlab.notes.ui.auth.authScreen
import com.itlab.notes.ui.editor.editorScreen
import com.itlab.notes.ui.filterDirectoriesByName
import com.itlab.notes.ui.notes.NotesListActions
import com.itlab.notes.ui.notes.directoriesScreen
import com.itlab.notes.ui.notes.notesListScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun notesApp() {
    val onboardingViewModel: OnboardingViewModel = koinViewModel()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val tourSteps by onboardingViewModel.tourSteps.collectAsState()
    val tourTargetBounds by onboardingViewModel.targetBoundsState.collectAsState()

    if (!onboardingState.isReady) {
        return
    }

    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()
    if (!authState.isSessionReady) {
        return
    }
    if (!authState.isSessionActive && !authState.continueOffline) {
        authScreen(authViewModel)
        return
    }

    if (onboardingState.showWelcome) {
        welcomeOnboardingScreen(
            onFinished = onboardingViewModel::completeWelcome,
            onSkip = onboardingViewModel::skipWelcome,
        )
        return
    }

    val sessionKey = authViewModel.sessionKey ?: return
    key(sessionKey) {
        LaunchedEffect(sessionKey, onboardingState.showWelcome) {
            if (!onboardingState.showWelcome) {
                onboardingViewModel.startTourIfNeeded()
                onboardingViewModel.activateTourIfPending()
            }
        }
        CompositionLocalProvider(
            LocalOnboardingRegistrar provides { targetKey, bounds ->
                onboardingViewModel.registerTarget(targetKey, bounds)
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                notesMain(
                    authViewModel = authViewModel,
                    onboardingViewModel = onboardingViewModel,
                )
                if (onboardingState.showTour && tourSteps.isNotEmpty()) {
                    val stepIndex = onboardingState.tourStepIndex.coerceIn(0, tourSteps.lastIndex)
                    val step = tourSteps[stepIndex]
                    val screenMatches =
                        step.requiredScreen == null ||
                            step.requiredScreen == onboardingState.currentScreenKind
                    coachMarkOverlay(
                        step = step,
                        stepIndex = stepIndex,
                        stepCount = tourSteps.size,
                        targetBounds = step.targetKey?.let { tourTargetBounds[it] },
                        screenMatchesStep = screenMatches,
                        onSkip = onboardingViewModel::skipTour,
                        onBack = onboardingViewModel::previousTourStep,
                        onNext = onboardingViewModel::nextTourStep,
                    )
                }
            }
        }
    }
}

@Composable
private fun notesMain(
    authViewModel: AuthViewModel,
    onboardingViewModel: OnboardingViewModel,
) {
    val viewModel: NotesViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()
    val state = viewModel.uiState

    LaunchedEffect(state.screen, authState.isSessionActive) {
        onboardingViewModel.updateCurrentScreen(state.screen)
        onboardingViewModel.updateShowSignOutStep(authState.isSessionActive)
    }

    when (val screen = state.screen) {
        NotesUiScreen.Directories -> {
            directoriesScreen(
                directories =
                    filterDirectoriesByName(
                        directories = state.directories,
                        query = state.directorySearchQuery,
                    ),
                searchQuery = state.directorySearchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onEvent(NotesUiEvent.DirectorySearchQueryChanged(query))
                },
                onCreateDirectory = { name ->
                    viewModel.onEvent(NotesUiEvent.CreateDirectory(name))
                },
                onDeleteDirectory = { directory ->
                    viewModel.onEvent(NotesUiEvent.DeleteDirectory(directory.id))
                },
                onRenameDirectory = { directory, newName ->
                    viewModel.onEvent(NotesUiEvent.RenameDirectory(directory.id, newName))
                },
                onDirectoryClick = { directory ->
                    viewModel.onEvent(NotesUiEvent.OpenDirectory(directory))
                },
                showSignOut = authState.isSessionActive,
                onSignOut = { authViewModel.signOut() },
                showReturnToSignIn = authState.continueOffline && !authState.isSessionActive,
                onReturnToSignIn = { authViewModel.exitOfflineToSignIn() },
            )
        }

        is NotesUiScreen.DirectoryNotes -> {
            notesListScreen(
                directoryId = screen.directory.id,
                directoryName = screen.directory.name,
                notes = state.notes,
                searchQuery = state.notesSearchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onEvent(NotesUiEvent.NotesSearchQueryChanged(query))
                },
                directories = state.directories,
                actions =
                    NotesListActions(
                        onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectories) },
                        onAddNoteClick = { viewModel.onEvent(NotesUiEvent.CreateNote) },
                        onNoteDelete = { note -> viewModel.onEvent(NotesUiEvent.DeleteNote(note.id)) },
                        onNoteMove = { noteId, directoryId ->
                            viewModel.onEvent(
                                NotesUiEvent.MoveNoteToDirectory(
                                    noteId = noteId,
                                    targetDirectoryId = directoryId,
                                ),
                            )
                        },
                        onNoteClick = { note ->
                            viewModel.onEvent(NotesUiEvent.OpenNote(note))
                        },
                    ),
            )
        }

        is NotesUiScreen.NoteEditor -> {
            editorScreen(
                directoryName = screen.directory.name,
                directoryId = screen.directory.id,
                note = screen.note,
                aiState = state.aiState,
                onBack = { draft -> viewModel.onEvent(NotesUiEvent.LeaveEditor(draft)) },
                onPersist = { draft ->
                    viewModel.onEvent(NotesUiEvent.PersistNote(draft))
                },
                onSave = { updated ->
                    viewModel.onEvent(NotesUiEvent.SaveNote(updated))
                },
                onToggleFavorite = {
                    viewModel.onEvent(NotesUiEvent.ToggleNoteFavorite(screen.note.id))
                },
                onSuggestSummary = { draft ->
                    viewModel.onEvent(NotesUiEvent.SuggestSummary(draft))
                },
                onSuggestTags = { draft ->
                    viewModel.onEvent(NotesUiEvent.SuggestTags(draft))
                },
                onRewrite = { draft ->
                    viewModel.onEvent(NotesUiEvent.RewriteNote(draft))
                },
                onCancelAi = {
                    viewModel.onEvent(NotesUiEvent.CancelAiGeneration)
                },
            )
        }
    }
}
