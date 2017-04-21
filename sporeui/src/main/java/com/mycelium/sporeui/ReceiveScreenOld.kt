package com.mycelium.sporeui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import com.mycelium.sporeui.screen.ReceiveScreen
import com.pawegio.kandroid.find
import nz.bradcampbell.paperparcel.PaperParcel
import nz.bradcampbell.paperparcel.PaperParcelable
import org.parceler.Parcel
import javax.inject.Inject

@Layout(R.layout.screen_receive_old)
@PaperParcel
data class ReceiveScreenOld(val title: String) : PaperParcelable {
    constructor() : this("")
    companion object {
      @JvmField val CREATOR = PaperParcelable.Creator(ReceiveScreen::class.java)
    }
}

class ReceiveViewOld(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    @Inject
    lateinit var bitcoinService: BitcoinService

    override fun onFinishInflate() {
        super.onFinishInflate()
        val qrcode = find<QRCodeView>(R.id.qrcode)
       // MainApplication.graph.inject(this)
        Log.d("BITCOIN", bitcoinService.getBitcoinAddress())
        qrcode.data = bitcoinService.getBitcoinAddress()
        qrcode.setOnClickListener {
            if (qrcode.backgroundColor == android.R.color.white) {
                qrcode.setBackgroundColor(R.color.greyish)
            } else {
                qrcode.setBackgroundColor(android.R.color.white)
            }
            qrcode.invalidate()
        }

        val address = bitcoinService.getBitcoinAddress()
        val addressTextView = find<TextView>(R.id.address)
        addressTextView.text = address
    }
}