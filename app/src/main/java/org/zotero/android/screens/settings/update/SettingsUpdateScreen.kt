package org.zotero.android.screens.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.zotero.android.uicomponents.CustomScaffoldM3
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.themem3.AppThemeM3

@Composable
internal fun SettingsUpdateScreen(
    onBack: () -> Unit,
    viewModel: SettingsUpdateViewModel = hiltViewModel(),
) {
    AppThemeM3 {
        val viewState by viewModel.viewStates.observeAsState(SettingsUpdateViewState())
        val viewEffect by viewModel.viewEffects.observeAsState()
        LaunchedEffect(key1 = viewModel) {
            viewModel.init()
        }
        LaunchedEffect(key1 = viewEffect) {
            when (viewEffect?.consume()) {
                null -> Unit
                is SettingsUpdateViewEffect.OnBack -> onBack()
            }
        }
        CustomScaffoldM3(
            topBar = { SettingsUpdateTopBar(onBack = onBack) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(Strings.update_current_build, buildLabel(viewState.currentBuild)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val latest = viewState.latest
                if (latest != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Strings.update_latest_build, buildLabel(latest.buildNumber)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val details = listOfNotNull(
                        latest.branch,
                        latest.commit?.take(7),
                        latest.publishedAt?.replace('T', ' ')?.removeSuffix("Z"),
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                UpdateStatus(viewState = viewState, viewModel = viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(Strings.update_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpdateStatus(viewState: SettingsUpdateViewState, viewModel: SettingsUpdateViewModel) {
    when (val status = viewState.status) {
        SettingsUpdateStatus.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(Strings.update_checking))
            }
        }

        SettingsUpdateStatus.UpToDate -> {
            Text(
                text = stringResource(Strings.update_up_to_date),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = viewModel::check) {
                    Text(stringResource(Strings.update_check_again))
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Allows reinstalling the published build, e.g. after a local build.
                OutlinedButton(onClick = viewModel::downloadAndInstall) {
                    Text(stringResource(Strings.update_reinstall))
                }
            }
        }

        SettingsUpdateStatus.Available -> {
            Text(
                text = stringResource(Strings.update_available),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = viewModel::downloadAndInstall) {
                Text(stringResource(Strings.update_download_install))
            }
        }

        is SettingsUpdateStatus.Downloading -> {
            Text(stringResource(Strings.update_downloading))
            Spacer(modifier = Modifier.height(8.dp))
            if (status.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        SettingsUpdateStatus.ReadyToInstall -> {
            Text(stringResource(Strings.update_ready))
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = viewModel::downloadAndInstall) {
                Text(stringResource(Strings.update_install))
            }
        }

        SettingsUpdateStatus.NeedsInstallPermission -> {
            Text(stringResource(Strings.update_needs_permission))
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = viewModel::downloadAndInstall) {
                Text(stringResource(Strings.update_install))
            }
        }

        is SettingsUpdateStatus.Error -> {
            Text(
                text = if (status.message == "no-release") {
                    stringResource(Strings.update_no_release)
                } else {
                    stringResource(Strings.update_error, status.message)
                },
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = viewModel::check) {
                Text(stringResource(Strings.update_check_again))
            }
        }
    }
}

@Composable
private fun buildLabel(buildNumber: Int): String {
    return if (buildNumber > 0) "#$buildNumber" else stringResource(Strings.update_local_build)
}

@Composable
private fun SettingsUpdateTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        title = {
            Text(
                text = stringResource(Strings.update_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Drawables.arrow_back_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
