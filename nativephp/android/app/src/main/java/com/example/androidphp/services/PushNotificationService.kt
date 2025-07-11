package com.example.androidphp.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationService: FirebaseMessagingService()  {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("MESSAGING", "Refreshed token: $token")
        //update remote server with new token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("MESSAGING", "Message data: ${message.data}")
        Log.d("MESSAGING", "Message notification: ${message.notification?.body}")
        Log.d("MESSAGING", "Message notification: ${message.notification?.title}")
    }

}
