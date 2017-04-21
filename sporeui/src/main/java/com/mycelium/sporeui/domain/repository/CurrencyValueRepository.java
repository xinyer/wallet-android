package com.mycelium.sporeui.domain.repository;



import com.mycelium.sporeui.data.CurrencyValue;

import rx.Observable;

/**
 * Created by Nelson on 05/03/2016.
 */
public interface CurrencyValueRepository {
    Observable<CurrencyValue> getNewCurrencyValueObservable(CurrencyValue value);
}
