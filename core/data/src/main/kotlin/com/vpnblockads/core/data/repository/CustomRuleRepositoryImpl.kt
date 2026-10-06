package com.vpnblockads.core.data.repository

import com.vpnblockads.core.data.database.CustomRuleDao
import com.vpnblockads.core.data.database.CustomRuleEntity
import com.vpnblockads.core.data.database.toModel
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.model.CustomRule
import com.vpnblockads.core.model.RuleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomRuleRepositoryImpl @Inject constructor(
    private val dao: CustomRuleDao,
) : CustomRuleRepository {
    override val rules: Flow<List<CustomRule>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun upsert(domain: String, kind: RuleKind) =
        dao.upsert(CustomRuleEntity(domain, kind.name, System.currentTimeMillis()))

    override suspend fun remove(domain: String) = dao.delete(domain)
}
