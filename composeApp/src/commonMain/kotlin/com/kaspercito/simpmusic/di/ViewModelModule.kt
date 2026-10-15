package com.kaspercito.simpmusic.di

import com.kaspercito.simpmusic.viewModel.AlbumViewModel
import com.kaspercito.simpmusic.utils.VersionManager
import com.kaspercito.simpmusic.viewModel.AnalyticsViewModel
import com.kaspercito.simpmusic.viewModel.ListenTogetherSettingsViewModel
import com.kaspercito.simpmusic.viewModel.ListenTogetherViewModel
import com.kaspercito.simpmusic.viewModel.ArtistViewModel
import com.kaspercito.simpmusic.viewModel.BrowseViewModel
import com.kaspercito.simpmusic.viewModel.HomeViewModel
import com.kaspercito.simpmusic.viewModel.ImportViewModel
import com.kaspercito.simpmusic.viewModel.SpotifyLibraryViewModel
import com.kaspercito.simpmusic.viewModel.LibraryDynamicPlaylistViewModel
import com.kaspercito.simpmusic.viewModel.LibraryViewModel
import com.kaspercito.simpmusic.viewModel.LocalPlaylistViewModel
import com.kaspercito.simpmusic.viewModel.LogInViewModel
import com.kaspercito.simpmusic.viewModel.LoginSyncHostViewModel
import com.kaspercito.simpmusic.viewModel.LoginSyncSenderViewModel
import com.kaspercito.simpmusic.viewModel.MoodViewModel
import com.kaspercito.simpmusic.viewModel.MoreAlbumsViewModel
import com.kaspercito.simpmusic.viewModel.NotificationViewModel
import com.kaspercito.simpmusic.viewModel.NowPlayingBottomSheetViewModel
import com.kaspercito.simpmusic.viewModel.PlaylistViewModel
import com.kaspercito.simpmusic.viewModel.PodcastViewModel
import com.kaspercito.simpmusic.viewModel.RecentlySongsViewModel
import com.kaspercito.simpmusic.viewModel.SearchViewModel
import com.kaspercito.simpmusic.viewModel.AutoEqViewModel
import com.kaspercito.simpmusic.viewModel.SettingsViewModel
import com.kaspercito.simpmusic.viewModel.SharedViewModel
import com.kaspercito.simpmusic.viewModel.SongSelectionViewModel
import com.kaspercito.simpmusic.viewModel.TasteViewModel
import com.kaspercito.simpmusic.viewModel.WrappedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule =
    module {
        single {
            SharedViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            SearchViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            SongSelectionViewModel(
                get(),
                get(),
            )
        }
        // Only one of these resolves on a given platform: Desktop binds the host repository, Android the
        // sender. Koin resolves dependencies when a ViewModel is created, not when it is declared.
        viewModel { LoginSyncHostViewModel(get()) }
        viewModel { LoginSyncSenderViewModel(get()) }
        viewModel {
            NowPlayingBottomSheetViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            SpotifyLibraryViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            ImportViewModel(
                get(),
            )
        }
        viewModel {
            AlbumViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AutoEqViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ArtistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            PlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LogInViewModel(
                get(),
            )
        }
        viewModel {
            PodcastViewModel(
                get(),
            )
        }
        viewModel {
            MoreAlbumsViewModel(
                get(),
            )
        }
        viewModel {
            BrowseViewModel(
                get(),
            )
        }
        viewModel {
            RecentlySongsViewModel(
                get(),
            )
        }
        viewModel {
            LocalPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            NotificationViewModel(
                get(),
            )
        }
        viewModel {
            MoodViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AnalyticsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            WrappedViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            TasteViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ListenTogetherSettingsViewModel(get())
        }
        viewModel {
            ListenTogetherViewModel(
                repository = get(),
                dataStore = get(),
                bridge = get(),
            )
        }

    }