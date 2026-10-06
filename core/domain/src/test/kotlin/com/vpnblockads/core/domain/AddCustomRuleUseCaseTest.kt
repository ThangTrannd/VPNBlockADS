package com.vpnblockads.core.domain

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.domain.usecase.AddCustomRuleUseCase
import com.vpnblockads.core.model.RuleKind
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddCustomRuleUseCaseTest {
    private val repository = mockk<CustomRuleRepository>(relaxed = true)
    private val useCase = AddCustomRuleUseCase(repository)

    @Test
    fun `normalizes domain before saving`() = runTest {
        val result = useCase("  *.Ads.Example.COM. ", RuleKind.ALLOW)

        assertThat(result).isEqualTo(AddCustomRuleUseCase.Result.Added("ads.example.com"))
        coVerify { repository.upsert("ads.example.com", RuleKind.ALLOW) }
    }

    @Test
    fun `rejects invalid domain`() = runTest {
        assertThat(useCase("not a domain", RuleKind.BLOCK)).isEqualTo(AddCustomRuleUseCase.Result.InvalidDomain)
        assertThat(useCase("", RuleKind.BLOCK)).isEqualTo(AddCustomRuleUseCase.Result.InvalidDomain)
        coVerify(exactly = 0) { repository.upsert(any(), any()) }
    }
}
