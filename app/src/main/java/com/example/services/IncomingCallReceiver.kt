package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.example.security.PrivacyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingCallReceiver : BroadcastReceiver() {
    private val tag = "IncomingCallReceiver"

    companion object {
        var callAssistantManager: CallAssistantManager? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val privacyManager = PrivacyManager(context)
        if (!privacyManager.isCapabilityEnabled("phone_calls", true)) {
            return
        }

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val callerName = resolveCallerName(context, incomingNumber)
            Log.d(tag, "Incoming call ringing from $callerName ($incomingNumber)")

            val manager = callAssistantManager ?: CallAssistantManager(context.applicationContext).also {
                callAssistantManager = it
            }
            manager.onCallRinging(incomingNumber, callerName)
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            callAssistantManager?.onCallEnded()
        }
    }

    private fun resolveCallerName(context: Context, number: String): String {
        if (number.isBlank()) return "Unknown"
        var contactName = number
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        contactName = it.getString(nameIndex) ?: number
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to resolve contact for number $number", e)
        }
        return contactName
    }
}
