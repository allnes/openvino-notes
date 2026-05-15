package com.itlab.notes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itlab.notes.ui.AiUiState

internal data class EditorAiPanelState(
    val summary: String?,
    val tags: Set<String>,
    val aiState: AiUiState,
)

internal data class EditorAiPanelActions(
    val onSuggestSummary: () -> Unit,
    val onSuggestTags: () -> Unit,
)

@Composable
internal fun editorAiPanel(
    state: EditorAiPanelState,
    actions: EditorAiPanelActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        editorAiActionsRow(state, actions)
        editorAiError(state.aiState.errorMessage)
        editorSummaryCard(state.summary)
        editorTagsText(state.tags)
    }
}

@Composable
private fun editorAiActionsRow(
    state: EditorAiPanelState,
    actions: EditorAiPanelActions,
) {
    val isGenerating = state.aiState.isGeneratingSummary || state.aiState.isGeneratingTags
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = actions.onSuggestSummary,
            enabled = !isGenerating,
            modifier = Modifier.weight(1f),
        ) {
            summaryButtonContent(state.aiState.isGeneratingSummary)
        }

        OutlinedButton(
            onClick = actions.onSuggestTags,
            enabled = !isGenerating,
            modifier = Modifier.weight(1f),
        ) {
            tagsButtonContent(state.aiState.isGeneratingTags)
        }
    }
}

@Composable
private fun summaryButtonContent(isGenerating: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (isGenerating) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = colors.onPrimary,
        )
    } else {
        Text("Summarize")
    }
}

@Composable
private fun tagsButtonContent(isGenerating: Boolean) {
    if (isGenerating) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Text("Suggest tags")
    }
}

@Composable
private fun editorAiError(errorMessage: String?) {
    val colors = MaterialTheme.colorScheme
    errorMessage?.let { error ->
        Text(
            text = error,
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun editorSummaryCard(summary: String?) {
    if (!summary.isNullOrBlank()) {
        val colors = MaterialTheme.colorScheme
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Summary",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = summary,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun editorTagsText(tags: Set<String>) {
    if (tags.isNotEmpty()) {
        val colors = MaterialTheme.colorScheme
        Text(
            text = "Tags: ${tags.joinToString(", ")}",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
