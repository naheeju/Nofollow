package com.nofollow.app.di

import com.nofollow.app.features.kepo.data.repository.KepoRepositoryImpl
import com.nofollow.app.features.kepo.data.settings.KepoStateStore
import com.nofollow.app.features.kepo.domain.model.KepoConfig
import com.nofollow.app.features.kepo.domain.repository.KepoRepository
import com.nofollow.app.features.kepo.domain.usecase.SyncKepoUseCase
import com.nofollow.app.features.kepo.ui.KepoViewModel
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.TrackerDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val KEPO_AVATARS = "kepoAvatars"

val kepoModule = module {
    single { get<TrackerDatabase>().kepoDao() }
    single { KepoConfig() }
    single { KepoStateStore(androidContext()) }
    // Folder foto terpisah: AvatarStore.prune() milik sweep follower menghapus semua foto yang tidak ada di daftar event.
    single(named(KEPO_AVATARS)) { AvatarStore(androidContext(), get(), "avatars_kepo") }
    single<KepoRepository> {
        KepoRepositoryImpl(
            api = get(),
            db = get(),
            dao = get(),
            syncDao = get(),
            usageDao = get(),
            store = get(),
            sessionStore = get(),
            syncConfig = get(),
            config = get(),
            gate = get(),
            avatars = get(named(KEPO_AVATARS)),
            followingStore = get(),
        )
    }
    factoryOf(::SyncKepoUseCase)
    viewModelOf(::KepoViewModel)
}