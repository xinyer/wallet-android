package com.mycelium.sporeui.helper;

import android.graphics.Point;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.mycelium.sporeui.CircleView;

import java.util.List;

/**
 * Created by elvis on 27.01.17.
 */

public class MainScreenViewHolder {
    public static CircleView circleView;
    public static ImageView logoView;
    public static ViewGroup buttonParent;
    public static Point center;
    public static List<Point> points;

    public static void clear() {
        circleView = null;
        logoView = null;
        buttonParent = null;
        center = null;
        points = null;
    }
}
