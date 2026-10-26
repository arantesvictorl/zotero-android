package org.zotero.android.screens.settings.translate

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.pdftranslate.PdfTranslator
import org.zotero.android.pdftranslate.data.LlmAction
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateServiceType
import org.zotero.android.pdftranslate.data.TranslateSettings
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class SettingsTranslateViewModel @Inject constructor(
    private val defaults: Defaults,
    private val pdfTranslator: PdfTranslator,
) : BaseViewModel2<SettingsTranslateViewState, SettingsTranslateViewEffect>(SettingsTranslateViewState()) {

    private var testJob: Job? = null

    fun init() = initOnce {
        val settings = defaults.getTranslateSettings()
        updateState {
            copy(
                settings = settings,
                contextLengthText = settings.contextLength.toString(),
            )
        }
    }

    private fun update(block: TranslateSettings.() -> TranslateSettings) {
        val settings = viewState.settings.block()
        defaults.setTranslateSettings(settings)
        updateState { copy(settings = settings, testResult = null, testError = null) }
    }

    fun onServiceSelected(service: TranslateServiceType) = update { copy(service = service) }
    fun onSourceLanguageSelected(code: String) = update { copy(sourceLanguage = code) }
    fun onTargetLanguageSelected(code: String) = update { copy(targetLanguage = code) }
    fun onDeeplApiKeyChanged(value: String) = update { copy(deeplApiKey = value) }
    fun onOpenAiApiKeyChanged(value: String) = update { copy(openAiApiKey = value) }
    fun onOpenAiBaseUrlChanged(value: String) = update { copy(openAiBaseUrl = value) }
    fun onOpenAiModelChanged(value: String) = update { copy(openAiModel = value) }
    fun onOpenAiPromptChanged(value: String) = update { copy(openAiPrompt = value) }
    fun onResetOpenAiPrompt() = update { copy(openAiPrompt = TranslateSettings.DEFAULT_OPEN_AI_PROMPT) }
    fun onAddToCommentChanged(value: Boolean) = update { copy(addTranslationToAnnotationComment = value) }
    fun onCleanUpTextChanged(value: Boolean) = update { copy(cleanUpSourceText = value) }

    fun onContextLengthChanged(value: String) {
        val digits = value.filter { it.isDigit() }.take(5)
        updateState { copy(contextLengthText = digits) }
        val length = digits.toIntOrNull() ?: return
        update {
            copy(contextLength = length.coerceIn(1, TranslateSettings.MAX_CONTEXT_LENGTH))
        }
    }

    private fun updateAction(id: String, block: LlmAction.() -> LlmAction) = update {
        copy(llmActions = llmActions.map { if (it.id == id) it.block() else it })
    }

    fun onActionNameChanged(id: String, value: String) = updateAction(id) { copy(name = value) }
    fun onActionPromptChanged(id: String, value: String) = updateAction(id) { copy(prompt = value) }
    fun onActionUseContextChanged(id: String, value: Boolean) = updateAction(id) { copy(useContext = value) }

    fun onMoveAction(id: String, offset: Int) = update {
        val index = llmActions.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in llmActions.indices) {
            return@update this
        }
        val actions = llmActions.toMutableList()
        actions.add(target, actions.removeAt(index))
        copy(llmActions = actions)
    }

    fun onDeleteAction(id: String) = update { copy(llmActions = llmActions.filterNot { it.id == id }) }

    fun onAddAction(defaultName: String) = update {
        copy(llmActions = llmActions + LlmAction(name = defaultName, prompt = LlmAction.NEW_ACTION_PROMPT))
    }

    fun onResetActions() = update { copy(llmActions = LlmAction.defaults()) }

    fun onTestTapped(sampleText: String) {
        testJob?.cancel()
        updateState { copy(isTesting = true, testResult = null, testError = null) }
        testJob = viewModelScope.launch {
            try {
                val result = pdfTranslator.translate(text = sampleText, settings = viewState.settings)
                updateState { copy(isTesting = false, testResult = result.translatedText) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SettingsTranslateViewModel: test translation failed")
                updateState {
                    copy(isTesting = false, testError = e as? TranslateError ?: TranslateError.Network(e))
                }
            }
        }
    }

    fun onBack() {
        triggerEffect(SettingsTranslateViewEffect.OnBack)
    }
}

internal data class SettingsTranslateViewState(
    val settings: TranslateSettings = TranslateSettings(),
    val contextLengthText: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testError: TranslateError? = null,
) : ViewState

internal sealed class SettingsTranslateViewEffect : ViewEffect {
    object OnBack : SettingsTranslateViewEffect()
}
