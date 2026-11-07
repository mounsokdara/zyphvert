package com.mounsokdara.zyphvert

import android.app.Application
import com.mounsokdara.zyphvert.util.DeveloperLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZyphvertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DeveloperLog.init(this)
    }
}
