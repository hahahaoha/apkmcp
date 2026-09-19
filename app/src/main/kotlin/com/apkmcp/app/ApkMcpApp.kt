package com.apkmcp.app

import android.app.Application
import android.content.Context
import com.apkmcp.app.core.Prefs

class ApkMcpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        Prefs.init(this)
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
