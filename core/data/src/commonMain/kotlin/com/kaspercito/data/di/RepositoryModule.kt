package com.kaspercito.data.di

import com.kaspercito.common.AppIdentity
import com.kaspercito.common.Config.SERVICE_SCOPE
import com.kaspercito.data.io.fileDir
import com.kaspercito.data.repository.AccountRepositoryImpl
import com.kaspercito.data.repository.AlbumRepositoryImpl
import com.kaspercito.data.repository.AnalyticsRepositoryImpl
import com.kaspercito.data.repository.ArtistRepositoryImpl
import com.kaspercito.data.repository.AutoEqRepositoryImpl
import com.kaspercito.data.lyrics.LyricsRomanizerRepositoryImpl
import com.kaspercito.data.repository.CommonRepositoryImpl
import com.kaspercito.data.repository.HomeRepositoryImpl
import com.kaspercito.data.repository.ImportRepositoryImpl
import com.kaspercito.data.spotify.SpotifyLibraryRepositoryImpl
import com.kaspercito.data.spotify.SpotifyTokenProvider
import com.kaspercito.data.repository.LocalPlaylistRepositoryImpl
import com.kaspercito.data.repository.LyricsCanvasRepositoryImpl
import com.kaspercito.data.repository.PlaylistRepositoryImpl
import com.kaspercito.data.repository.PodcastRepositoryImpl
import com.kaspercito.data.repository.SearchRepositoryImpl
import com.kaspercito.data.repository.SongRepositoryImpl
import com.kaspercito.data.repository.StreamRepositoryImpl
import com.kaspercito.data.repository.TasteRepositoryImpl
import com.kaspercito.data.repository.UpdateRepositoryImpl
import com.kaspercito.domain.repository.AccountRepository
import com.kaspercito.domain.repository.AlbumRepository
import com.kaspercito.domain.repository.AnalyticsRepository
import com.kaspercito.domain.repository.ArtistRepository
import com.kaspercito.domain.repository.AutoEqRepository
import com.kaspercito.domain.repository.LyricsRomanizerRepository
import com.kaspercito.domain.repository.CommonRepository
import com.kaspercito.domain.repository.HomeRepository
import com.kaspercito.domain.repository.ImportRepository
import com.kaspercito.domain.repository.SpotifyLibraryRepository
import com.kaspercito.domain.repository.LocalPlaylistRepository
import com.kaspercito.domain.repository.LyricsCanvasRepository
import com.kaspercito.domain.repository.PlaylistRepository
import com.kaspercito.domain.repository.PodcastRepository
import com.kaspercito.domain.repository.SearchRepository
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.repository.StreamRepository
import com.kaspercito.domain.repository.TasteRepository
import com.kaspercito.domain.repository.UpdateRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule =
    module {
        single<AccountRepository>(createdAtStart = true) {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository>(createdAtStart = true) {
            ArtistRepositoryImpl(get(), get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get(), get<AppIdentity>().isDevBuild)
            }
        }

        // Lazy for the same reason its client is: the picker is the only thing that wants it.
        single<AutoEqRepository> {
            AutoEqRepositoryImpl(get(), get())
        }

        // Lazy: constructing it costs a few File.length() calls (plus, while the pack is missing,
        // one background read of the stored selection), but the kuromoji dictionary behind it is
        // loaded on first Japanese line, or right after a download installs it, and never at
        // startup — so this must NOT be createdAtStart, or every launch pays for a feature most
        // listeners leave off. The path is where Android keeps the downloaded ipadic pack (the
        // APK no longer bundles it); Desktop and iOS ignore it.
        single<LyricsRomanizerRepository> {
            LyricsRomanizerRepositoryImpl("${fileDir()}/kuromoji-ipadic", get())
        }

        single<HomeRepository>(createdAtStart = true) {
            HomeRepositoryImpl(get(), get())
        }

        // Lazy: nothing needs Spotify until a lyrics or library request actually asks for a token.
        single { SpotifyTokenProvider(get(), get()) }

        single<SpotifyLibraryRepository> {
            SpotifyLibraryRepositoryImpl(get(), get(), get(), get())
        }

        single<ImportRepository>(createdAtStart = true) {
            ImportRepositoryImpl(get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get())
        }

        single<LyricsCanvasRepository>(createdAtStart = true) {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository>(createdAtStart = true) {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository>(createdAtStart = true) {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository>(createdAtStart = true) {
            StreamRepositoryImpl(get(), get())
        }

        single<UpdateRepository>(createdAtStart = true) {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository>(createdAtStart = true) {
            AnalyticsRepositoryImpl(get())
        }

        // Lazy: only the taste card in Library asks for it.
        single<TasteRepository> {
            TasteRepositoryImpl(get(), get())
        }
    }