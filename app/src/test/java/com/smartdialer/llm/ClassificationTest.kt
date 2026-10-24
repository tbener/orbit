package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationTest {
    private val people = listOf(Contact("k-mom", "Mom"), Contact("k-ron1", "Ron Cohen"), Contact("k-ron2", "Ron Levi"), Contact("k-dana", "Dana Levi"))

    private fun rule(text: String, key: String? = null) = FreeTextRule(1, text, 1, FreeTextStatus.UNCLASSIFIED, null, key)
    private fun hard(action: ModelAction, name: String, slot: Int = 0) = ModelClassification(1, true, false, action, slot, name, "because")
    private fun resolve(r: FreeTextRule, m: ModelClassification) = ClassificationResolver.resolve(r, m, people, 20)

    @Test
    fun softStaysSoft() {
        assertTrue(resolve(rule("every 3 days", "k-mom"), ModelClassification(1, false, false, ModelAction.NONE, 0, "", "soft")) is ClassificationDecision.Soft)
    }

    @Test
    fun generalRuleNamingAUniqueContactBecomesHard() {
        val d = resolve(rule("pin Mom to slot 1"), hard(ModelAction.PIN_TO_SLOT, "mom", 1)) as ClassificationDecision.Hard
        assertEquals(TypedRuleParams.PinToSlot(d.params.contact, 1), d.params)
        assertEquals("k-mom", d.params.contact.lookupKey)
    }

    @Test
    fun contactRuleUsesItsOwnKey() {
        val d = resolve(rule("never show", "k-dana"), hard(ModelAction.NEVER_SHOW, "")) as ClassificationDecision.Hard
        assertEquals("k-dana", d.params.contact.lookupKey)
    }

    @Test
    fun ambiguousOrMissingNamesNeedReview() {
        val amb = resolve(rule("hide Ron"), hard(ModelAction.NEVER_SHOW, "Ron")) as ClassificationDecision.NeedsReview
        assertEquals(TypedRuleType.NEVER_SHOW, amb.proposedType)
        assertTrue("Ron Cohen" in amb.explanation)
        assertTrue(resolve(rule("hide Zed"), hard(ModelAction.NEVER_SHOW, "Zed")) is ClassificationDecision.NeedsReview)
        assertTrue(resolve(rule("hide"), hard(ModelAction.NEVER_SHOW, "")) is ClassificationDecision.NeedsReview)
        assertTrue(resolve(rule("x", "gone"), hard(ModelAction.NEVER_SHOW, "")) is ClassificationDecision.NeedsReview)
    }

    @Test
    fun uniqueWordMatchWorksAndBadSlotNeedsReview() {
        assertTrue(resolve(rule("hide Dana"), hard(ModelAction.NEVER_SHOW, "Dana")) is ClassificationDecision.Hard)
        val bad = resolve(rule("pin Mom"), hard(ModelAction.PIN_TO_SLOT, "Mom", 99)) as ClassificationDecision.NeedsReview
        assertEquals(TypedRuleType.PIN_TO_SLOT, bad.proposedType)
    }

    @Test
    fun parserDropsUnknownIds() {
        val text = """{"classifications":[{"ruleId":1,"kind":"SOFT","action":"NONE","slot":0,"personName":"","explanation":"e"},{"ruleId":9,"kind":"SOFT","action":"NONE","slot":0,"personName":"","explanation":"e"}]}"""
        val p = ClassificationParser.parse(text, setOf(1L, 2L)) as ClassificationParse.Valid
        assertEquals(1, p.items.size)
        assertTrue(p.issues.any { "unknown" in it } && p.issues.any { "stay unclassified" in it })
    }

    private suspend fun classify(json: String?, rules: FakeRules): ClassifyOutcome {
        val client = if (json == null) FakeLlmClient.failing() else FakeLlmClient.answering(json)
        return RuleClassifier(client, InMemoryAiSettings(), rules, FakeContacts(people), InMemoryLlmExchangeStore()).classifyPending()
    }

    @Test
    fun classifierConvertsHardRulesAndLinksBothWays() = runTest {
        val rules = FakeRules()
        val id = rules.addFreeTextRule("always put Mom first")
        val other = rules.addFreeTextRule("hide Ron")
        val json = """{"classifications":[
            {"ruleId":$id,"kind":"HARD","action":"PIN_TO_SLOT","slot":1,"personName":"Mom","explanation":"Hard: Mom first."},
            {"ruleId":$other,"kind":"HARD","action":"NEVER_SHOW","slot":0,"personName":"Ron","explanation":"hide"}]}"""
        assertEquals(ClassifyOutcome.DONE, classify(json, rules))
        val free = rules.freeTextRules.first().associateBy { it.id }
        val typed = rules.typedRules.first().single()
        assertEquals(FreeTextStatus.CONVERTED_TO_TYPED, free.getValue(id).status)
        assertEquals(typed.id, free.getValue(id).typedRuleId)
        assertEquals(id, typed.sourceFreeTextRuleId)
        assertEquals(FreeTextStatus.NEEDS_REVIEW, free.getValue(other).status)
    }

    @Test
    fun failedCallLeavesRulesUnclassified() = runTest {
        val rules = FakeRules()
        rules.addFreeTextRule("x")
        assertEquals(ClassifyOutcome.FAILED, classify(null, rules))
        assertEquals(FreeTextStatus.UNCLASSIFIED, rules.freeTextRules.first().single().status)
    }

    @Test
    fun overrideFlows() = runTest {
        val rules = FakeRules()
        val id = rules.addFreeTextRule("often")
        rules.setClassification(id, FreeTextStatus.SOFT, "soft")
        val typedId = rules.addTypedRule(TypedRuleParams.NeverShow(com.smartdialer.rules.ContactRef("k-mom", "Mom")), id, explanation = "You made this a hard rule.")
        assertEquals(FreeTextStatus.CONVERTED_TO_TYPED, rules.freeTextRules.first().single().status)
        rules.deleteTypedRule(typedId)
        val back = rules.freeTextRules.first().single()
        assertEquals(FreeTextStatus.SOFT, back.status)
        assertNull(back.typedRuleId)
        assertTrue(rules.typedRules.first().isEmpty())
    }
}
