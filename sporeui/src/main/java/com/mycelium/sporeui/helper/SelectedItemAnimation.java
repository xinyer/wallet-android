package com.mycelium.sporeui.helper;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Created by elvis on 27.01.17.
 */

public class SelectedItemAnimation extends Animation {

    private View view;
    private float yDelta;
    private float scaleFrom;
    private float scaleTo;


    public SelectedItemAnimation(View view, float yDelta, float scaleFrom, float scaleTo) {
        this.view = view;
        this.yDelta = yDelta;
        this.scaleFrom = scaleFrom;
        this.scaleTo = scaleTo;
    }

    private float prevTime = 0;

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float timeDx = (interpolatedTime - prevTime);
        view.setY(view.getY() + timeDx * yDelta);
        float scale = scaleFrom + interpolatedTime * (scaleTo - scaleFrom);
        view.setScaleX(scale);
        view.setScaleY(scale);
        prevTime = interpolatedTime;
    }

}
