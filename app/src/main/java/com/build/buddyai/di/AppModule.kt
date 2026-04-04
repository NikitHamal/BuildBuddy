package com.build.buddyai.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level Hilt module.
 *
 * SettingsDataStore, SecureKeyStore, and repositories use @Inject constructor
 * and are auto-discovered by Hilt. Room database and DAOs are provided by
 * DataModule in core:data. Network layer is provided by NetworkModule in core:network.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
