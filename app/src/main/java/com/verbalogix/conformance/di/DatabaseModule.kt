package com.verbalogix.conformance.di

import android.content.Context
import androidx.room.Room
import com.verbalogix.conformance.data.ConformanceDatabase
import com.verbalogix.conformance.data.ItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ConformanceDatabase =
        Room.databaseBuilder(context, ConformanceDatabase::class.java, ConformanceDatabase.NAME)
            // No fallbackToDestructiveMigration. That flag silently wipes user data
            // when a migration is missing, turning a loud, fixable build-time problem
            // into quiet data loss in the field.
            .build()

    @Provides
    fun provideItemDao(db: ConformanceDatabase): ItemDao = db.itemDao()
}
