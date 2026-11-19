package br.com.vigiaterritorio

import android.app.Application
import br.com.vigiaterritorio.di.AppContainer

class VigiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
