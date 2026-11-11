package com.erpbarcode.scanner.di

import com.erpbarcode.scanner.data.repository.DocumentRepository
import com.erpbarcode.scanner.data.repository.DocumentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Note what is *not* provided here: any database.
 *
 * The work database is one file per profile and changes at runtime, so it comes from
 * `DatabaseProvider` rather than from Dagger — a `@Singleton AppDatabase` would have pinned the
 * first operator's file for the life of the process. Since 3.6.53 the reference data (locations and
 * responsibility centres) is one file per ERP installation for the same reason, and comes from
 * `SharedDatabaseProvider`.
 *
 * The migration discipline applies to every file either provider opens: **no
 * `fallbackToDestructiveMigration`, ever.**
 *
 * These databases hold recordings that exist nowhere else until they reach the ERP — a shift's
 * scanning lives there and only there. A destructive fallback turns any future version bump that
 * forgets a migration into silent, total loss of that work at app launch, with nothing shown to
 * the operator. It is also not hypothetical: the chain used to start at 10 while devices had
 * shipped on schemas 7, 8 and 9, so every one of those upgraded straight into a wipe.
 *
 * Without the fallback, a gap in the chain fails loudly at startup instead. That is a
 * worse-looking failure and a far better one: support can act on a device that won't open, and
 * cannot act on scans that quietly disappeared.
 *
 * So: the chain must stay complete. Every schema bump needs its migration added — in
 * `DatabaseProvider.openFor` for the per-profile database, and in `SharedDatabaseProvider` once the
 * reference data's shape first changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
}
