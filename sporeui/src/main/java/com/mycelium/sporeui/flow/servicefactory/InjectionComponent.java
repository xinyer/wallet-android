package com.mycelium.sporeui.flow.servicefactory;

/**
 * Created by Nelson on 08/03/2016.
 */
public interface InjectionComponent<T> {
    Object createComponent(T parent);
}
