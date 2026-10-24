package com.smartdialer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleDaoTest {

    private lateinit var db: OrbitDatabase
    private lateinit var repo: RoomRulesRepository
    private var clock = 1_000L

    private val mom = ContactRef("lookup-mom", "Mom")
    private val boss = ContactRef("lookup-boss", "Boss")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).build()
        repo = RoomRulesRepository(db) { clock++ }
    }

    @After
    fun tearDown() = db.close()

    // --- Free-text DAO ---

    @Test
    fun freeTextRule_isStoredUnclassifiedAndTrimmed() = runBlocking {
        val id = repo.addFreeTextRule("  call Mom often  ")
        val rule = repo.freeTextRules.first().single()
        assertEquals(id, rule.id)
        assertEquals("call Mom often", rule.text)
        assertEquals(FreeTextStatus.UNCLASSIFIED, rule.status)
        assertNull(rule.typedRuleId)
        assertEquals(1_000L, rule.createdAt)
    }

    @Test
    fun freeTextRules_areListedNewestFirst() = runBlocking {
        repo.addFreeTextRule("first")
        repo.addFreeTextRule("second")
        assertEquals(listOf("second", "first"), repo.freeTextRules.first().map { it.text })
    }

    @Test
    fun freeTextRule_withoutContactIsGeneral_withContactIsAboutThatContact() = runBlocking {
        repo.addFreeTextRule("general")
        repo.addFreeTextRule("about mom", contactKey = mom.lookupKey)
        val rules = repo.freeTextRules.first()
        assertEquals(mom.lookupKey, rules[0].contactKey)
        assertNull(rules[1].contactKey)
    }

    @Test
    fun deletingAContactRuleLeavesGeneralOnesAlone() = runBlocking {
        val about = repo.addFreeTextRule("about mom", contactKey = mom.lookupKey)
        repo.addFreeTextRule("general")
        repo.deleteFreeTextRule(about)
        assertEquals(listOf("general"), repo.freeTextRules.first().map { it.text })
    }

    @Test
    fun freeTextRule_canBeEditedAndDeleted() = runBlocking {
        val id = repo.addFreeTextRule("old")
        repo.updateFreeTextRule(id, "new")
        assertEquals("new", repo.freeTextRules.first().single().text)
        repo.deleteFreeTextRule(id)
        assertTrue(repo.freeTextRules.first().isEmpty())
    }

    // --- Typed DAO ---

    @Test
    fun typedRule_roundTripsItsTypeAndParameters() = runBlocking {
        repo.addTypedRule(TypedRuleParams.PinToSlot(mom, 2))
        repo.addTypedRule(TypedRuleParams.NeverShow(boss))
        val rules = repo.typedRules.first()
        assertEquals(2, rules.size)
        val never = rules[0].params as TypedRuleParams.NeverShow
        assertEquals(boss, never.contact)
        val pin = rules[1].params as TypedRuleParams.PinToSlot
        assertEquals(mom, pin.contact)
        assertEquals(2, pin.slot)
        assertEquals(TypedRuleType.PIN_TO_SLOT, rules[1].params.type)
    }

    @Test
    fun typedRule_storesTheTypeInItsOwnColumn() = runBlocking {
        val id = repo.addTypedRule(TypedRuleParams.NeverShow(boss))
        val row = db.typedRuleDao().get(id)
        assertEquals(TypedRuleType.NEVER_SHOW, row?.type)
        assertTrue(row!!.paramsJson.contains("NEVER_SHOW"))
    }

    @Test
    fun typedRule_canBeUpdatedToADifferentType() = runBlocking {
        val id = repo.addTypedRule(TypedRuleParams.PinToSlot(mom, 1))
        repo.updateTypedRule(id, TypedRuleParams.NeverShow(mom))
        val rule = repo.typedRules.first().single()
        assertEquals(id, rule.id)
        assertTrue(rule.params is TypedRuleParams.NeverShow)
        assertEquals(TypedRuleType.NEVER_SHOW, db.typedRuleDao().get(id)?.type)
    }

    @Test
    fun typedRule_canBeDeleted() = runBlocking {
        val id = repo.addTypedRule(TypedRuleParams.NeverShow(boss))
        repo.deleteTypedRule(id)
        assertTrue(repo.typedRules.first().isEmpty())
    }

    @Test
    fun typedRule_restoredWithItsOriginalAge() = runBlocking {
        repo.addTypedRule(TypedRuleParams.NeverShow(boss), createdAt = 42L)
        assertEquals(42L, repo.typedRules.first().single().createdAt)
    }

    @Test
    fun typedRule_withUnreadableParametersIsSkippedNotFatal() = runBlocking {
        db.typedRuleDao().insert(
            TypedRuleEntity(createdAt = 1, type = TypedRuleType.NEVER_SHOW, paramsJson = "{not json", sourceFreeTextRuleId = null),
        )
        repo.addTypedRule(TypedRuleParams.NeverShow(boss))
        assertEquals(1, repo.typedRules.first().size)
    }

    // --- Links between the two lists ---

    @Test
    fun convertingAFreeTextRule_linksBothDirections() = runBlocking {
        val freeId = repo.addFreeTextRule("never show my boss")
        val typedId = repo.addTypedRule(TypedRuleParams.NeverShow(boss), sourceFreeTextRuleId = freeId)

        val free = repo.freeTextRules.first().single()
        assertEquals(FreeTextStatus.CONVERTED_TO_TYPED, free.status)
        assertEquals(typedId, free.typedRuleId)
        assertEquals(freeId, repo.typedRules.first().single().sourceFreeTextRuleId)
    }

    @Test
    fun deletingTheTypedRule_resetsTheFreeTextRule() = runBlocking {
        val freeId = repo.addFreeTextRule("never show my boss")
        val typedId = repo.addTypedRule(TypedRuleParams.NeverShow(boss), sourceFreeTextRuleId = freeId)

        repo.deleteTypedRule(typedId)

        val free = repo.freeTextRules.first().single()
        assertEquals(FreeTextStatus.UNCLASSIFIED, free.status)
        assertNull(free.typedRuleId)
    }

    @Test
    fun deletingTheFreeTextRule_keepsTheTypedRuleButUnlinksIt() = runBlocking {
        val freeId = repo.addFreeTextRule("never show my boss")
        repo.addTypedRule(TypedRuleParams.NeverShow(boss), sourceFreeTextRuleId = freeId)

        repo.deleteFreeTextRule(freeId)

        val typed = repo.typedRules.first().single()
        assertNotNull(typed)
        assertNull(typed.sourceFreeTextRuleId)
        assertTrue(repo.freeTextRules.first().isEmpty())
    }

    @Test
    fun unrelatedRulesAreUntouchedByDeletes() = runBlocking {
        val a = repo.addFreeTextRule("a")
        repo.addFreeTextRule("b")
        val t = repo.addTypedRule(TypedRuleParams.NeverShow(boss))
        repo.deleteFreeTextRule(a)
        repo.deleteTypedRule(t)
        assertEquals(listOf("b"), repo.freeTextRules.first().map { it.text })
    }
}
