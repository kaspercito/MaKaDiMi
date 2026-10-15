package com.kaspercito.data.di

import DatabaseDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kaspercito.data.dataStore.DataStoreManagerImpl
import com.kaspercito.data.dataStore.createDataStoreInstance
import com.kaspercito.data.db.Converters
import com.kaspercito.data.db.MusicDatabase
import com.kaspercito.data.db.datasource.AnalyticsDatasource
import com.kaspercito.data.db.datasource.LocalDataSource
import com.kaspercito.data.db.getDatabaseBuilder
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.kotlinytmusicscraper.YouTube
import com.kaspercito.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import org.simpmusic.aiservice.AiClient
import org.simpmusic.lyrics.SimpMusicLyricsClient
import kotlin.time.ExperimentalTime
import org.simpmusic.autoeq.AutoEq

@OptIn(ExperimentalTime::class)
val databaseModule =
    module {
        single(createdAtStart = true) {
            Converters()
        }
        // Database
        single(createdAtStart = true) {
            getDatabaseBuilder(
                get<Converters>()
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        // DatabaseDao
        single(createdAtStart = true) {
            get<MusicDatabase>().getDatabaseDao()
        }
        // LocalDataSource
        single(createdAtStart = true) {
            LocalDataSource(get<DatabaseDao>(), get<MusicDatabase>())
        }
        // AnalyticsDatasource
        single(createdAtStart = true) {
            AnalyticsDatasource(get<DatabaseDao>())
        }
        // Datastore
        single(createdAtStart = true) {
            createDataStoreInstance()
        }
        // DatastoreManager
        single<DataStoreManager>(createdAtStart = true) {
            DataStoreManagerImpl(get<DataStore<Preferences>>())
        }

        // Move YouTube from Singleton to Koin DI
        single(createdAtStart = true) {
            YouTube()
        }

        single(createdAtStart = true) {
            Spotify()
        }

        single(createdAtStart = true) {
            AiClient()
        }

        single(createdAtStart = true) {
            SimpMusicLyricsClient()
        }

        // Not created at start, unlike the rest: nothing needs it until someone opens the AutoEq
        // picker, and it holds an HTTP client the vast majority of sessions never use.
        single {
            AutoEq()
        }
    }