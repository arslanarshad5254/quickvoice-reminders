package com.xovrix.quickvoicereminder

import android.app.Application
import com.xovrix.quickvoicereminder.billing.BillingManager
import com.xovrix.quickvoicereminder.data.AppDatabase
import com.xovrix.quickvoicereminder.data.TaskRepository
import com.xovrix.quickvoicereminder.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickVoiceApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set

    lateinit var repository: TaskRepository
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = TaskRepository(this, database.taskDao())
        billingManager = BillingManager(this)
        settingsManager = SettingsManager(this)
    }
}
