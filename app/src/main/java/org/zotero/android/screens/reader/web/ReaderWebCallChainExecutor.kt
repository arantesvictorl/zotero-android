package org.zotero.android.screens.reader.web

import android.graphics.RectF
import android.webkit.WebMessage
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.Result
import org.zotero.android.architecture.coroutines.Dispatchers
import org.zotero.android.screens.reader.data.ReaderDocumentData
import org.zotero.android.screens.reader.data.ReaderPage
import org.zotero.android.screens.reader.data.ReaderWebData
import org.zotero.android.screens.reader.data.ReaderWebError
import org.zotero.android.screens.reader.settings.data.PageLayoutFlowMode
import org.zotero.android.screens.reader.settings.data.PageScrollMode
import org.zotero.android.screens.reader.settings.data.PageSpreadsMode
import org.zotero.android.screens.reader.web.data.CreateReaderLocation
import org.zotero.android.screens.reader.web.data.CreateReaderViewOptions
import org.zotero.android.translator.data.WebPortResponse
import org.zotero.android.translator.helper.TranslatorHelper.encodeAsJSONForJavascript
import org.zotero.android.translator.helper.TranslatorHelper.encodeStringToBase64Binary
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

@ViewModelScoped
class ReaderWebCallChainExecutor @Inject constructor(
    dispatchers: Dispatchers,
    private val gson: Gson,
    private val observable: ReaderWebCallChainEventStream,
    private val readerWebViewHandler: ReaderWebViewHandler,
    private val defaults: Defaults,
) {

    private val limitedParallelismDispatcher =
        dispatchers.io.limitedParallelism(1)
    private var webViewExecutorScope = CoroutineScope(limitedParallelismDispatcher)

    fun start(webView: WebView, file: File) {
        try {
            val filePath = "file://" + file.absolutePath

            readerWebViewHandler.load(
                webView = webView,
                url = filePath,
                onWebViewLoadPage = ::onIndexHtmlLoaded,
                processWebViewResponses = ::receiveMessage,
            )

            Timber.i("ReaderWebCallChainExecutor: initialization succeeded")
        } catch (e: Exception) {
            observable.emitAsync(
                Result.Failure(ReaderWebError.failedToInitializeWebView)
            )
            Timber.i(e, "ReaderWebCallChainExecutor: initialization failed")
        }
    }

    private fun receiveMessage(message: WebMessage) {
        webViewExecutorScope.launch {
            val data = message.data

            val mapType = object : TypeToken<WebPortResponse>() {}.type
            val decodedBody: WebPortResponse = gson.fromJson(data, mapType)
            val handlerName = decodedBody.handlerName
            val bodyElement = decodedBody.message
            when (handlerName) {
                "textHandler" -> {
                    val data = bodyElement.asJsonObject
                    val event = data["event"].asString
                    Timber.i("ReaderWebCallChainExecutor: $event")
                    when (event) {
                        "onInitialized" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.loadDocument
                                )
                            )
                        }

                        "onInitThumbnails" -> {
                            val params = data["params"].asJsonObject
                            val thumbnailsJsonArray = params["thumbnails"].asJsonArray
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onInitThumbnails(thumbnailsJsonArray)
                                )
                            )
                        }

                        "onRenderThumbnail" -> {
                            val params = data["params"].asJsonObject
                            val thumbnailJsonObject = params["thumbnail"].asJsonObject
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onRenderThumbnail(thumbnailJsonObject)
                                )
                            )
                        }

                        "onRenderAnnotationImage" -> {
                            val params = data["params"].asJsonObject
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onRenderAnnotationImage(params)
                                )
                            )
                        }

                        "onSetPageLabels" -> {
                            val params = data["params"].asJsonObject
                            val pageLabelsJsonArray = params["pageLabels"].asJsonArray
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onSetPageLabels(pageLabelsJsonArray)
                                )
                            )
                        }

                        "onSaveAnnotations" -> {
                            val params = data["params"].asJsonObject
                            Timber.i("ReaderWebCallChainExecutor: $params")
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.saveAnnotations(params)
                                )
                            )
                        }

                        "onSetAnnotationPopup" -> {
                            val dParams = data["params"]
                            if (dParams == null
                                || dParams.isJsonNull
                                || !dParams.isJsonObject
                                || dParams.asJsonObject.isEmpty
                            ) {
                                return@launch
                            }
                            val params = dParams.asJsonObject

//                            val rectArray = params["rect"].asJsonArray.map { it.asDouble }
                            val key = params["annotation"].asJsonObject["id"].asString
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.selectAnnotationFromDocument(key)
                                )
                            )

                            //TODO send the event with rect, accommodating for nav and status bar space.
                        }

                        "onSelectAnnotations" -> {
                            val params = data["params"]
                            if (params == null || params.isJsonNull) {
                                Timber.w("ReaderWebCallChainExecutor: event $event missing params")
                                return@launch
                            }
                            val paramsObject = params.asJsonObject
                            val ids = paramsObject["ids"]
                            if (ids == null || ids.isJsonNull) {
                                Timber.w("ReaderWebCallChainExecutor: event $event missing params")
                                return@launch
                            }
                            val idsJsonArrayOfStrings = ids.asJsonArray.mapNotNull {
                                if (it.isJsonNull) {
                                    null
                                } else {
                                    it.asString
                                }
                            }
                            val key = idsJsonArrayOfStrings.firstOrNull()
                            if (key != null) {
                                val inlineTextEditing = paramsObject["inlineTextEditing"]
                                    ?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                                observable.emitAsync(
                                    Result.Success(
                                        ReaderWebData.selectAnnotationFromDocument(
                                            key,
                                            inlineTextEditing
                                        )
                                    )
                                )
                            } else {
                                observable.emitAsync(
                                    Result.Success(
                                        ReaderWebData.deselectSelectedAnnotation
                                    )
                                )
                            }
                        }

                        "onSetSelectionPopup" -> {
                            val dParams = data["params"]
                            if (dParams == null
                                || dParams.isJsonNull
                                || (dParams.isJsonObject && dParams.asJsonObject.isEmpty)
                            ) {
                                observable.emitAsync(
                                    Result.Success(
                                        ReaderWebData.clearSelectedTextParams
                                    )
                                )
                                return@launch
                            }
                            val params = dParams.asJsonObject
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.setSelectedTextParams(params)
                                )
                            )
                        }

                        "onChangeViewState" -> {
                            val params = data["params"].asJsonObject
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.setViewState(params)
                                )
                            )
                        }

                        "onOpenLink" -> {
                            val params = data["params"].asJsonObject
                            val url = params["url"].asString
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.showUrl(url)
                                )
                            )
                        }

                        "onSetOutline" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.parseOutline(data)
                                )
                            )
                        }

                        "onFindResult" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.processDocumentSearchResults(data)
                                )
                            )
                        }

                        "onBackdropTap" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.toggleInterfaceVisibility
                                )
                            )
                        }

                        "onEdgePageTurnTap" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onEdgePageTurnTap
                                )
                            )
                        }

                        "onViewContentInitialized" -> {
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.onViewContentInitialized
                                )
                            )
                        }

                        "onChangeViewStats" -> {
                            val params = data["params"].asJsonObject
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.setViewStats(params)
                                )
                            )
                        }

                        "onReadingModeEnabled" -> {
                            val params = data["params"].asJsonObject
                            val enabled = params["enabled"].asBoolean
                            observable.emitAsync(
                                Result.Success(
                                    ReaderWebData.setReadingModeEnabled(enabled)
                                )
                            )
                        }

                    }
                }

                "logHandler" -> {
                    Timber.d("JSLOG: ${bodyElement.asString}")
                }

            }
        }
    }

    private fun onIndexHtmlLoaded() {
        //no-op
    }

    suspend fun selectInDocument(key: String, centerInDocument: Boolean = true) {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("select({ key: '$key', centerInDocument: $centerInDocument });") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun show(location: Map<String, Any>) {
        val encodedPayload = encodeAsJSONForJavascript(gson = gson, data = location)

        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:navigate({ location: '${encodedPayload}' });") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun navigateBack() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:navigateBack();") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun navigateForward() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:navigateForward();") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun beginNavigation() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:beginNavigation();") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun endNavigation() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:endNavigation();") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun selectSearchResult(index: Int) {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:window._view.find({ index: ${index} });") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setTool(toolName: String, colorHex: String? = null, size: Float? = null) {
        return suspendCancellableCoroutine { cont ->
            val javascript = "setTool({ type: '$toolName', color: '$colorHex',  size: '$size' });"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun renderThumbnails(index: Int) {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("renderThumbnails([$index])") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun renderAnnotationImages(id: String) {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("renderAnnotationImages(['$id'])") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun search(term: String) {
        val encodedPayload = encodeStringToBase64Binary(term)

        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("search({ term: '${encodedPayload}' });") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun clearTool() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("clearTool();") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun clearSearch() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:window._view.find();") {
                cont.resume(Unit)
            }
        }
    }

    /**
     * Returns up to [before] characters preceding and [after] characters following [text] in the document,
     * used as context when asking the LLM to explain a selection.
     */
    suspend fun getSelectionContext(
        text: String,
        pageIndex: Int?,
        before: Int,
        after: Int,
    ): Pair<String, String>? {
        val javascript = """
            (function(selected, pageIndex, before, after) {
                try {
                    // window._view is the reader's View wrapper, the PDF/EPUB/HTML view is inside it.
                    var wrapper = window._view;
                    var view = wrapper && (wrapper._pdfPages ? wrapper : (wrapper._view || wrapper));
                    if (!view) return null;
                    var normalize = function(s) { return (s || '').replace(/\s+/g, ' ').trim(); };
                    var text = '';
                    if (view._pdfPages) {
                        var pageText = function(page) {
                            if (!page || !page.chars) return '';
                            var parts = [];
                            for (var i = 0; i < page.chars.length; i++) {
                                var ch = page.chars[i];
                                if (ch.ignorable) continue;
                                parts.push(ch.c);
                                if (ch.spaceAfter || ch.lineBreakAfter || ch.paragraphBreakAfter) parts.push(' ');
                            }
                            return parts.join('');
                        };
                        var center = pageIndex == null ? 0 : pageIndex;
                        var pages = [];
                        for (var p = Math.max(0, center - 1); p <= center + 1; p++) {
                            pages.push(pageText(view._pdfPages[p]));
                        }
                        text = pages.join(' ');
                    } else {
                        var doc = view._iframeDocument || (view._iframeWindow && view._iframeWindow.document);
                        text = doc && doc.body ? doc.body.innerText : '';
                    }
                    text = normalize(text);
                    selected = normalize(selected);
                    if (!text || !selected) return null;
                    var index = text.indexOf(selected);
                    var length = selected.length;
                    if (index < 0) {
                        var head = selected.slice(0, 50);
                        index = text.indexOf(head);
                    }
                    if (index < 0) return null;
                    return {
                        before: text.slice(Math.max(0, index - before), index),
                        after: text.slice(index + length, index + length + after)
                    };
                } catch (e) {
                    return null;
                }
            })(${gson.toJson(text)}, ${pageIndex ?: "null"}, $before, $after);
        """.trimIndent()

        val result = suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(it)
            }
        }
        return try {
            val json = JsonParser.parseString(result)
            if (!json.isJsonObject) {
                return null
            }
            val obj = json.asJsonObject
            (obj["before"]?.asString ?: "") to (obj["after"]?.asString ?: "")
        } catch (e: Exception) {
            Timber.w(e, "ReaderWebCallChainExecutor: can't parse selection context")
            null
        }
    }

    /**
     * Returns the text selection params (`{ annotation: ... }`) for the PDF text inside [rect]
     * (WebView pixels), or `{ error: "..." }` explaining why there's none.
     */
    suspend fun getTextInArea(rect: RectF): JsonObject {
        val javascript = ReaderAreaTextExtractor.script(rect.left, rect.top, rect.right, rect.bottom)
        // The page text may still be loading, the script asks to retry in that case.
        repeat(5) {
            val result = suspendCancellableCoroutine { cont ->
                readerWebViewHandler.evaluateJavascript(javascript) {
                    cont.resume(it)
                }
            }
            val json = try {
                JsonParser.parseString(result)
            } catch (e: Exception) {
                Timber.w(e, "ReaderWebCallChainExecutor: can't parse area text")
                return areaError("invalid result")
            }
            if (!json.isJsonObject) {
                return areaError("no result")
            }
            val obj = json.asJsonObject
            when {
                obj.has("annotation") -> return obj
                obj.has("retry") -> delay(300)
                else -> {
                    Timber.i("ReaderWebCallChainExecutor: no area text, ${obj["error"]}")
                    return obj
                }
            }
        }
        return areaError("page text not loaded")
    }

    private fun areaError(message: String): JsonObject {
        return JsonObject().apply { addProperty("error", message) }
    }

    suspend fun deselectText() {
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:window._view.selectAnnotations([]);") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun updateView(modifications: JsonArray, insertions: JsonArray, deletions: JsonArray) {
        val encodedDeletions = encodeAsJSONForJavascript(gson = this.gson, data = deletions)
        val encodedInsertions = encodeAsJSONForJavascript(gson = this.gson, data = insertions)
        val encodedModifications = encodeAsJSONForJavascript(gson = this.gson, data = modifications)

        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("javascript:updateAnnotations({ deletions: '${encodedDeletions}', insertions: '${encodedInsertions}', modifications: '${encodedModifications}'});") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun loadDocument(data: ReaderDocumentData, isDark: Boolean) {
        Timber.i("ReaderWebCallChainExecutor: try creating view for ${data.type}; page = ${data.page}")
        Timber.i("${data.file.absolutePath}")
        val createReaderViewOptions = CreateReaderViewOptions(
            type = data.type,
            url = "https://appassets.androidplatform.net/local/${data.file.name}",
            annotations = data.annotationsJson
        )

        val flowModeString = when (defaults.getReaderSettings().pageLayoutFlowMode) {
            PageLayoutFlowMode.PAGINATED -> {
                "paginated"
            }

            PageLayoutFlowMode.SCROLLED -> {
                "scrolled"
            }
        }

        val spreadsModeInt = when (defaults.getReaderSettings().spreadsMode) {
            PageSpreadsMode.NONE -> {
                0
            }

            PageSpreadsMode.ODD -> {
                1
            }

            PageSpreadsMode.EVEN -> {
                2
            }
        }

        val key = data.selectedAnnotationKey
        val page = data.page
        if (key != null) {
            createReaderViewOptions.location = CreateReaderLocation(annotationID = key)
        } else if (page != null) {
            when (page) {
                is ReaderPage.html -> {
                    createReaderViewOptions.viewState.scrollYPercent = page.scrollYPercent
                    createReaderViewOptions.viewState.scale = 1.0

                }

                is ReaderPage.epub -> {
                    createReaderViewOptions.viewState.cfi = page.cfi
                    createReaderViewOptions.viewState.flowMode = flowModeString
                    createReaderViewOptions.viewState.spreadMode = spreadsModeInt

                }

                is ReaderPage.pdf -> {
                    createReaderViewOptions.viewState.pageIndex = page.pageIndex
                    createReaderViewOptions.viewState.spreadMode = spreadsModeInt
                    createReaderViewOptions.viewState.scrollMode =
                        defaults.getReaderSettings().scrollMode.jsValue
                }
            }
        }

        val appearanceString = if (isDark) {
            "dark"
        } else {
            "light"
        }
        createReaderViewOptions.colorScheme = appearanceString

        val toJson = encodeAsJSONForJavascript(this.gson, createReaderViewOptions)
        val javascript = "javascript:createView('${toJson}');"
        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }

    }

    suspend fun updateInterface(isDark: Boolean) {
        val appearanceString = if (isDark) {
            "dark"
        } else {
            "light"
        }

        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("window._view.setColorScheme('$appearanceString');") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setFlowMode(flowMode: PageLayoutFlowMode) {
        val flowModeString = when (flowMode) {
            PageLayoutFlowMode.PAGINATED -> {
                "paginated"
            }

            PageLayoutFlowMode.SCROLLED -> {
                "scrolled"
            }
        }

        return suspendCancellableCoroutine { cont ->
            readerWebViewHandler.evaluateJavascript("window._view.setFlowMode('$flowModeString');") {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setSpreadMode(spreadsMode: PageSpreadsMode) {
        val spreadsModeString = when (spreadsMode) {
            PageSpreadsMode.NONE -> {
                "0"
            }

            PageSpreadsMode.ODD -> {
                "1"
            }

            PageSpreadsMode.EVEN -> {
                "2"
            }
        }

        return suspendCancellableCoroutine { cont ->
            val javascript = "window._view.setSpreadMode(${spreadsModeString.toInt()});"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setScrollMode(scrollMode: PageScrollMode) {
        return suspendCancellableCoroutine { cont ->
            val javascript = "window._view.setScrollMode(${scrollMode.jsValue});"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setSDTPack(bytesBase64: String, packVersion: Int, schemaMajorVersion: Int) {
        return suspendCancellableCoroutine { cont ->
            val javascript =
                "javascript:setSDTPack({ bytes: '$bytesBase64', packVersion: $packVersion, schemaMajorVersion: $schemaMajorVersion });"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setAppearance(
        lineHeight: Double,
        wordSpacing: Double,
        letterSpacing: Double,
        pageWidth: Int
    ) {
        return suspendCancellableCoroutine { cont ->
            val javascript =
                "javascript:setAppearance({ lineHeight: $lineHeight, wordSpacing: $wordSpacing, letterSpacing: $letterSpacing, pageWidth: $pageWidth });"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun setReadingModeEnabled(enabled: Boolean) {
        return suspendCancellableCoroutine { cont ->
            val javascript = "javascript:setReadingModeEnabled({ enabled: $enabled });"
            readerWebViewHandler.evaluateJavascript(javascript) {
                cont.resume(Unit)
            }
        }
    }

}