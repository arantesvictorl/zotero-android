package org.zotero.android.pdftranslate.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateResult
import org.zotero.android.pdftranslate.data.TranslateSettings
import java.io.IOException

interface TranslateService {
    /** Blocking call, must be invoked from a background dispatcher. */
    fun translate(text: String, settings: TranslateSettings): TranslateResult
}

internal fun OkHttpClient.executeForBody(request: Request): String {
    val response = try {
        newCall(request).execute()
    } catch (e: IOException) {
        throw TranslateError.Network(e)
    }
    response.use {
        val body = it.body.string()
        if (!it.isSuccessful) {
            throw TranslateError.Http(code = it.code, body = body)
        }
        return body
    }
}
