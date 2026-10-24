package com.smartdialer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberChooserTest {

    private fun key(number: String) = CallMatcher.numberKey(number)!!

    @Test
    fun noNumbers_none() {
        assertEquals(NumberChoice.None, NumberChooser.choose(emptyList(), emptyMap()))
        assertEquals(NumberChoice.None, NumberChooser.choose(listOf("", "n/a"), emptyMap()))
    }

    @Test
    fun singleNumber_isUsed() {
        assertEquals(NumberChoice.Use("050-111-1111"), NumberChooser.choose(listOf("050-111-1111"), emptyMap()))
    }

    @Test
    fun sameNumberWrittenTwoWays_countsOnce() {
        val choice = NumberChooser.choose(listOf("050-111-1111", "+972501111111"), emptyMap())
        assertEquals(NumberChoice.Use("050-111-1111"), choice)
    }

    @Test
    fun mostRecentlyUsedNumberWins() {
        val numbers = listOf("0501111111", "0522222222", "0533333333")
        val used = mapOf(key(numbers[0]) to 100L, key(numbers[1]) to 300L, key(numbers[2]) to 200L)
        assertEquals(NumberChoice.Use("0522222222"), NumberChooser.choose(numbers, used))
    }

    @Test
    fun theOnlyNumberEverUsedWins() {
        val numbers = listOf("0501111111", "0522222222")
        assertEquals(NumberChoice.Use("0522222222"), NumberChooser.choose(numbers, mapOf(key(numbers[1]) to 5L)))
    }

    @Test
    fun noNumberInTheCallLog_asksTheUser() {
        val numbers = listOf("0501111111", "0522222222")
        assertEquals(NumberChoice.Pick(numbers), NumberChooser.choose(numbers, emptyMap()))
    }

    @Test
    fun tieForNewest_asksTheUserWithRecentFirst() {
        val numbers = listOf("0501111111", "0522222222", "0533333333")
        val used = mapOf(key(numbers[0]) to 100L, key(numbers[1]) to 100L)
        assertEquals(
            NumberChoice.Pick(listOf("0501111111", "0522222222", "0533333333")),
            NumberChooser.choose(numbers, used),
        )
    }

    @Test
    fun usageOfNumbersFromOtherContactsIsIgnored() {
        val numbers = listOf("0501111111", "0522222222")
        val used = mapOf(key("0599999999") to 999L)
        assertEquals(NumberChoice.Pick(numbers), NumberChooser.choose(numbers, used))
    }
}
