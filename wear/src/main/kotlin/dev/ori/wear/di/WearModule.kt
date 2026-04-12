package dev.ori.wear.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the Wear module.
 *
 * Currently empty: WearState and WearDataSyncClient are constructor-injected
 * @Singleton classes, so Hilt provides them automatically. This module exists
 * as the explicit attachment point for any future Wear-specific bindings
 * (e.g. providing a real vs fake [com.google.android.gms.wearable.DataClient]).
 */
@Module
@InstallIn(SingletonComponent::class)
object WearModule
