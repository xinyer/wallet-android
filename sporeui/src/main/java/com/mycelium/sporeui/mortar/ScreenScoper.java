package com.mycelium.sporeui.mortar;

import com.mycelium.sporeui.MainActivity;
import com.mycelium.sporeui.di.DaggerService;
import com.mycelium.sporeui.flow.servicefactory.InjectionComponent;

import mortar.MortarScope;

/**
 * Created by Nelson
 */
public class ScreenScoper {

    public MortarScope getScreenScope(MortarScope parentScope, String name, Object key) {

        parentScope = parentScope.findChild(MainActivity.class.getName());
        MortarScope childScope = parentScope.findChild(name);

        if (childScope != null)
            return childScope;

        if (!(key instanceof InjectionComponent)) {
            //throw new IllegalStateException(String.format(Locale.getDefault(),"The screen (%s) must implement InjectionComponent",key.getClass().getSimpleName()));
            return null;
        }

        InjectionComponent screenComponent = (InjectionComponent) key;
        Object component = screenComponent.createComponent(parentScope.getService(DaggerService.SERVICE_NAME));

        return parentScope.buildChild().withService(DaggerService.SERVICE_NAME, component).build(name);
    }

}
