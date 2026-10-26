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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
        onTabSelected = viewModel::onAssistTabSelected,
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
    onTabSelected: (String) -> Unit,
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
                val tabs = listOf(ReaderTranslationState.TRANSLATE_TAB to stringResource(Strings.translate_action)) +
                        state.actions.map { it.id to it.name }
                PrimaryScrollableTabRow(
                    modifier = Modifier.weight(1f),
                    selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                ) {
                    tabs.forEach { (id, name) ->
                        Tab(
                            selected = state.selectedTab == id,
                            onClick = { onTabSelected(id) },
                            text = { Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                TargetLanguagePicker(
                    selected = state.targetLanguage,
                    onSelected = onTargetLanguageChanged,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    state.isTranslateTab -> serviceName(state.service)
                    state.selectedAction?.useContext == true -> stringResource(Strings.llm_action_label_with_context)
                    else -> stringResource(Strings.llm_action_label)
                },
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
                AssistContent(isTranslation = state.isTranslateTab, result = state.current, onRetry = onRetry)
            }

            Spacer(modifier = Modifier.height(16.dp))
            val hasTranslation = state.current.hasResult
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
                        Text(
                            stringResource(
                                if (state.isTranslateTab) {
                                    Strings.translate_underline_with_translation
                                } else {
                                    Strings.llm_action_underline_with_result
                                }
                            )
                        )
                    }
                    FilledTonalButton(
                        onClick = { onCreateAnnotation(AnnotationType.highlight) },
                        enabled = hasTranslation
                    ) {
                        Text(
                            stringResource(
                                if (state.isTranslateTab) {
                                    Strings.translate_highlight_with_translation
                                } else {
                                    Strings.llm_action_highlight_with_result
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistContent(isTranslation: Boolean, result: ReaderAssistResult, onRetry: () -> Unit) {
    val error = result.error
    val text = result.text
    when {
        result.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                if (!isTranslation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Strings.llm_action_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

        text != null -> {
            SelectionContainer {
                Text(
                    text = if (isTranslation) AnnotatedString(text) else simpleMarkdown(text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * LLMs answer in Markdown. Renders the common bits (headings, bold, bullets, inline code)
 * instead of showing raw asterisks.
 */
private fun simpleMarkdown(markdown: String): AnnotatedString {
    val inlineRegex = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`")
    return buildAnnotatedString {
        markdown.trim().lines().forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            var line = rawLine
            var isHeading = false
            val heading = Regex("^\\s{0,3}#{1,6}\\s+").find(line)
            if (heading != null) {
                line = line.substring(heading.range.last + 1)
                isHeading = true
            }
            val bullet = Regex("^(\\s*)[-*+]\\s+").find(line)
            if (bullet != null) {
                line = bullet.groupValues[1] + "• " + line.substring(bullet.range.last + 1)
            }
            val start = length
            var last = 0
            for (match in inlineRegex.findAll(line)) {
                append(line.substring(last, match.range.first))
                val bold = match.groups[1]?.value ?: match.groups[2]?.value
                if (bold != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(match.groupValues[3]) }
                }
                last = match.range.last + 1
            }
            append(line.substring(last))
            if (isHeading) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
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
