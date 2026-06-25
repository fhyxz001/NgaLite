package com.ngalite.app

import android.app.Application

class NgaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NgaApp
            private set
    }
}
