package org.zotero.android.screens.settings.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.zotero.android.pdftranslate.data.LlmAction
import org.zotero.android.pdftranslate.data.TranslateLanguage
import org.zotero.android.pdftranslate.data.TranslateServiceType
import org.zotero.android.screens.reader.translate.errorMessage
import org.zotero.android.screens.settings.elements.NewSettingsDivider
import org.zotero.android.screens.settings.elements.NewSettingsItemWithDescription
import org.zotero.android.screens.settings.elements.NewSettingsSectionTitle
import org.zotero.android.screens.settings.quickcopy.SettingsQuickCopySwitchItem
import org.zotero.android.uicomponents.CustomScaffoldM3
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.themem3.AppThemeM3

@Composable
internal fun SettingsTranslateScreen(
    onBack: () -> Unit,
    viewModel: SettingsTranslateViewModel = hiltViewModel(),
) {
    AppThemeM3 {
        val viewState by viewModel.viewStates.observeAsState(SettingsTranslateViewState())
        val viewEffect by viewModel.viewEffects.observeAsState()
        LaunchedEffect(key1 = viewModel) {
            viewModel.init()
        }

        LaunchedEffect(key1 = viewEffect) {
            when (viewEffect?.consume()) {
                null -> Unit
                is SettingsTranslateViewEffect.OnBack -> onBack()
            }
        }
        CustomScaffoldM3(
            topBar = {
                SettingsTranslateTopBar(onBack = onBack)
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsTranslateContent(viewState = viewState, viewModel = viewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsTranslateContent(
    viewState: SettingsTranslateViewState,
    viewModel: SettingsTranslateViewModel,
) {
    val settings = viewState.settings
    val autoLabel = stringResource(Strings.translate_language_auto)

    Spacer(modifier = Modifier.height(8.dp))
    NewSettingsSectionTitle(titleId = Strings.translate_settings_service)
    TranslateServiceType.entries.forEach { service ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { viewModel.onServiceSelected(service) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = settings.service == service,
                onClick = { viewModel.onServiceSelected(service) }
            )
            Text(
                text = serviceName(service),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    when (settings.service) {
        TranslateServiceType.google -> {
            HintText(stringResource(Strings.translate_settings_google_hint))
        }

        TranslateServiceType.deepl -> {
            SecretField(
                label = stringResource(Strings.translate_settings_api_key),
                value = settings.deeplApiKey,
                onValueChange = viewModel::onDeeplApiKeyChanged,
            )
            HintText(stringResource(Strings.translate_settings_deepl_hint))
        }

        TranslateServiceType.openAiCompatible -> {
            HintText(stringResource(Strings.translate_settings_uses_llm_below))
            SettingsTextField(
                label = stringResource(Strings.translate_settings_prompt),
                value = settings.openAiPrompt,
                onValueChange = viewModel::onOpenAiPromptChanged,
                singleLine = false,
            )
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = viewModel::onResetOpenAiPrompt) {
                    Text(stringResource(Strings.translate_settings_reset_prompt))
                }
            }
        }
    }

    NewSettingsDivider()
    NewSettingsSectionTitle(titleId = Strings.translate_settings_llm)
    HintText(stringResource(Strings.translate_settings_openai_hint))
    SettingsTextField(
        label = stringResource(Strings.translate_settings_base_url),
        value = settings.openAiBaseUrl,
        onValueChange = viewModel::onOpenAiBaseUrlChanged,
        keyboardType = KeyboardType.Uri,
    )
    SecretField(
        label = stringResource(Strings.translate_settings_api_key),
        value = settings.openAiApiKey,
        onValueChange = viewModel::onOpenAiApiKeyChanged,
    )
    SettingsTextField(
        label = stringResource(Strings.translate_settings_model),
        value = settings.openAiModel,
        onValueChange = viewModel::onOpenAiModelChanged,
    )

    NewSettingsDivider()
    NewSettingsSectionTitle(titleId = Strings.llm_actions_title)
    HintText(stringResource(Strings.llm_actions_hint))
    settings.llmActions.forEachIndexed { index, action ->
        key(action.id) {
            LlmActionCard(
                action = action,
                canMoveUp = index > 0,
                canMoveDown = index < settings.llmActions.lastIndex,
                viewModel = viewModel,
            )
        }
    }
    val newActionName = stringResource(Strings.llm_actions_new_name)
    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { viewModel.onAddAction(newActionName) }) {
            Text(stringResource(Strings.llm_actions_add))
        }
        TextButton(onClick = viewModel::onResetActions) {
            Text(stringResource(Strings.llm_actions_reset))
        }
    }
    SettingsTextField(
        label = stringResource(Strings.llm_actions_context_length),
        value = viewState.contextLengthText,
        onValueChange = viewModel::onContextLengthChanged,
        keyboardType = KeyboardType.Number,
    )
    HintText(stringResource(Strings.llm_actions_context_length_hint))

    NewSettingsDivider()
    NewSettingsSectionTitle(titleId = Strings.translate_settings_languages)
    LanguageItem(
        title = stringResource(Strings.translate_settings_source_language),
        selected = settings.sourceLanguage,
        codes = TranslateLanguage.sourceCodes,
        autoLabel = autoLabel,
        onSelected = viewModel::onSourceLanguageSelected,
    )
    LanguageItem(
        title = stringResource(Strings.translate_settings_target_language),
        selected = settings.targetLanguage,
        codes = TranslateLanguage.targetCodes,
        autoLabel = autoLabel,
        onSelected = viewModel::onTargetLanguageSelected,
    )

    NewSettingsDivider()
    SettingsQuickCopySwitchItem(
        title = stringResource(Strings.translate_settings_add_to_comment),
        isChecked = settings.addTranslationToAnnotationComment,
        onCheckedChange = viewModel::onAddToCommentChanged,
    )
    SettingsQuickCopySwitchItem(
        title = stringResource(Strings.translate_settings_clean_up_text),
        isChecked = settings.cleanUpSourceText,
        onCheckedChange = viewModel::onCleanUpTextChanged,
    )

    NewSettingsDivider()
    val sampleText = stringResource(Strings.translate_settings_test_sample)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = { viewModel.onTestTapped(sampleText) },
                enabled = !viewState.isTesting
            ) {
                Text(stringResource(Strings.translate_settings_test))
            }
            if (viewState.isTesting) {
                Spacer(modifier = Modifier.size(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        val testResult = viewState.testResult
        val testError = viewState.testError
        if (testResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = testResult,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (testError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage(testError),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LlmActionCard(
    action: LlmAction,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    viewModel: SettingsTranslateViewModel,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsTextField(
                label = stringResource(Strings.llm_actions_name),
                value = action.name,
                onValueChange = { viewModel.onActionNameChanged(action.id, it) },
            )
            SettingsTextField(
                label = stringResource(Strings.llm_actions_prompt),
                value = action.prompt,
                onValueChange = { viewModel.onActionPromptChanged(action.id, it) },
                singleLine = false,
            )
            SettingsQuickCopySwitchItem(
                title = stringResource(Strings.llm_actions_use_context),
                isChecked = action.useContext,
                onCheckedChange = { viewModel.onActionUseContextChanged(action.id, it) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { viewModel.onMoveAction(action.id, -1) }, enabled = canMoveUp) {
                    Text("↑")
                }
                TextButton(onClick = { viewModel.onMoveAction(action.id, 1) }, enabled = canMoveDown) {
                    Text("↓")
                }
                TextButton(onClick = { viewModel.onDeleteAction(action.id) }) {
                    Text(
                        text = stringResource(Strings.llm_actions_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(
    title: String,
    selected: String,
    codes: List<String>,
    autoLabel: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NewSettingsItemWithDescription(
            title = title,
            description = TranslateLanguage.displayName(selected, autoLabel),
            onItemTapped = { expanded = true },
        )
        DropdownMenu(
            modifier = Modifier.heightIn(max = 400.dp),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            codes.forEach { code ->
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
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun serviceName(service: TranslateServiceType): String {
    return when (service) {
        TranslateServiceType.google -> stringResource(Strings.translate_service_google)
        TranslateServiceType.deepl -> stringResource(Strings.translate_service_deepl)
        TranslateServiceType.openAiCompatible -> stringResource(Strings.translate_service_openai)
    }
}

@Composable
private fun SettingsTranslateTopBar(
    onBack: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        title = {
            Text(
                text = stringResource(Strings.translate_settings_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Drawables.arrow_back_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
