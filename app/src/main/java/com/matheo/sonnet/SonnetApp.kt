package com.matheo.sonnet

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonnet_settings")

class SonnetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SonnetApp
            private set
    }
}
