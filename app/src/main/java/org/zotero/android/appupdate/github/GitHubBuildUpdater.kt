package org.zotero.android.appupdate.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.zotero.android.BuildConfig
import org.zotero.android.api.interceptors.HttpLoggingInterceptor
import org.zotero.android.architecture.coroutines.Dispatchers
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubBuild(
    val buildNumber: Int,
    val apkUrl: String,
    val sizeBytes: Long,
    val commit: String?,
    val branch: String?,
    val publishedAt: String?,
)

/**
 * Updates the app from the "debug-latest" pre-release published by the "Debug APK" GitHub workflow
 * of this fork. The workflow writes "build=<run number>" in the release notes and embeds the same
 * number in [BuildConfig.CI_BUILD_NUMBER], which is how a newer build is detected.
 */
@Singleton
class GitHubBuildUpdater @Inject constructor(
    private val context: Context,
    private val dispatchers: Dispatchers,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor.createInterceptor(Level.BASIC))
            .build()
    }

    val currentBuildNumber: Int
        get() = BuildConfig.CI_BUILD_NUMBER

    private val apkFile: File
        get() = File(File(context.filesDir, "updates"), "zotero-debug.apk")

    suspend fun fetchLatest(): GitHubBuild = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/tags/$RELEASE_TAG")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                throw GitHubUpdateError.NoRelease
            }
            if (!response.isSuccessful) {
                throw GitHubUpdateError.Http(response.code)
            }
            response.body.string()
        }
        val release = JsonParser.parseString(body).asJsonObject
        val notes = release.get("body")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val values = notes.lines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
        val asset = release.getAsJsonArray("assets")
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk") == true }
            ?: throw GitHubUpdateError.NoRelease
        GitHubBuild(
            buildNumber = values["build"]?.toIntOrNull() ?: 0,
            apkUrl = asset.get("browser_download_url").asString,
            sizeBytes = asset.get("size")?.asLong ?: 0L,
            commit = values["commit"],
            branch = values["branch"],
            publishedAt = release.get("published_at")?.takeIf { it.isJsonPrimitive }?.asString,
        )
    }

    /**
     * Downloads the APK, reporting progress from 0 to 1 (or -1 when the size is unknown).
     */
    suspend fun download(build: GitHubBuild, onProgress: (Float) -> Unit): File = withContext(dispatchers.io) {
        val target = apkFile
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val request = Request.Builder().url(build.apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GitHubUpdateError.Http(response.code)
            }
            val responseBody = response.body
            val total = responseBody.contentLength().takeIf { it > 0 } ?: build.sizeBytes
            responseBody.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        } else {
                            onProgress(-1f)
                        }
                    }
                }
            }
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Can't move downloaded update to ${target.absolutePath}")
        }
        target
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    /** Opens the system screen where the user allows this app to install updates. */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val RELEASE_TAG = "debug-latest"
    }
}

sealed class GitHubUpdateError : Exception() {
    object NoRelease : GitHubUpdateError()
    data class Http(val code: Int) : GitHubUpdateError()
}
