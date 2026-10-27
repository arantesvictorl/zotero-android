package org.zotero.android.screens.reader.web

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.get
import androidx.core.view.size
import org.zotero.android.uicomponents.Strings

private const val MENU_ITEM_ID_HIGHLIGHT = Menu.FIRST + 1000
private const val MENU_ITEM_ID_UNDERLINE = Menu.FIRST + 1001
private const val MENU_ITEM_ID_TRANSLATE = Menu.FIRST + 1002
// LLM actions get consecutive ids starting from here.
private const val MENU_ITEM_ID_LLM_ACTION_FIRST = Menu.FIRST + 1100
private const val MAX_LLM_ACTIONS_IN_MENU = 50
private const val GOOGLE_TRANSLATE_PACKAGE = "com.google.android.apps.translate"

private fun isOwnMenuItem(itemId: Int): Boolean {
    return itemId == MENU_ITEM_ID_HIGHLIGHT ||
            itemId == MENU_ITEM_ID_UNDERLINE ||
            itemId == MENU_ITEM_ID_TRANSLATE ||
            itemId in MENU_ITEM_ID_LLM_ACTION_FIRST until MENU_ITEM_ID_LLM_ACTION_FIRST + MAX_LLM_ACTIONS_IN_MENU
}

class ReaderWevViewSelectActionModeCallback(
    private val context: Context,
    private val original: ActionMode.Callback,
    private val delegate: () -> ReaderSelectionActionModeDelegate?,
    private val onDestroyed: () -> Unit,
) : ActionMode.Callback2() {

    private var originalMenuPrepared = false

    // Action ids for the LLM action menu items currently shown, by position.
    private var shownLlmActionIds: List<String> = emptyList()

    private val frameworkCopyLabel: CharSequence? by lazy {
        val resId = Resources.getSystem().getIdentifier("copy", "string", "android")
        if (resId != 0) runCatching { context.getText(resId) }.getOrNull() else null
    }

    private fun isCopyItem(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.copy) {
            return true
        }
        val label = frameworkCopyLabel ?: return false
        return item.title?.toString() == label.toString()
    }
  
    /**
     * The system adds its own "Translate" entry (Google Translate's text processing action or the
     * text classifier's translate suggestion), which duplicates the reader's own translation.
     */
    private fun isSystemTranslateItem(item: MenuItem): Boolean {
        val intent = item.intent
        val targetPackage = intent?.component?.packageName ?: intent?.`package`
        if (targetPackage == GOOGLE_TRANSLATE_PACKAGE) {
            return true
        }
        val title = item.title?.toString()?.trim() ?: return false
        return title.equals(context.getString(Strings.translate_action), ignoreCase = true) ||
                title.equals("Translate", ignoreCase = true)
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        return original.onCreateActionMode(mode, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (!originalMenuPrepared) {
            original.onPrepareActionMode(mode, menu)
            originalMenuPrepared = true
        }

        val menuItems: MutableList<MenuItem> = ArrayList()
        for (i in 0..<menu.size) {
            menuItems.add(menu[i])
        }

        menu.clear()

        var orderIndex = 0

        menu.add(
            Menu.NONE,
            MENU_ITEM_ID_HIGHLIGHT,
            Menu.NONE,
            context.getString(Strings.pdf_highlight)
        )
        menu.add(
            Menu.NONE,
            MENU_ITEM_ID_UNDERLINE,
            Menu.NONE,
            context.getString(Strings.pdf_underline)
        )
        menu.add(
            Menu.NONE,
            MENU_ITEM_ID_TRANSLATE,
            Menu.NONE,
            context.getString(Strings.translate_action)
        )
        val llmActions = delegate()?.llmActions().orEmpty()
            .filter { it.name.isNotBlank() }
            .take(MAX_LLM_ACTIONS_IN_MENU)
        shownLlmActionIds = llmActions.map { it.id }
        llmActions.forEachIndexed { index, action ->
            menu.add(Menu.NONE, MENU_ITEM_ID_LLM_ACTION_FIRST + index, Menu.NONE, action.name)
        }

        for (item in menuItems) {
            if (!isOwnMenuItem(item.itemId) && !isSystemTranslateItem(item)) {
                menu.add(item.groupId, item.itemId, orderIndex++, item.title).intent = item.intent
            }
        }

        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_ITEM_ID_HIGHLIGHT -> {
                delegate()?.onHighlight()
                mode.finish()
                true
            }

            MENU_ITEM_ID_UNDERLINE -> {
                delegate()?.onUnderline()
                mode.finish()
                true
            }

            MENU_ITEM_ID_TRANSLATE -> {
                delegate()?.onTranslate()
                mode.finish()
                true
            }

            in MENU_ITEM_ID_LLM_ACTION_FIRST until MENU_ITEM_ID_LLM_ACTION_FIRST + MAX_LLM_ACTIONS_IN_MENU -> {
                val actionId = shownLlmActionIds.getOrNull(item.itemId - MENU_ITEM_ID_LLM_ACTION_FIRST)
                if (actionId != null) {
                    delegate()?.onLlmAction(actionId)
                }
                mode.finish()
                true
            }

            else -> {
                if (isCopyItem(item) && delegate()?.onCopy() == true) {
                    mode.finish()
                    true
                } else {
                    original.onActionItemClicked(mode, item)
                }
            }
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        onDestroyed()
        original.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        val originalCallback2 = original as? ActionMode.Callback2
        if (originalCallback2 != null) {
            originalCallback2.onGetContentRect(mode, view, outRect)
        } else {
            super.onGetContentRect(mode, view, outRect)
        }
    }
}
