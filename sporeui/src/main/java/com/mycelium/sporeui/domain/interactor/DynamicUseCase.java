package com.mycelium.sporeui.domain.interactor;

import android.os.Bundle;

import com.mycelium.sporeui.domain.executor.PostExecutionThread;

import java.util.concurrent.Executor;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;


/**
 * Created by Nelson on 09/11/2015.
 */
abstract class DynamicUseCase extends UseCase {

    protected DynamicUseCase(Executor threadExecutor, PostExecutionThread postExecutionThread) {
        super(threadExecutor, postExecutionThread);
    }

    public abstract Observable buildUseCaseObservable(Bundle bundle);

    @Override
    protected Observable buildUseCaseObservable() {
        throw new UnsupportedOperationException("Use buildUseCaseObservable(Bundle bundle)");
    }

    @SuppressWarnings("unchecked")
    public void execute(Subscriber UseCaseSubscriber, Bundle bundle) {
        this.subscription = this.buildUseCaseObservable(bundle)
                .subscribeOn(Schedulers.from(this.threadExecutor))
                .observeOn(this.postExecutionThread.getScheduler())
                .subscribe(UseCaseSubscriber);
    }
}
