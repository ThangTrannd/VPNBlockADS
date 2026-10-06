package com.vpnblockads.core.domain.usecase

import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.model.Domains
import com.vpnblockads.core.model.RuleKind
import javax.inject.Inject

/**
 * Thêm quy tắc whitelist/blocklist. Chuẩn hoá & kiểm tra domain ở một chỗ
 * để UI không phải lặp lại. Mỗi domain chỉ có một quy tắc: thêm "ALLOW"
 * cho domain đang "BLOCK" sẽ thay thế.
 */
class AddCustomRuleUseCase @Inject constructor(
    private val repository: CustomRuleRepository,
) {
    sealed interface Result {
        data class Added(val domain: String) : Result
        data object InvalidDomain : Result
    }

    suspend operator fun invoke(rawDomain: String, kind: RuleKind): Result {
        val domain = Domains.normalize(rawDomain).removePrefix("*.")
        if (!Domains.isValid(domain)) return Result.InvalidDomain
        repository.upsert(domain, kind)
        return Result.Added(domain)
    }
}
