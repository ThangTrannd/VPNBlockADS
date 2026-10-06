package com.vpnblockads.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.vpnblockads.core.data.database.AppDatabase
import com.vpnblockads.core.data.database.CustomRuleDao
import com.vpnblockads.core.data.database.QueryLogDao
import com.vpnblockads.core.data.repository.BlocklistRepositoryImpl
import com.vpnblockads.core.data.repository.CustomRuleRepositoryImpl
import com.vpnblockads.core.data.repository.QueryLogRepositoryImpl
import com.vpnblockads.core.data.repository.SettingsRepositoryImpl
import com.vpnblockads.core.domain.repository.BlocklistRepository
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.domain.repository.QueryLogRepository
import com.vpnblockads.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope sống cùng process, cho các việc nền không gắn với màn hình nào. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun blocklist(impl: BlocklistRepositoryImpl): BlocklistRepository
    @Binds abstract fun customRules(impl: CustomRuleRepositoryImpl): CustomRuleRepository
    @Binds abstract fun queryLog(impl: QueryLogRepositoryImpl): QueryLogRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "vpnblockads.db").build()

    @Provides fun queryLogDao(db: AppDatabase): QueryLogDao = db.queryLogDao()

    @Provides fun customRuleDao(db: AppDatabase): CustomRuleDao = db.customRuleDao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
