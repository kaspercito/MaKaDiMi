package com.kaspercito.data.di.loader

import com.simpmusic.media_jvm.di.loadDesktopPlayerModule
import com.kaspercito.data.loginsync.LoginSyncHostRepositoryImpl
import com.kaspercito.data.loginsync.LoginSyncStore
import com.kaspercito.domain.repository.LoginSyncHostRepository
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

actual fun loadMediaService() {
    loadDesktopPlayerModule()
}

actual fun loadLoginSyncModule() {
    loadKoinModules(
        module {
            single { LoginSyncStore(get(), get(), get()) }
            single<LoginSyncHostRepository> { LoginSyncHostRepositoryImpl(get()) }
        },
    )
}
