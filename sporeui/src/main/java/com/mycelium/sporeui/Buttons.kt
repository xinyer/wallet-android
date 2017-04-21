package com.mycelium.sporeui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.*
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.util.AttributeSet
import android.util.StateSet
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.pawegio.kandroid.find
import com.pawegio.kandroid.inflateLayout
import kotlin.properties.Delegates

class RoundButton(context: Context, attrs: AttributeSet) : ImageButton(context, attrs) {

    var circleColor: Int by Delegates.observable(0) {
        prop, old, new ->
        val drawable: Drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawable = resources.getDrawable(R.drawable.round_background, context.theme)
            val bg = (drawable as RippleDrawable).getDrawable(0) as GradientDrawable
            bg.setColor(new)
        } else {
            val defaultDrawable = resources.getDrawable(R.drawable.round_background).mutate()
            val pressedDrawable = resources.getDrawable(R.drawable.round_background).mutate()
            (defaultDrawable as GradientDrawable).setColor(new)
            val pressedColor = Color.argb(128, Color.red(new), Color.green(new), Color.blue(new))
            (pressedDrawable as GradientDrawable).setColor(pressedColor)
            drawable = StateListDrawable()
            drawable.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            drawable.addState(StateSet.WILD_CARD, defaultDrawable)
        }
        background = drawable
    }

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.RoundButton, 0, 0)
        try {
            circleColor = a.getColor(R.styleable.RoundButton_backgroundColor,
                    R.color.colorPrimary)
        } finally {
            a.recycle()
        }
    }
}

class RoundButtonWithText(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    init {
        context.inflateLayout(R.layout.button_big_round_with_text, this, true)

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.RoundButtonWithText, 0, 0)
        try {
            val roundButton = find<RoundButton>(R.id.roundButton)

            val circleColor = a.getColor(R.styleable.RoundButtonWithText_backgroundColor,
                    R.color.colorPrimary)
            roundButton.circleColor = circleColor

            val icon = a.getDrawable(R.styleable.RoundButtonWithText_icon)
            roundButton.setImageDrawable(icon)

            val text = a.getString(R.styleable.RoundButtonWithText_text)
            val textView = find<TextView>(R.id.textView)
            textView.text = text
        } finally {
            a.recycle()
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        val roundButton = find<RoundButton>(R.id.roundButton)
        roundButton.setOnClickListener(l)
    }
}

class GradientRoundButton(context: Context, attrs: AttributeSet) : ImageButton(context, attrs) {

    var topColor: Int = 0

    var bottomColor: Int = 0

    private fun updateBackground() {
        val colors = intArrayOf(topColor, bottomColor)

        val drawable: Drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val shape = ShapeDrawable(OvalShape())
            shape.paint.shader = LinearGradient(0F, 0F, 0F, height.toFloat(), topColor, bottomColor, Shader.TileMode.MIRROR)
            drawable = RippleDrawable(ColorStateList.valueOf(Color.WHITE), shape, null)
        } else {
            val defaultDrawable = resources.getDrawable(R.drawable.round_background).mutate()
            val pressedDrawable = resources.getDrawable(R.drawable.round_background).mutate()

            (defaultDrawable as GradientDrawable).colors = colors
            val topPressedColor = Color.argb(128,
                    Color.red(topColor), Color.green(topColor), Color.blue(topColor))
            val bottomPressedColor = Color.argb(128,
                    Color.red(bottomColor), Color.green(bottomColor), Color.blue(bottomColor))
            (pressedDrawable as GradientDrawable).colors = intArrayOf(topPressedColor, bottomPressedColor)
            drawable = StateListDrawable()
            drawable.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            drawable.addState(StateSet.WILD_CARD, defaultDrawable)
        }
        background = drawable
    }

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.GradientRoundButton, 0, 0)
        try {
            topColor = a.getColor(R.styleable.GradientRoundButton_topColor,
                    R.color.colorPrimary)
            bottomColor = a.getColor(R.styleable.GradientRoundButton_bottomColor,
                    R.color.colorPrimary)
            val icon = a.getDrawable(R.styleable.GradientRoundButton_icon)
            setImageDrawable(icon)
        } finally {
            a.recycle()
        }

        viewTreeObserver.addOnGlobalLayoutListener({
            updateBackground()
        })
    }
}
