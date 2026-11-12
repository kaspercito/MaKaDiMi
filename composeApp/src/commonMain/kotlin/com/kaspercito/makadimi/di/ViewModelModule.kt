package com.kaspercito.makadimi.di

import com.kaspercito.makadimi.viewModel.AlbumViewModel
import com.kaspercito.makadimi.utils.VersionManager
import com.kaspercito.makadimi.viewModel.AnalyticsViewModel
import com.kaspercito.makadimi.viewModel.ListenTogetherSettingsViewModel
import com.kaspercito.makadimi.viewModel.ListenTogetherViewModel
import com.kaspercito.makadimi.viewModel.ArtistViewModel
import com.kaspercito.makadimi.viewModel.BrowseViewModel
import com.kaspercito.makadimi.viewModel.HomeViewModel
import com.kaspercito.makadimi.viewModel.ImportViewModel
import com.kaspercito.makadimi.viewModel.SpotifyLibraryViewModel
import com.kaspercito.makadimi.viewModel.LibraryDynamicPlaylistViewModel
import com.kaspercito.makadimi.viewModel.LibraryViewModel
import com.kaspercito.makadimi.viewModel.LocalPlaylistViewModel
import com.kaspercito.makadimi.viewModel.LogInViewModel
import com.kaspercito.makadimi.viewModel.LoginSyncHostViewModel
import com.kaspercito.makadimi.viewModel.LoginSyncSenderViewModel
import com.kaspercito.makadimi.viewModel.MoodViewModel
import com.kaspercito.makadimi.viewModel.MoreAlbumsViewModel
import com.kaspercito.makadimi.viewModel.NotificationViewModel
import com.kaspercito.makadimi.viewModel.NowPlayingBottomSheetViewModel
import com.kaspercito.makadimi.viewModel.PlaylistViewModel
import com.kaspercito.makadimi.viewModel.PodcastViewModel
import com.kaspercito.makadimi.viewModel.RecentlySongsViewModel
import com.kaspercito.makadimi.viewModel.SearchViewModel
import com.kaspercito.makadimi.viewModel.AutoEqViewModel
import com.kaspercito.makadimi.viewModel.SettingsViewModel
import com.kaspercito.makadimi.viewModel.SharedViewModel
import com.kaspercito.makadimi.viewModel.SongSelectionViewModel
import com.kaspercito.makadimi.viewModel.TasteViewModel
import com.kaspercito.makadimi.viewModel.WrappedViewModel
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