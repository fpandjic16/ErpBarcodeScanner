package com.erpbarcode.scanner.data.export

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.erpbarcode.scanner.data.auth.UserProfileStore
import com.erpbarcode.scanner.data.db.DatabaseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dumps the signed-in operator's work to JSON, for support.
 *
 * One operator's, not the device's — there is a database per profile and this reaches only the
 * open one. `exportedBy` names whose it is, because a dump that does not say is a dump nobody can
 * interpret once it has been e-mailed somewhere.
 */
@Singleton
class DatabaseExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val provider: DatabaseProvider,
    private val profileStore: UserProfileStore,
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val db = provider.current()
        val headers    = db.documentHeaderDao().getAll()
        val lines      = db.documentLineDao().getAll()
        val recordings = db.recordingDao().getAll()

        val payload = mapOf(
            "exportedAt"      to Instant.now().toString(),
            "exportedBy"      to profileStore.currentId().orEmpty(),
            "documentHeaders" to headers,
            "documentLines"   to lines,
            "recordings"      to recordings,
        )

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.writer().use { it.write(gson.toJson(payload)) }
        }
    }
}