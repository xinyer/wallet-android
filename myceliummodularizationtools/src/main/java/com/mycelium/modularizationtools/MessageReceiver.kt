package com.mycelium.modularizationtools

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mycelium.modularizationtools.Constants.Companion.TAG

class MessageReceiver : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (null == intent || null == intent.action || !intent.hasExtra("key")) {
            Log.d(TAG, "onStartCommand failed: Intent was $intent")
            return Service.START_NOT_STICKY
        }
        Log.d(TAG, "onStartCommand: Intent is $intent")
        val key = intent.getLongExtra("key", 0)
        intent.removeExtra("key") // no need to share the key with other packages that might leak it
        val callerPackage: String
        try {
            // verify sender and get sending package name
            callerPackage = CommunicationManager.getInstance(this).getPackageName(key)
        } catch (e: SecurityException) {
            Log.e(TAG, "onStartCommand failed: ${e.message}")
            return Service.START_NOT_STICKY
        }
        if(application !is ModuleMessageReceiver) {
            // TODO: The application should not be required to implement anything. This would probably be better solved with Annotations on the ModuleMessageReceiver class.
            throw Error("onStartCommand failed: The current Application does not implement ModuleMessageReceiver!")
        }
        val moduleMessageReceiver = application as ModuleMessageReceiver
        moduleMessageReceiver.onMessage(callerPackage, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? = null
}

interface ModuleMessageReceiver {
    fun onMessage(callingPackageName: String, intent: Intent)
}
