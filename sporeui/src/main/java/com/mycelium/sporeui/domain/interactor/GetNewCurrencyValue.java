package com.mycelium.sporeui.domain.interactor;

import android.os.Bundle;

import com.mycelium.sporeui.data.CurrencyValue;
import com.mycelium.sporeui.domain.executor.PostExecutionThread;
import com.mycelium.sporeui.domain.repository.CurrencyValueRepository;

import org.parceler.Parcels;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import rx.Observable;

/**
 * Created by Nelson on 05/03/2016.
 */
public class GetNewCurrencyValue extends DynamicUseCase {

    private final CurrencyValueRepository currencyValueRepository;

    @Inject
    public GetNewCurrencyValue(CurrencyValueRepository currencyValueRepository,
                               Executor threadExecutor,
                               PostExecutionThread postExecutionThread) {
        super(threadExecutor, postExecutionThread);
        this.currencyValueRepository = currencyValueRepository;
    }

    @Override
    public Observable buildUseCaseObservable(Bundle bundle) {
        return this.currencyValueRepository.getNewCurrencyValueObservable(
            (CurrencyValue) Parcels.unwrap(bundle.getParcelable("currencyValue")));
    }
}
