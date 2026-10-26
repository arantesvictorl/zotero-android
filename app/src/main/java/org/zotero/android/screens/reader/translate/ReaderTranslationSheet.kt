package org.zotero.android.screens.reader.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zotero.android.database.objects.AnnotationType
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateLanguage
import org.zotero.android.screens.reader.ReaderViewModel
import org.zotero.android.screens.reader.ReaderViewState
import org.zotero.android.screens.settings.translate.serviceName
import org.zotero.android.uicomponents.Strings

@Composable
internal fun ReaderTranslationView(
    viewState: ReaderViewState,
    viewModel: ReaderViewModel,
) {
    val translation = viewState.translation ?: return
    ReaderTranslationSheet(
        state = translation,
        onDismiss = viewModel::dismissTranslation,
        onTargetLanguageChanged = viewModel::onTranslationTargetLanguageChanged,
        onRetry = viewModel::retryTranslation,
        onCopy = viewModel::copyTranslation,
        onCreateAnnotation = viewModel::createAnnotationWithTranslation,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderTranslationSheet(
    state: ReaderTranslationState,
    onDismiss: () -> Unit,
    onTargetLanguageChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onCreateAnnotation: (AnnotationType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Strings.translate_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TargetLanguagePicker(
                    selected = state.targetLanguage,
                    onSelected = onTargetLanguageChanged,
                )
            }
            Text(
                text = serviceName(state.service),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = state.sourceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                TranslationContent(state = state, onRetry = onRetry)
            }

            Spacer(modifier = Modifier.height(16.dp))
            val hasTranslation = state.translatedText != null && !state.isLoading
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onCopy, enabled = hasTranslation) {
                    Text(stringResource(Strings.translate_copy))
                }
                if (state.canCreateAnnotation) {
                    OutlinedButton(
                        onClick = { onCreateAnnotation(AnnotationType.underline) },
                        enabled = hasTranslation
                    ) {
                        Text(stringResource(Strings.translate_underline_with_translation))
                    }
                    FilledTonalButton(
                        onClick = { onCreateAnnotation(AnnotationType.highlight) },
                        enabled = hasTranslation
                    ) {
                        Text(stringResource(Strings.translate_highlight_with_translation))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationContent(state: ReaderTranslationState, onRetry: () -> Unit) {
    val error = state.error
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }
        }

        error != null -> {
            Column {
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(Strings.translate_retry))
                }
            }
        }

        state.translatedText != null -> {
            SelectionContainer {
                Text(
                    text = state.translatedText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TargetLanguagePicker(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringResource(Strings.translate_language_auto)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = "→ " + TranslateLanguage.displayName(selected, autoLabel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            modifier = Modifier.heightIn(max = 360.dp),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TranslateLanguage.targetCodes.forEach { code ->
                DropdownMenuItem(
                    text = { Text(TranslateLanguage.displayName(code, autoLabel)) },
                    onClick = {
                        expanded = false
                        onSelected(code)
                    }
                )
            }
        }
    }
}

@Composable
internal fun errorMessage(error: TranslateError): String {
    return when (error) {
        TranslateError.MissingApiKey -> stringResource(Strings.translate_error_missing_api_key)
        TranslateError.EmptyText -> stringResource(Strings.translate_error_empty_text)
        TranslateError.EmptyResponse -> stringResource(Strings.translate_error_empty_response)
        is TranslateError.Http -> stringResource(Strings.translate_error_http, error.code, error.body.take(300))
        is TranslateError.Network -> stringResource(Strings.translate_error_network, error.cause.message ?: error.cause.javaClass.simpleName)
    }
}
