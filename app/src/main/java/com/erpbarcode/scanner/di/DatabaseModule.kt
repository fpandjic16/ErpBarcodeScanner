package com.erpbarcode.scanner.di

import android.content.Context
import androidx.room.Room
import com.erpbarcode.scanner.data.db.LocationDao
import com.erpbarcode.scanner.data.db.SharedDatabase
import com.erpbarcode.scanner.data.repository.DocumentRepository
import com.erpbarcode.scanner.data.repository.DocumentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    companion object {
        /**
         * Reference data for the site, shared by every operator on this device.
         *
         * Note what is *not* provided here any more: the work database. There is one of those per
         * profile and it changes at runtime, so it comes from `DatabaseProvider` rather than from
         * Dagger — a `@Singleton PrimaDatabase` would have pinned the first operator's file for
         * the life of the process.
         *
         * The migration discipline that applied to that database applies here too, and to every
         * per-profile file `DatabaseProvider` opens: **no `fallbackToDestructiveMigration`, ever.**
         *
         * These databases hold recordings that exist nowhere else until they reach the ERP — a
         * shift's scanning lives there and only there. A destructive fallback turns any future
         * version bump that forgets a migration into silent, total loss of that work at app
         * launch, with nothing shown to the operator. It is also not hypothetical: the chain used
         * to start at 10 while devices had shipped on schemas 7, 8 and 9, so every one of those
         * upgraded straight into a wipe.
         *
         * Without the fallback, a gap in the chain fails loudly at startup instead. That is a
         * worse-looking failure and a far better one: support can act on a device that won't open,
         * and cannot act on scans that quietly disappeared.
         *
         * So: the chain must stay complete. Every schema bump needs its migration added — here
         * for this database, and in `DatabaseProvider.openFor` for the per-profile one.
         */
        @Provides
        @Singleton
        fun provideSharedDatabase(@ApplicationContext context: Context): SharedDatabase =
            Room.databaseBuilder(
                context,
                SharedDatabase::class.java,
                "prima_shared.db",
            ).build()

        @Provides
        @Singleton
        fun provideLocationDao(db: SharedDatabase): LocationDao = db.locationDao()
    }
}
