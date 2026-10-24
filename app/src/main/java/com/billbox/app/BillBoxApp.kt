package com.billbox.app

import android.app.Application
import com.billbox.app.data.AppDatabase
import com.billbox.app.data.Repository
import com.billbox.app.export.Exporter
import com.billbox.app.ocr.OcrEngine
import com.billbox.app.reminders.ReminderWorker
import com.billbox.app.settings.SettingsStore
import com.billbox.app.util.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-wired dependencies; small enough not to need a DI framework. */
class AppContainer(val app: Application) {
    val files = FileStore(app)
    val database = AppDatabase.build(app)
    val repository = Repository(database, files)
    val settings = SettingsStore(app)
    val ocr = OcrEngine(app)
    val exporter = Exporter(app, files)
}

class BillBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.repository.seedIfEmpty() }
        ReminderWorker.createChannel(this)
        ReminderWorker.schedule(this)
    }
}
