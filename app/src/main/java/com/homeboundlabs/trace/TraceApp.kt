package com.homeboundlabs.trace

import android.app.Application
import androidx.room.Room
import com.homeboundlabs.trace.data.Prefs
import com.homeboundlabs.trace.data.Repository
import com.homeboundlabs.trace.data.MIGRATION_1_2
import com.homeboundlabs.trace.data.MIGRATION_2_3
import com.homeboundlabs.trace.data.MIGRATION_3_4
import com.homeboundlabs.trace.data.SeedCategories
import com.homeboundlabs.trace.data.TraceDatabase
import com.homeboundlabs.trace.quickadd.QuickAdd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TraceApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: Prefs private set
    lateinit var repo: Repository private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val db = Room.databaseBuilder(this, TraceDatabase::class.java, "trace.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(SeedCategories)
            .build()
        repo = Repository(this, db.expenses(), db.categories(), scope)
        QuickAdd.ensureChannel(this)

        scope.launch {
            // One-time cleanup of the sample data earlier builds added.
            if (prefs.hadSampleData && !prefs.sampleDataRemoved) {
                repo.deleteSampleRows()
                prefs.sampleDataRemoved = true
            }
            QuickAdd.refresh(this@TraceApp)
        }
    }
}

val android.content.Context.traceApp: TraceApp get() = applicationContext as TraceApp
