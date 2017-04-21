package com.mycelium.sporeui.flow.servicefactory;

import android.util.Log;

import com.mycelium.sporeui.mortar.ScreenScoper;

import flow.Services;
import flow.ServicesFactory;
import mortar.MortarScope;

/**
 * Created by Nelson on 04/03/2016.
 */
public class DaggerServiceFactory extends ServicesFactory {

    private final MortarScope parentScope;
    private final ScreenScoper screenScoper;

    public DaggerServiceFactory(MortarScope parentScope) {
        this.parentScope = parentScope;
        this.screenScoper = new ScreenScoper();
    }

    @Override
    public void bindServices(Services.Binder services) {
        MortarScope scope = null;

        Log.d("services", services.getKey().toString());

        scope = screenScoper.getScreenScope(parentScope, services.getKey().getClass().getName(), services.getKey());

        if(scope != null)
            services.bind(services.getKey().getClass().getName(), scope);
    }

    @Override
    public void tearDownServices(Services services) {
        super.tearDownServices(services);
        MortarScope scope = parentScope.findChild(services.getKey().getClass().getName());

        if(scope != null)
            scope.destroy();
    }
}
