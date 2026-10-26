package com.smartdialer.data.db

import androidx.room.withTransaction
import com.smartdialer.llm.InstructionLimits
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRulesRepository(
    private val db: OrbitDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RulesRepository {

    private val freeText = db.freeTextRuleDao()
    private val typed = db.typedRuleDao()
    private val effectDao = db.compiledEffectDao()

    override val freeTextRules: Flow<List<FreeTextRule>> =
        freeText.observeAll().map { rows -> rows.map(FreeTextRuleEntity::toDomain) }

    override val typedRules: Flow<List<TypedRule>> =
        typed.observeAll().map { rows -> rows.mapNotNull(TypedRuleEntity::toDomainOrNull) }

    override val effects: Flow<List<com.smartdialer.ranking.CompiledEffect>> =
        effectDao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                runCatching { com.smartdialer.ranking.EffectCodec.decode(row.paramsJson) }.getOrNull()
                    ?.let { com.smartdialer.ranking.CompiledEffect(row.id, row.sourceRuleId, it, row.enabled) }
            }
        }

    override suspend fun addEffects(ruleId: Long, params: List<com.smartdialer.ranking.EffectParams>) {
        params.forEach {
            effectDao.insert(CompiledEffectEntity(sourceRuleId = ruleId, paramsJson = com.smartdialer.ranking.EffectCodec.encode(it), enabled = true, createdAt = nowMillis()))
        }
    }

    override suspend fun setEffectEnabled(id: Long, enabled: Boolean) = effectDao.setEnabled(id, enabled)
    override suspend fun deleteEffect(id: Long) = effectDao.delete(id)
    override suspend fun clearEffects(ruleId: Long) = effectDao.deleteForRule(ruleId)

    override suspend fun addFreeTextRule(text: String, contactKey: String?): Long =
        freeText.insert(
            FreeTextRuleEntity(
                text = text.trim().take(InstructionLimits.MAX_LENGTH),
                createdAt = nowMillis(),
                status = FreeTextStatus.UNCLASSIFIED,
                typedRuleId = null,
                contactKey = contactKey,
            ),
        )

    override suspend fun updateFreeTextRule(id: Long, text: String) = db.withTransaction {
        // An edited instruction is compiled again, so its old effects go.
        effectDao.deleteForRule(id)
        freeText.updateText(id, text.trim().take(InstructionLimits.MAX_LENGTH))
    }

    override suspend fun setClassification(
        id: Long,
        status: FreeTextStatus,
        explanation: String?,
        proposedType: TypedRuleType?,
        proposedSlot: Int?,
    ) = freeText.setClassification(id, status, explanation, proposedType, proposedSlot)

    override suspend fun deleteFreeTextRule(id: Long) = db.withTransaction {
        typed.clearSource(id)
        effectDao.deleteForRule(id)
        freeText.delete(id)
    }

    override suspend fun addTypedRule(
        params: TypedRuleParams,
        sourceFreeTextRuleId: Long?,
        createdAt: Long?,
        explanation: String?,
    ): Long = db.withTransaction {
        val id = typed.insert(
            TypedRuleEntity(
                createdAt = createdAt ?: nowMillis(),
                type = params.type,
                paramsJson = params.toJson(),
                sourceFreeTextRuleId = sourceFreeTextRuleId,
            ),
        )
        if (sourceFreeTextRuleId != null) {
            freeText.markConverted(sourceFreeTextRuleId, id, explanation ?: "Converted to a typed rule.")
        }
        id
    }

    override suspend fun updateTypedRule(id: Long, params: TypedRuleParams) =
        typed.updateParams(id, params.type, params.toJson())

    override suspend fun deleteTypedRule(id: Long) = db.withTransaction {
        freeText.detachFromTyped(id, "The typed rule was removed, so this is a soft instruction again.")
        typed.delete(id)
    }
}
