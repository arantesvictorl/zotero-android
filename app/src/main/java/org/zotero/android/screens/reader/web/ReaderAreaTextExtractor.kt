package org.zotero.android.screens.reader.web

/**
 * Script that returns the PDF text inside a rectangle drawn over the reader (area selection with the
 * S Pen). Only characters whose center is inside the rectangle are taken, so text from a neighbouring
 * column is never included. The result has the same shape as the reader's text selection params
 * (`{ annotation: { text, position: { pageIndex, rects }, sortIndex, pageLabel } }`), so it can be used
 * to create highlights too. Returns `{ retry: true }` while the page text is still loading and
 * `{ error: "..." }` when there's nothing to take.
 */
object ReaderAreaTextExtractor {

    /**
     * Coordinates are in physical pixels relative to the WebView.
     */
    fun script(left: Float, top: Float, right: Float, bottom: Float): String = """
        (function(left, top, right, bottom) {
            try {
                // window._view is the reader's View wrapper, the PDF view is inside it.
                var wrapper = window._view;
                var view = wrapper && (wrapper._pdfPages ? wrapper : wrapper._view);
                if (!view || !view._pdfPages || !view._iframe || !view._iframeWindow) {
                    return { error: 'unsupported' };
                }
                var dpr = window.devicePixelRatio || 1;
                var iframeRect = view._iframe.getBoundingClientRect();
                var x1 = left / dpr - iframeRect.left;
                var y1 = top / dpr - iframeRect.top;
                var x2 = right / dpr - iframeRect.left;
                var y2 = bottom / dpr - iframeRect.top;

                var win = view._iframeWindow;
                var pageDivs = win.document.querySelectorAll('.page[data-page-number]');
                var pageDiv = null;
                var bestArea = 0;
                for (var i = 0; i < pageDivs.length; i++) {
                    var r = pageDivs[i].getBoundingClientRect();
                    var w = Math.min(x2, r.right) - Math.max(x1, r.left);
                    var h = Math.min(y2, r.bottom) - Math.max(y1, r.top);
                    if (w > 0 && h > 0 && w * h > bestArea) {
                        bestArea = w * h;
                        pageDiv = pageDivs[i];
                    }
                }
                if (!pageDiv) return { error: 'empty' };

                var pageIndex = parseInt(pageDiv.dataset.pageNumber) - 1;
                var page = view._pdfPages[pageIndex];
                if (!page || !page.chars) {
                    if (view._ensureBasicPageData) view._ensureBasicPageData(pageIndex);
                    return { retry: true };
                }
                var pageView = win.PDFViewerApplication.pdfViewer.getPageView(pageIndex);
                var viewport = pageView.viewport;
                var content = pageDiv.querySelector('.canvasWrapper') || pageDiv.querySelector('.textLayer') || pageDiv;
                var contentRect = content.getBoundingClientRect();
                // The page may be scaled with CSS while zooming, map through the actual on-screen size.
                var scaleX = viewport.width / contentRect.width;
                var scaleY = viewport.height / contentRect.height;
                var p1 = viewport.convertToPdfPoint((x1 - contentRect.left) * scaleX, (y1 - contentRect.top) * scaleY);
                var p2 = viewport.convertToPdfPoint((x2 - contentRect.left) * scaleX, (y2 - contentRect.top) * scaleY);
                var minX = Math.min(p1[0], p2[0]);
                var maxX = Math.max(p1[0], p2[0]);
                var minY = Math.min(p1[1], p2[1]);
                var maxY = Math.max(p1[1], p2[1]);

                var chars = page.chars;
                var selected = [];
                for (var c = 0; c < chars.length; c++) {
                    var rect = chars[c].rect;
                    if (!rect) continue;
                    var cx = (rect[0] + rect[2]) / 2;
                    var cy = (rect[1] + rect[3]) / 2;
                    if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                        selected.push(c);
                    }
                }
                if (!selected.length) return { error: 'empty' };

                var text = '';
                var rects = [];
                var line = null;
                for (var s = 0; s < selected.length; s++) {
                    var index = selected[s];
                    var ch = chars[index];
                    var previous = s > 0 ? selected[s - 1] : -2;
                    var continuesLine = previous === index - 1 && !chars[previous].lineBreakAfter
                        && !chars[previous].paragraphBreakAfter;
                    if (!ch.ignorable) {
                        if (text && !continuesLine && !/\s$/.test(text)) text += ' ';
                        text += ch.c;
                        if (ch.spaceAfter || ch.lineBreakAfter || ch.paragraphBreakAfter) text += ' ';
                    }
                    if (line && continuesLine) {
                        line[0] = Math.min(line[0], ch.rect[0]);
                        line[1] = Math.min(line[1], ch.rect[1]);
                        line[2] = Math.max(line[2], ch.rect[2]);
                        line[3] = Math.max(line[3], ch.rect[3]);
                    } else {
                        if (line) rects.push(line);
                        line = ch.rect.slice(0, 4);
                    }
                }
                if (line) rects.push(line);
                text = text.trim();
                if (!text) return { error: 'empty' };

                rects = rects.map(function(r) {
                    return r.map(function(v) { return Math.round(v * 1000) / 1000; });
                });
                var position = { pageIndex: pageIndex, rects: rects };
                var meta = view.getAnnotationMeta ? view.getAnnotationMeta(position) : {};
                return {
                    annotation: {
                        type: 'highlight',
                        text: text,
                        position: position,
                        sortIndex: meta.sortIndex,
                        pageLabel: meta.pageLabel
                    }
                };
            } catch (e) {
                return { error: String(e) };
            }
        })($left, $top, $right, $bottom);
    """.trimIndent()
}
