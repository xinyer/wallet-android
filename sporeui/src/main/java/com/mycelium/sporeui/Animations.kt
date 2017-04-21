package com.mycelium.sporeui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Point
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.widget.ImageButton
import android.widget.ImageView
import com.mycelium.sporeui.helper.SelectedItemAnimation
import com.pawegio.kandroid.dp
import com.pawegio.kandroid.views


private val DURATION = 400L
private val LOGO_RADIUS = 100
private val BUTTON_SCALE = 0.625F

fun CircleView.animateOut(radius: Float) {
    val ret = ObjectAnimator.ofFloat(this, "radius", 0F, radius)
    ret.duration = DURATION
    ret.start()
}

fun CircleView.animateIn() {
    val ret = ObjectAnimator.ofFloat(this, "radius", radius, 0F)
    ret.duration = DURATION
    ret.start()
}

fun ViewGroup.setAsCircle(center: Point, offset: Int = 0) {

    val diff = 2 * Math.PI / childCount
    val midPoint = childCount / 2
    val halfWidth = views[0].width / 2

    views.forEachIndexed { i, view ->
        val index = (i + offset - midPoint) % childCount
        val rad = index * diff - Math.PI / 2

        view.x = Math.cos(rad).toFloat() * dp(LOGO_RADIUS).toFloat() + center.x - halfWidth
        view.y = Math.sin(rad).toFloat() * dp(LOGO_RADIUS).toFloat() + center.y - halfWidth

        view.scaleX = BUTTON_SCALE
        view.scaleY = BUTTON_SCALE
    }
}

fun ViewGroup.animateIn(context: Context, center: Point, offset: Int = 0) {
    val diff = 2 * Math.PI / childCount
    val animator = AnimatorSet()
    animator.duration = DURATION
    val midPoint = childCount / 2
    val halfWidth = views[0].width / 2
    val midButton = views[midPoint] as ImageButton

    views.forEachIndexed { i, view ->
        val index = (i + offset - midPoint) % childCount
        val rad = index * diff - Math.PI / 2
        val animX = ObjectAnimator.ofFloat(view, "x", view.x,
                Math.cos(rad).toFloat() * dp(LOGO_RADIUS).toFloat() + center.x - halfWidth)
        animator.play(animX)
        val animY = ObjectAnimator.ofFloat(view, "y", view.y,
                Math.sin(rad).toFloat() * dp(LOGO_RADIUS).toFloat() + center.y - halfWidth)
        animator.play(animY)
    }

    val scaleAnim = AnimationUtils.loadAnimation(context, R.anim.button_scale_down)
    midButton.startAnimation(scaleAnim)
    midButton.scaleType = ImageView.ScaleType.FIT_CENTER
    animator.start()
}

fun ViewGroup.rotateIn(center: Point, points: List<Point>) {
    val midPoint = childCount / 2

    views.forEachIndexed { i, view ->
        view.clearAnimation()
        val animation = AnimationSet(true)
        animation.fillAfter = true
        animation.duration = DURATION

        val point = points[i]

        val x1 = point.x - view.x
        val y1 = point.y - view.y

        if (i == midPoint) {
            val rad = 0 - Math.PI / 2
            val y = Math.sin(rad).toFloat() * dp(LOGO_RADIUS).toFloat() + center.y - view.width / 2
            val translate = SelectedItemAnimation(view, y - view.y, 1f, BUTTON_SCALE)
            animation.addAnimation(translate)
            val midButton = views[midPoint] as ImageButton
            midButton.scaleType = ImageView.ScaleType.FIT_CENTER
        }else {
            val translate = TranslateAnimation(x1, 0f, y1, 0f)
            animation.addAnimation(translate)
        }
        view.startAnimation(animation)

    }
}

fun ViewGroup.rotateOut(index: Int, center: Point, points: List<Point>) {
    val midPoint = childCount / 2
    val offset = index - midPoint

    if (offset < 0) {
        for (i in offset + 1..0) {
            val child = views.last()
            removeView(child)
            addView(child, 0)
        }
    } else if (offset > 0) {
        for (i in 0..offset - 1) {
            val child = views.first()
            removeView(child)
            addView(child)
        }
    }

    setAsCircle(center, offset)

    views.forEachIndexed { i, view ->
        val nextAnim = {
            val animation = AnimationSet(true)
            animation.fillAfter = true
            animation.duration = DURATION

            val point = points[i]

            val x2 = point.x - view.x
            val y2 = point.y - view.y

            if (i == midPoint) {
                val translate = SelectedItemAnimation(view, point.y - view.y, BUTTON_SCALE, 1f)
                animation.addAnimation(translate)
                val midButton = views[midPoint] as ImageButton
                midButton.scaleType = ImageView.ScaleType.CENTER
            } else {
                val translate = TranslateAnimation(0f, x2, 0f, y2)
                animation.addAnimation(translate)
            }
            view.startAnimation(animation)
        }

        if (offset == 0) {
            nextAnim()
        } else {
            val rotate = CircleAnimation(this, view, i, offset, center, dp(LOGO_RADIUS).toFloat())
            rotate.fillAfter = true
            rotate.duration = DURATION
            rotate.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationEnd(animation: Animation?) {
                    nextAnim()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationStart(animation: Animation?) {}
            })
            view.startAnimation(rotate)
        }
    }
}

fun ViewGroup.radians(index: Int, offset: Int): Double {
    val diff = 2 * Math.PI / childCount
    val midPoint = childCount / 2
    val index = index + offset - midPoint
    return index * diff - Math.PI / 2
}

fun View.fadeIn() {
    visibility = View.VISIBLE
    val anim = AlphaAnimation(0F, 1F)
    anim.duration = DURATION
    startAnimation(anim)
}

fun View.fadeOut() {
    val anim = AlphaAnimation(1F, 0F)
    anim.duration = DURATION
    anim.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationEnd(animation: Animation?) {
            visibility = View.GONE
        }

        override fun onAnimationRepeat(animation: Animation?) {}
        override fun onAnimationStart(animation: Animation?) {}
    })
    startAnimation(anim)
}

class CircleAnimation(
        val layout: ViewGroup,
        val view:View,
        val i: Int,
        val offset: Int,
        val center: Point,
        val radius: Float) : Animation() {
    private var prevX: Float = 0F
    private var prevY: Float = 0F
    private var prevDx: Float = 0F
    private var prevDy: Float = 0F
    var diffX = 0.0
    var diffY = 0.0
    var startRad = 0.0
    var diffRad = 0.0
    var halfWidth = 0

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        halfWidth = layout.views[0].width / 2

        startRad = layout.radians(i, offset)
        diffRad = -offset * 2 * Math.PI / layout.childCount
        val x = Math.cos(startRad).toFloat() * radius + center.x - halfWidth
        val y = Math.sin(startRad).toFloat() * radius + center.y - halfWidth

        diffX = (x - center.x).toDouble()
        diffY = (y - center.y).toDouble()

        // set previous position to center
        prevX = x
        prevY = y
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        if (interpolatedTime == 0F) {
            return
        }

        val angle = interpolatedTime * diffRad
        val rad = startRad + angle

        view.x = Math.cos(rad).toFloat() * radius + center.x - halfWidth
        view.y = Math.sin(rad).toFloat() * radius + center.y - halfWidth
    }

}
