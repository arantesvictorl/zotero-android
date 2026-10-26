package org.zotero.android.screens.reader.web

/**
 * On two column PDFs, dragging a native selection handle over the gap between columns (or slightly
 * past the end of a line) makes WebView resolve the touch point to text in the other column, so the
 * selection suddenly grows over half of the page. This script watches selection changes inside the
 * PDF view and, while the selection starts entirely in one half of the page, undoes changes that
 * move its end entirely into the other half of the same page. Full width text (titles, abstracts,
 * wide captions) and selections across pages are not affected.
 *
 * The script is installed once per page and keeps checking for new PDF views, since the reader
 * recreates its view (and iframe) when a document is reloaded.
 */
object ReaderSelectionColumnGuard {

    fun script(enabled: Boolean): String = """
        (function(enabled) {
            window.__zoteroColumnGuardEnabled = enabled;
            if (window.__zoteroColumnGuardTimer) return;

            function install() {
                var view = window._view;
                if (!view || !view._pdfPages || !view._iframeWindow) return;
                var win = view._iframeWindow;
                var doc = win.document;
                if (!doc || doc.__zoteroColumnGuardInstalled) return;
                doc.__zoteroColumnGuardInstalled = true;

                var lastValid = null;

                function elementOf(node) {
                    if (!node) return null;
                    return node.nodeType === 3 ? node.parentElement : node;
                }

                function spanOf(node) {
                    var element = elementOf(node);
                    var span = element && element.closest ? element.closest('.textLayer span') : null;
                    // Highlight wrappers (search results) are nested spans, use the positioned one.
                    while (span && span.parentElement && span.parentElement.closest('.textLayer span')) {
                        span = span.parentElement.closest('.textLayer span');
                    }
                    return span;
                }

                function layerOf(node) {
                    var element = elementOf(node);
                    return element && element.closest ? element.closest('.textLayer') : null;
                }

                // -1: entirely in the left half, 1: entirely in the right half, 0: crosses the middle.
                function sideOf(span, layerRect) {
                    var rect = span.getBoundingClientRect();
                    if (!rect.width) return 0;
                    var middle = layerRect.left + layerRect.width / 2;
                    if (rect.right <= middle) return -1;
                    if (rect.left >= middle) return 1;
                    return 0;
                }

                function remember(selection) {
                    lastValid = {
                        anchorNode: selection.anchorNode,
                        anchorOffset: selection.anchorOffset,
                        focusNode: selection.focusNode,
                        focusOffset: selection.focusOffset
                    };
                }

                doc.addEventListener('selectionchange', function() {
                    var selection = win.getSelection();
                    if (!window.__zoteroColumnGuardEnabled || !selection || !selection.rangeCount) {
                        lastValid = null;
                        return;
                    }
                    if (selection.isCollapsed) {
                        // Collapsed selections are transient while a handle is dragged, keep the last one.
                        return;
                    }
                    var anchorLayer = layerOf(selection.anchorNode);
                    var focusLayer = layerOf(selection.focusNode);
                    var anchorSpan = spanOf(selection.anchorNode);
                    var focusSpan = spanOf(selection.focusNode);
                    if (!anchorLayer || anchorLayer !== focusLayer || !anchorSpan || !focusSpan) {
                        remember(selection);
                        return;
                    }
                    var layerRect = anchorLayer.getBoundingClientRect();
                    var anchorSide = sideOf(anchorSpan, layerRect);
                    var focusSide = sideOf(focusSpan, layerRect);
                    var jumpedColumn = anchorSide !== 0 && focusSide !== 0 && anchorSide !== focusSide;
                    if (!jumpedColumn) {
                        remember(selection);
                        return;
                    }
                    if (!lastValid || !lastValid.anchorNode.isConnected || !lastValid.focusNode.isConnected) {
                        return;
                    }
                    try {
                        selection.setBaseAndExtent(
                            lastValid.anchorNode,
                            lastValid.anchorOffset,
                            lastValid.focusNode,
                            lastValid.focusOffset
                        );
                    } catch (e) {
                        lastValid = null;
                    }
                }, true);
            }

            install();
            window.__zoteroColumnGuardTimer = setInterval(install, 1000);
        })($enabled);
    """.trimIndent()
}
