package com.nofollow.app.di

import com.nofollow.app.core.common.DefaultDispatcherProvider
import com.nofollow.app.core.common.DispatcherProvider
import com.nofollow.app.core.session.SessionStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { SessionStore(androidContext()) }
}