package com.smartdialer

import android.content.Context
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.data.db.OrbitDatabase
import com.smartdialer.data.db.RoomRulesRepository
import com.smartdialer.data.device.DeviceCallLogSource
import com.smartdialer.data.device.DeviceContactsRepository
import com.smartdialer.data.device.DeviceContactsSource
import com.smartdialer.data.device.DeviceSuggestionsRepository
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons. Created once by [OrbitApplication]. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolver = context.contentResolver

    val rulesRepository: RulesRepository = RoomRulesRepository(OrbitDatabase.create(context))
    val contactsRepository: ContactsRepository = DeviceContactsRepository(DeviceContactsSource(resolver))
    val suggestionsRepository: SuggestionsRepository =
        DeviceSuggestionsRepository(contactsRepository, DeviceCallLogSource(resolver))
    val suggestionPipeline = SuggestionPipeline(suggestionsRepository, rulesRepository, contactsRepository, scope)
}
