package org.zotero.android.pdftranslate.data

sealed class TranslateError : Exception() {
    object MissingApiKey : TranslateError()
    object EmptyText : TranslateError()
    object EmptyResponse : TranslateError()
    data class Http(val code: Int, val body: String) : TranslateError() {
        override val message: String
            get() = "HTTP $code: ${body.take(300)}"
    }
    data class Network(override val cause: Throwable) : TranslateError() {
        override val message: String?
            get() = cause.message
    }
}
