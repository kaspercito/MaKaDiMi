package com.kaspercito.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kaspercito.domain.repository.SpotifyFailure
import com.kaspercito.domain.repository.SpotifyImportProgress
import com.kaspercito.domain.repository.SpotifyPlaylistInfo
import com.kaspercito.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.kaspercito.simpmusic.viewModel.SpotifyImportUi
import com.kaspercito.simpmusic.viewModel.SpotifyLibraryViewModel
import com.kaspercito.simpmusic.viewModel.SpotifyPlaylistsState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.log_in_to_spotify
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.spotify_error
import simpmusic.composeapp.generated.resources.spotify_import
import simpmusic.composeapp.generated.resources.spotify_import_done
import simpmusic.composeapp.generated.resources.spotify_import_error
import simpmusic.composeapp.generated.resources.spotify_import_hint
import simpmusic.composeapp.generated.resources.spotify_import_none
import simpmusic.composeapp.generated.resources.spotify_importing_title
import simpmusic.composeapp.generated.resources.spotify_liked_songs
import simpmusic.composeapp.generated.resources.spotify_login_needed
import simpmusic.composeapp.generated.resources.spotify_matching_tracks
import simpmusic.composeapp.generated.resources.spotify_no_playlists
import simpmusic.composeapp.generated.resources.spotify_not_found_list
import simpmusic.composeapp.generated.resources.spotify_rate_limited
import simpmusic.composeapp.generated.resources.spotify_reading_tracks
import simpmusic.composeapp.generated.resources.spotify_session_expired
import simpmusic.composeapp.generated.resources.spotify_tracks_count

/**
 * The Spotify chip of the library: the account's playlists, each importable into a local playlist.
 *
 * Spotify tracks cannot be played directly — the app plays from YouTube Music — so "import" finds
 * every song there and saves them as an ordinary local playlist, which then appears under
 * "Your playlists" and plays, downloads and syncs like any other.
 */
@Composable
fun SpotifyPlaylistsTab(
    navController: NavController,
    contentPadding: PaddingValues,
    viewModel: SpotifyLibraryViewModel = koinViewModel(),
) {
    val loggedIn by viewModel.spotifyLoggedIn.collectAsStateWithLifecycle(initialValue = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importUi by viewModel.importState.collectAsStateWithLifecycle()
    val likedSongsName = stringResource(Res.string.spotify_liked_songs)

    LaunchedEffect(loggedIn) {
        if (loggedIn) viewModel.load()
    }

    Column(Modifier.fillMaxSize()) {
        when {
            !loggedIn ->
                Message(
                    contentPadding,
                    stringResource(Res.string.spotify_login_needed),
                    stringResource(Res.string.log_in_to_spotify),
                ) { navController.navigate(SpotifyLoginDestination) }

            else ->
                when (val current = state) {
                    SpotifyPlaylistsState.Loading ->
                        Column(
                            Modifier.fillMaxSize().padding(contentPadding),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { CircularProgressIndicator() }

                    is SpotifyPlaylistsState.Failed ->
                        if (current.reason == SpotifyFailure.NOT_LOGGED_IN || current.reason == SpotifyFailure.SESSION_EXPIRED) {
                            Message(
                                contentPadding,
                                stringResource(Res.string.spotify_session_expired),
                                stringResource(Res.string.log_in_to_spotify),
                            ) { navController.navigate(SpotifyLoginDestination) }
                        } else {
                            Message(
                                contentPadding,
                                stringResource(
                                    if (current.reason == SpotifyFailure.RATE_LIMITED) {
                                        Res.string.spotify_rate_limited
                                    } else {
                                        Res.string.spotify_error
                                    },
                                ),
                                stringResource(Res.string.retry),
                            ) { viewModel.load(force = true) }
                        }

                    is SpotifyPlaylistsState.Loaded ->
                        if (current.playlists.isEmpty()) {
                            Message(contentPadding, stringResource(Res.string.spotify_no_playlists), stringResource(Res.string.retry)) {
                                viewModel.load(force = true)
                            }
                        } else {
                            LazyColumn(
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                item {
                                    Text(
                                        stringResource(Res.string.spotify_import_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                items(current.playlists, key = { it.id }) { playlist ->
                                    val name = if (playlist.isLikedSongs) likedSongsName else playlist.name
                                    PlaylistRow(playlist, name) { viewModel.import(playlist, name) }
                                }
                                item { Spacer(Modifier.height(96.dp)) }
                            }
                        }
                }
        }
    }

    importUi?.let { ImportDialog(it, onDismiss = viewModel::dismissImport) }
}

@Composable
private fun Message(
    padding: PaddingValues,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PlaylistRow(
    playlist: SpotifyPlaylistInfo,
    name: String,
    onImport: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = playlist.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            val subtitle =
                buildList {
                    if (playlist.trackCount > 0) add(stringResource(Res.string.spotify_tracks_count, playlist.trackCount))
                    playlist.ownerName?.takeIf { !playlist.isLikedSongs }?.let { add(it) }
                }.joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onImport) { Text(stringResource(Res.string.spotify_import)) }
    }
}

@Composable
private fun ImportDialog(
    ui: SpotifyImportUi,
    onDismiss: () -> Unit,
) {
    val progress = ui.progress
    val finished = progress is SpotifyImportProgress.Success || progress is SpotifyImportProgress.Error
    AlertDialog(
        onDismissRequest = { if (finished) onDismiss() },
        title = { Text(stringResource(Res.string.spotify_importing_title, ui.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (progress) {
                    is SpotifyImportProgress.ReadingTracks -> {
                        Text(stringResource(Res.string.spotify_reading_tracks))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    is SpotifyImportProgress.Matching -> {
                        Text(stringResource(Res.string.spotify_matching_tracks, progress.processed, progress.total, progress.matched))
                        LinearProgressIndicator(
                            progress = { if (progress.total == 0) 0f else progress.processed.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is SpotifyImportProgress.Success -> {
                        Text(
                            if (progress.matched == 0) {
                                stringResource(Res.string.spotify_import_none)
                            } else {
                                stringResource(Res.string.spotify_import_done, progress.matched, progress.total, progress.playlistTitle)
                            },
                        )
                        if (progress.notFound.isNotEmpty()) {
                            Text(stringResource(Res.string.spotify_not_found_list), style = MaterialTheme.typography.labelLarge)
                            // Capped: a 300-song playlist with 40 misses must not fill the dialog.
                            Text(
                                progress.notFound.take(NOT_FOUND_SHOWN).joinToString("\n") +
                                    if (progress.notFound.size > NOT_FOUND_SHOWN) "\n… +${progress.notFound.size - NOT_FOUND_SHOWN}" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    is SpotifyImportProgress.Error ->
                        Text(
                            stringResource(
                                when (progress.reason) {
                                    SpotifyFailure.RATE_LIMITED -> Res.string.spotify_rate_limited
                                    SpotifyFailure.UNKNOWN -> Res.string.spotify_import_error
                                    else -> Res.string.spotify_session_expired
                                },
                            ),
                        )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (finished) Res.string.ok else Res.string.cancel))
            }
        },
    )
}

private const val NOT_FOUND_SHOWN = 12
