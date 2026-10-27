package org.zotero.android.screens.reader.web

import org.zotero.android.pdftranslate.data.LlmAction

interface ReaderSelectionActionModeDelegate {
    fun hasValidSelection(): Boolean
    fun onHighlight()
    fun onUnderline()
    fun onCopy(): Boolean
    fun onTranslate()
    fun llmActions(): List<LlmAction>
    fun onLlmAction(actionId: String)
}