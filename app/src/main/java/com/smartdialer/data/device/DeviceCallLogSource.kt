package com.smartdialer.data.device

import android.content.ContentResolver
import android.provider.CallLog
import com.smartdialer.data.CallKind
import com.smartdialer.data.CallLogSource
import com.smartdialer.data.CallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceCallLogSource(private val resolver: ContentResolver) : CallLogSource {

    override suspend fun loadCalls(sinceMillis: Long): List<CallRecord> = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            )
            val records = mutableListOf<CallRecord>()
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberCol)?.takeIf { it.isNotBlank() } ?: continue
                    records += CallRecord(
                        number = number,
                        kind = when (cursor.getInt(typeCol)) {
                            CallLog.Calls.INCOMING_TYPE -> CallKind.Incoming
                            CallLog.Calls.OUTGOING_TYPE -> CallKind.Outgoing
                            CallLog.Calls.MISSED_TYPE -> CallKind.Missed
                            else -> CallKind.Other
                        },
                        timestampMillis = cursor.getLong(dateCol),
                        durationSeconds = cursor.getLong(durationCol),
                    )
                }
            }
            records
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
