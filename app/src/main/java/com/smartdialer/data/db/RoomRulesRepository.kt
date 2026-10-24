package com.smartdialer.data.db

import androidx.room.withTransaction
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRulesRepository(
    private val db: OrbitDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RulesRepository {

    private val freeText = db.freeTextRuleDao()
    private val typed = db.typedRuleDao()

    override val freeTextRules: Flow<List<FreeTextRule>> =
        freeText.observeAll().map { rows -> rows.map(FreeTextRuleEntity::toDomain) }

    override val typedRules: Flow<List<TypedRule>> =
        typed.observeAll().map { rows -> rows.mapNotNull(TypedRuleEntity::toDomainOrNull) }

    override suspend fun addFreeTextRule(text: String, contactKey: String?): Long =
        freeText.insert(
            FreeTextRuleEntity(
                text = text.trim(),
                createdAt = nowMillis(),
                status = FreeTextStatus.UNCLASSIFIED,
                typedRuleId = null,
                contactKey = contactKey,
            ),
        )

    override suspend fun updateFreeTextRule(id: Long, text: String) = freeText.updateText(id, text.trim())

    override suspend fun deleteFreeTextRule(id: Long) = db.withTransaction {
        typed.clearSource(id)
        freeText.delete(id)
    }

    override suspend fun addTypedRule(
        params: TypedRuleParams,
        sourceFreeTextRuleId: Long?,
        createdAt: Long?,
    ): Long = db.withTransaction {
        val id = typed.insert(
            TypedRuleEntity(
                createdAt = createdAt ?: nowMillis(),
                type = params.type,
                paramsJson = params.toJson(),
                sourceFreeTextRuleId = sourceFreeTextRuleId,
            ),
        )
        if (sourceFreeTextRuleId != null) freeText.markConverted(sourceFreeTextRuleId, id)
        id
    }

    override suspend fun updateTypedRule(id: Long, params: TypedRuleParams) =
        typed.updateParams(id, params.type, params.toJson())

    override suspend fun deleteTypedRule(id: Long) = db.withTransaction {
        freeText.detachFromTyped(id)
        typed.delete(id)
    }
}
