package com.kaspercito.data.di.loader

import com.kaspercito.media3.di.loadMediaService
import com.kaspercito.data.loginsync.LoginSyncSenderRepositoryImpl
import com.kaspercito.data.loginsync.LoginSyncStore
import com.kaspercito.domain.repository.LoginSyncSenderRepository
import org.koin.core.context.loadKoinModules
import org.simpmusic.loginsync.LoginSyncClient
import org.koin.dsl.module

actual fun loadMediaService() {
    loadMediaService()
}

actual fun loadLoginSyncModule() {
    loadKoinModules(
        module {
            single { LoginSyncStore(get(), get(), get()) }
            single<LoginSyncSenderRepository> { LoginSyncSenderRepositoryImpl(get(), LoginSyncClient()) }
        },
    )
}
