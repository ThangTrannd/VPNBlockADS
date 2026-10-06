package com.vpnblockads.core.model

enum class RuleKind { ALLOW, BLOCK }

/** Quy tắc người dùng tự thêm: luôn cho qua (whitelist) hoặc luôn chặn. */
data class CustomRule(
    val domain: String,
    val kind: RuleKind,
    val addedAtMillis: Long,
)
