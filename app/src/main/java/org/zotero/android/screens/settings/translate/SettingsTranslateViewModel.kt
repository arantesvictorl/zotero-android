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
        updateState { copy(settings = defaults.getTranslateSettings()) }
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
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testError: TranslateError? = null,
) : ViewState

internal sealed class SettingsTranslateViewEffect : ViewEffect {
    object OnBack : SettingsTranslateViewEffect()
}
