package org.zotero.android.pdftranslate.data

enum class TranslateServiceType {
    // Free public endpoint, no API key required.
    google,
    // DeepL API. Free (":fx" keys) and Pro keys are both supported, the endpoint is picked from the key.
    deepl,
    // Any server implementing the OpenAI "chat/completions" API (OpenAI, OpenRouter, Ollama, LM Studio, etc.).
    openAiCompatible;

    // OpenAI compatible servers running locally (e.g. Ollama) usually don't need a key.
    val requiresApiKey: Boolean
        get() = this == deepl
}
