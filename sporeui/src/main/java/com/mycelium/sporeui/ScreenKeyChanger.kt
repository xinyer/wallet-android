package com.mycelium.sporeui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mycelium.sporeui.listener.KeyChangeListener
import com.pawegio.kandroid.IntentFor
import com.pawegio.kandroid.find
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.views
import flow.Direction
import flow.KeyChanger
import flow.State
import flow.TraversalCallback

class ScreenKeyChanger(private val mainActivity: MainActivity) : KeyChanger() {
    override fun changeKey(outgoingState: State?, incomingState: State, direction: Direction,
                           incomingContexts: MutableMap<Any, Context>, callback: TraversalCallback) {
        val frame = mainActivity.find<ViewGroup>(R.id.frame)
        val originView = frame.views.firstOrNull()
        originView?.let { outgoingState?.save(originView) }

        val key = incomingState.getKey<Any>()
        val outKey = outgoingState?.getKey<Any>()
        var isFake = false;
        if(outKey is KeyChangeListener) {
            isFake = outKey.isFake()
        }

        if (key is ScanActivity) {
            mainActivity.startActivityForResult(IntentFor<ScanActivity>(mainActivity), mainActivity.SCANNER_RESULT_CODE)
        } else if(!isFake && key.javaClass.getAnnotation(Layout::class.java) != null){
            val layout = key.javaClass.getAnnotation(Layout::class.java)
            val screenContext = incomingContexts[key]
            val destinationView = LayoutInflater.from(screenContext).inflate(layout.value, frame, false)
            incomingState.restore(destinationView)
            with(frame) {
                addView(destinationView)
                removeView(originView)
            }
        }

        if(outKey is KeyChangeListener) {
            outKey.outgoing(direction)
        }

        if(key is KeyChangeListener) {
            key.incoming(direction)
        }

        callback.onTraversalCompleted()
    }
}
