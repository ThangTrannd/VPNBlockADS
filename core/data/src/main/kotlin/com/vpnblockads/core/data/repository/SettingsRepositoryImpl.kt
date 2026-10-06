package com.vpnblockads.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpnblockads.core.domain.repository.SettingsRepository
import com.vpnblockads.core.model.AppSettings
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.UpstreamDns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            upstream = when (prefs[UPSTREAM]) {
                "google" -> UpstreamDns.Google
                "quad9" -> UpstreamDns.Quad9
                "custom" -> prefs[UPSTREAM_CUSTOM]?.let(UpstreamDns::Custom) ?: UpstreamDns.Cloudflare
                else -> UpstreamDns.Cloudflare
            },
            blockMode = prefs[BLOCK_MODE]
                ?.let { runCatching { BlockResponseMode.valueOf(it) }.getOrNull() }
                ?: BlockResponseMode.NXDOMAIN,
            blocklistUrl = prefs[BLOCKLIST_URL] ?: AppSettings.DEFAULT_BLOCKLIST_URL,
        )
    }.distinctUntilChanged()

    override suspend fun setUpstream(upstream: UpstreamDns) {
        dataStore.edit { prefs ->
            prefs[UPSTREAM] = when (upstream) {
                UpstreamDns.Cloudflare -> "cloudflare"
                UpstreamDns.Google -> "google"
                UpstreamDns.Quad9 -> "quad9"
                is UpstreamDns.Custom -> "custom".also { prefs[UPSTREAM_CUSTOM] = upstream.address }
            }
        }
    }

    override suspend fun setBlockMode(mode: BlockResponseMode) {
        dataStore.edit { it[BLOCK_MODE] = mode.name }
    }

    override suspend fun setBlocklistUrl(url: String) {
        dataStore.edit { it[BLOCKLIST_URL] = url }
    }

    private companion object {
        val UPSTREAM = stringPreferencesKey("upstream")
        val UPSTREAM_CUSTOM = stringPreferencesKey("upstream_custom")
        val BLOCK_MODE = stringPreferencesKey("block_mode")
        val BLOCKLIST_URL = stringPreferencesKey("blocklist_url")
    }
}
