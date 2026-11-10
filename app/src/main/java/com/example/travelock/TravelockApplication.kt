package com.example.travelock

import android.app.Application
import com.example.travelock.util.DataStoreManager

class TravelockApplication : Application() {
    lateinit var dataStoreManager: DataStoreManager
        private set

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(applicationContext)
    }
}

