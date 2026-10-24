package com.smartdialer.data

/** Matches call-log entries to contacts by phone number. */
object CallMatcher {

    /**
     * Compares phone numbers by their last 9 digits, so the same number written as `+972 50-111-1111`
     * and `050-111-1111` matches. Returns null for a number with no digits (private or unknown caller).
     */
    fun numberKey(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.takeLast(9)
    }

    /** Groups call records by the id (lookup key) of the contact they belong to. Unknown numbers are dropped. */
    fun groupByContact(contacts: List<DeviceContact>, calls: List<CallRecord>): Map<String, List<CallRecord>> {
        val contactByKey = HashMap<String, DeviceContact>()
        contacts.forEach { c -> c.numbers.forEach { n -> numberKey(n)?.let { contactByKey.putIfAbsent(it, c) } } }
        return calls
            .mapNotNull { record -> numberKey(record.number)?.let(contactByKey::get)?.let { it.contact.id to record } }
            .groupBy({ it.first }, { it.second })
    }

    /** When each number (by [numberKey]) was last used in a call of any kind. */
    fun lastUsedByNumberKey(records: List<CallRecord>): Map<String, Long> {
        val result = HashMap<String, Long>()
        for (r in records) {
            val key = numberKey(r.number) ?: continue
            if (r.timestampMillis > (result[key] ?: Long.MIN_VALUE)) result[key] = r.timestampMillis
        }
        return result
    }
}
