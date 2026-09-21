package com.patchcam.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PatchCamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
        }
    }
}
