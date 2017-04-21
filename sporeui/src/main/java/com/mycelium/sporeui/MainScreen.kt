package com.mycelium.sporeui

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.mycelium.sporeui.data.CurrencyValue
import com.mycelium.sporeui.helper.MainScreenViewHolder
import com.mycelium.sporeui.helper.MainScreenViewHolder.buttonParent
import com.mycelium.sporeui.listener.KeyChangeListener
import com.mycelium.sporeui.screen.ReceiveScreen
import com.mycelium.sporeui.screen.SendScreen
import com.pawegio.kandroid.*
import flow.Direction
import flow.Flow
import nz.bradcampbell.paperparcel.PaperParcel
import nz.bradcampbell.paperparcel.PaperParcelable
import org.parceler.Parcel
import org.parceler.ParcelConstructor
import java.math.BigDecimal
import javax.inject.Inject

@PaperParcel
@Layout(R.layout.screen_main)
data class MainScreen(val title: String) : PaperParcelable {
    constructor() : this("")

    companion object {
        @JvmField val CREATOR = PaperParcelable.Creator(MainScreen::class.java)
    }
}

class MainView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    var onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    @set:Inject
    lateinit var bitcoinService: BitcoinService


    override fun onFinishInflate() {
        super.onFinishInflate()
        MainApplication.graph.inject(this)

        val qrcodeView = find<QRCodeView>(R.id.qrcode)
        val addressTextView = find<TextView>(R.id.address)

        val address = bitcoinService.getBitcoinAddress()
        qrcodeView.data = address
        addressTextView.text = address.substring(0, 12) + '\n' + address.substring(12, 24) + '\n' + address.substring(24)

        val scanButton = find<RoundButtonWithText>(R.id.scan)
        scanButton.setOnClickListener {
            Flow.get(scanButton).set(ScanActivity())
        }

        val receiveButton = find<RoundButtonWithText>(R.id.receive)
        receiveButton.setOnClickListener {
            Flow.get(receiveButton).set(ReceiveScreen(CurrencyValue("XBT", BigDecimal.ZERO)))
        }

        val sendButton = find<RoundButtonWithText>(R.id.send)
        sendButton.setOnClickListener {
            Flow.get(sendButton).set(SendScreen(CurrencyValue("XBT", BigDecimal.ZERO)))
        }

        val balanceTextView = find<TextView>(R.id.balance)
        balanceTextView.text = "${bitcoinService.getBitcoinBalance().toDouble() / 100000000.0} BTC"

        val buttonParent = find<ViewGroup>(R.id.button_parent)

        MainScreenViewHolder.buttonParent = buttonParent
        MainScreenViewHolder.circleView = find<CircleView>(R.id.circle)
        MainScreenViewHolder.logoView = find<ImageView>(R.id.logo)


        onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (MainScreenViewHolder.points == null && MainScreenViewHolder.circleView != null) {

                val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusBarHeight = resources.getDimensionPixelSize(statusBarId)
                val yOffset = dp(64).toFloat()
                val size = Point()
                context.windowManager.defaultDisplay.getSize(size)
                MainScreenViewHolder.circleView.yOffset = yOffset

                val lastButton = buttonParent.views.last()
                val buttonParentWidth = lastButton.x + lastButton.width
                MainScreenViewHolder.center = if (size.x > buttonParentWidth) {
                    Point(buttonParentWidth.toInt() / 2, size.y / 2)
                } else {
                    Point(size.x / 2, size.y / 2)
                }
                MainScreenViewHolder.circleView.radius = 0F
                MainScreenViewHolder.points = buttonParent.views.map { Point(it.x.toInt(), it.y.toInt()) }

                buttonParent.setAsCircle(MainScreenViewHolder.center)

                val buttons = buttonParent.views
                buttons.forEachIndexed { i, button ->
                    button.setOnClickListener({ view ->
                        run {
                            val index = buttonParent.views.indexOf(view)
                            buttonParent.rotateOut(index, MainScreenViewHolder.center, MainScreenViewHolder.points)
                            runDelayed(if (index == buttonParent.childCount / 2) 0 else 400) {
                                if(MainScreenViewHolder.circleView != null) {
                                    MainScreenViewHolder.circleView.animateOut(size.y.toFloat() / 2 - statusBarHeight / 2)
                                    MainScreenViewHolder.logoView.fadeOut()
                                }
                            }
                            Flow.get(button).set(FakeKey())
                        }
                    })
                }

            }
        }
        buttonParent.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MainScreenViewHolder.buttonParent.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        onGlobalLayoutListener = null;
        MainScreenViewHolder.clear()
    }
}


@Parcel
class FakeKey : KeyChangeListener {

    @ParcelConstructor
    constructor()


    override fun isFake(): Boolean {
        return true
    }

    override fun outgoing(direction: Direction) {
        if (direction == Direction.BACKWARD && MainScreenViewHolder.buttonParent != null) {
            MainScreenViewHolder.circleView.animateIn()
            MainScreenViewHolder.logoView.fadeIn()
            MainScreenViewHolder.buttonParent.rotateIn(MainScreenViewHolder.center, MainScreenViewHolder.points);
        }
    }

    override fun incoming(direction: Direction) {
    }
}

