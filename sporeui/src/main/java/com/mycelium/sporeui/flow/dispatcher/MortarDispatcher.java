package com.mycelium.sporeui.flow.dispatcher;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mycelium.sporeui.mortar.ScreenScoper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import flow.Direction;
import flow.Dispatcher;
import flow.KeyChanger;
import flow.MultiKey;
import flow.State;
import flow.Traversal;
import flow.TraversalCallback;

/**
 * Created by Nelson on 08/03/2016.
 */
public class MortarDispatcher implements Dispatcher {

    public static final class Builder {
        private final Activity activity;
        private final KeyChanger keyChanger;

        private Builder(Activity activity, KeyChanger keyChanger) {
            this.activity = activity;
            this.keyChanger = keyChanger;
        }

        public Dispatcher build() {
            return new MortarDispatcher(activity, this.keyChanger);
        }
    }

    public static Builder configure(Activity activity, KeyChanger changer) {
        return new Builder(activity, changer);
    }

    private final Activity activity;
    private final KeyChanger keyChanger;

    private MortarDispatcher(Activity activity, KeyChanger keyChanger) {
        this.activity = activity;
        this.keyChanger = keyChanger;
    }

    @Override
    public void dispatch(@NonNull Traversal traversal, @NonNull TraversalCallback callback) {
        State inState = traversal.getState(traversal.destination.top());
        Object inKey = inState.getKey();
        State outState = traversal.origin == null ? null : traversal.getState(traversal.origin.top());
        Object outKey = outState == null ? null : outState.getKey();

        // TODO(#126): short-circuit may belong in Flow, since every Dispatcher we have implements it.
        if (inKey.equals(outKey)) {
            callback.onTraversalCompleted();
            return;
        }

        ScreenScoper scoper = new ScreenScoper();
        Map<Object, Context> contexts = null;
        if (inKey instanceof MultiKey) {
            final List<Object> keys = ((MultiKey) inKey).getKeys();
            final int count = keys.size();
            contexts = new LinkedHashMap<>(count);
            for (int i = 0; i < count; i++) {
                final Object key = keys.get(i);
                Context context = traversal.createContext(key, activity);
                //MortarScope scope = scoper.getScreenScope(context,key.getClass().getName(),key);
                //contexts.put(key, scope.createContext(context));
            }
            Context context = traversal.createContext(inKey, activity);
           // MortarScope scope = scoper.getScreenScope(context,inKey.getClass().getName(),inKey);
            //contexts.put(inKey, scope.createContext(context));
            contexts = Collections.unmodifiableMap(contexts);
        } else {
            Context context = traversal.createContext(inKey, activity);
            //MortarScope scope = scoper.getScreenScope(context,inKey.getClass().getName(),inKey);
            //contexts = Collections.singletonMap(inKey, scope.createContext(context));
        }
        changeKey(outState, inState, traversal.direction, contexts, callback);
    }

    public void changeKey(@Nullable State outgoingState, State incomingState,
                          Direction direction, Map<Object, Context> incomingContexts,
                          final TraversalCallback callback) {
        keyChanger.changeKey(outgoingState, incomingState, direction, incomingContexts, callback);
    }

}
