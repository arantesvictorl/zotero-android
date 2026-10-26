package org.zotero.android.screens.settings.update

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.zotero.android.appupdate.github.GitHubBuild
import org.zotero.android.appupdate.github.GitHubBuildUpdater
import org.zotero.android.appupdate.github.GitHubUpdateError
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
internal class SettingsUpdateViewModel @Inject constructor(
    private val updater: GitHubBuildUpdater,
) : BaseViewModel2<SettingsUpdateViewState, SettingsUpdateViewEffect>(SettingsUpdateViewState()) {

    private var job: Job? = null
    private var downloadedApk: File? = null

    fun init() = initOnce {
        updateState { copy(currentBuild = updater.currentBuildNumber) }
        check()
    }

    fun check() {
        job?.cancel()
        updateState { copy(status = SettingsUpdateStatus.Checking) }
        job = viewModelScope.launch {
            try {
                val latest = updater.fetchLatest()
                updateState {
                    copy(
                        latest = latest,
                        status = if (latest.buildNumber > currentBuild) {
                            SettingsUpdateStatus.Available
                        } else {
                            SettingsUpdateStatus.UpToDate
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SettingsUpdateViewModel: can't check for updates")
                updateState { copy(status = SettingsUpdateStatus.Error(errorMessage(e))) }
            }
        }
    }

    fun downloadAndInstall() {
        val latest = viewState.latest ?: return
        val apk = downloadedApk
        if (apk != null && apk.exists()) {
            install(apk)
            return
        }
        job?.cancel()
        updateState { copy(status = SettingsUpdateStatus.Downloading(progress = 0f)) }
        job = viewModelScope.launch {
            try {
                val file = updater.download(latest) { progress ->
                    viewModelScope.launch {
                        updateState { copy(status = SettingsUpdateStatus.Downloading(progress)) }
                    }
                }
                downloadedApk = file
                updateState { copy(status = SettingsUpdateStatus.ReadyToInstall) }
                install(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SettingsUpdateViewModel: can't download update")
                updateState { copy(status = SettingsUpdateStatus.Error(errorMessage(e))) }
            }
        }
    }

    private fun install(apk: File) {
        if (!updater.canInstallPackages()) {
            updateState { copy(status = SettingsUpdateStatus.NeedsInstallPermission) }
            updater.openInstallPermissionSettings()
            return
        }
        updateState { copy(status = SettingsUpdateStatus.ReadyToInstall) }
        try {
            updater.install(apk)
        } catch (e: Exception) {
            Timber.e(e, "SettingsUpdateViewModel: can't start installer")
            updateState { copy(status = SettingsUpdateStatus.Error(errorMessage(e))) }
        }
    }

    private fun errorMessage(e: Exception): String {
        return when (e) {
            GitHubUpdateError.NoRelease -> "no-release"
            is GitHubUpdateError.Http -> "HTTP ${e.code}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    fun onBack() {
        triggerEffect(SettingsUpdateViewEffect.OnBack)
    }
}

internal sealed class SettingsUpdateStatus {
    object Checking : SettingsUpdateStatus()
    object UpToDate : SettingsUpdateStatus()
    object Available : SettingsUpdateStatus()
    data class Downloading(val progress: Float) : SettingsUpdateStatus()
    object ReadyToInstall : SettingsUpdateStatus()
    object NeedsInstallPermission : SettingsUpdateStatus()
    data class Error(val message: String) : SettingsUpdateStatus()
}

internal data class SettingsUpdateViewState(
    val currentBuild: Int = 0,
    val latest: GitHubBuild? = null,
    val status: SettingsUpdateStatus = SettingsUpdateStatus.Checking,
) : ViewState

internal sealed class SettingsUpdateViewEffect : ViewEffect {
    object OnBack : SettingsUpdateViewEffect()
}
