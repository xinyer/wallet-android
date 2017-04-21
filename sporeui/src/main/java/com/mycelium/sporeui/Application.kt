package com.mycelium.sporeui

import android.app.Application
import com.mycelium.sporeui.di.DaggerService
import com.mycelium.sporeui.di.component.ApplicationComponent
import com.mycelium.sporeui.di.component.DaggerApplicationComponent
import com.mycelium.sporeui.di.module.ApplicationModule
import javax.inject.Inject
import mortar.MortarScope

class MainApplication : Application() {

    companion object {
        //platformStatic allow access it from java code
        @JvmStatic lateinit var graph: ApplicationComponent
    }

    @set:Inject
    lateinit var bitcoinService: BitcoinService



    override fun onCreate() {
        super.onCreate()
        graph = DaggerApplicationComponent.builder().applicationModule(ApplicationModule(this)).build()
        graph.inject(this)

        println("App: $bitcoinService")
    }

    private var rootScope: MortarScope? = null

    override fun getSystemService(name: String): Any {
        if (rootScope == null) {
            rootScope = MortarScope.buildRootScope()
                    .withService(DaggerService.SERVICE_NAME,
                            DaggerApplicationComponent
                                    .builder()
                                    .applicationModule(ApplicationModule(this))
                                    .build())
                    .build(getScopeName())
        }

        return if (rootScope!!.hasService(name))
            rootScope!!.getService(name)
        else
            super.getSystemService(name)
    }

    private fun getScopeName(): String? {
        return this.javaClass.name
    }
}
