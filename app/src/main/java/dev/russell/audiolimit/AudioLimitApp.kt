package dev.russell.audiolimit

import android.app.Application

class AudioLimitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.enforcer.start()
    }
}
