package com.mycelium.sporeui.data.repository;

import android.content.ContentResolver;

import com.mycelium.sporeui.data.CurrencyValue;
import com.mycelium.sporeui.domain.repository.CurrencyValueRepository;

import java.math.BigDecimal;

import rx.Observable;
import rx.Subscriber;


public class CurrencyValueRepositoryMockImpl implements CurrencyValueRepository {

    private final ContentResolver contentResolver;

    public CurrencyValueRepositoryMockImpl(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    @Override
    public Observable<CurrencyValue> getNewCurrencyValueObservable(
        final CurrencyValue currentCurrencyValue) {
        return Observable.create(new Observable.OnSubscribe<CurrencyValue>() {
            @Override
            public void call(Subscriber<? super CurrencyValue> subscriber) {
                switch (currentCurrencyValue.getCurrencyCode()) {
                  case "EUR":
                    subscriber.onNext(new CurrencyValue("USD", BigDecimal.valueOf(123)));
                    break;
                  case "USD":
                    subscriber.onNext(new CurrencyValue("XBT", BigDecimal.valueOf(93)));
                    break;
                  case "XBT":
                    subscriber.onNext(new CurrencyValue("EUR", BigDecimal.valueOf(75)));
                    break;
                    default:
                      subscriber.onError(new Exception("Unknown currency."));
                      subscriber.onCompleted();
                }
                subscriber.onCompleted();
            }
        });
    }

}
