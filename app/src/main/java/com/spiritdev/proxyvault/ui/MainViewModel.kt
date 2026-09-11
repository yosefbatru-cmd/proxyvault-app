package com.spiritdev.proxyvault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spiritdev.proxyvault.data.LicenseManager
import com.spiritdev.proxyvault.data.Tier
import com.spiritdev.proxyvault.model.ProxyItem
import com.spiritdev.proxyvault.network.ProxyFetcher
import com.spiritdev.proxyvault.network.ProxyValidator
import com.spiritdev.proxyvault.network.SourceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val fetcher = ProxyFetcher()
    private val validator = ProxyValidator()
    private val ctx get() = getApplication<Application>()

    private val _workingProxies = MutableStateFlow<List<ProxyItem>>(emptyList())
    val workingProxies: StateFlow<List<ProxyItem>> = _workingProxies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _tierLabel = MutableStateFlow("FREE")
    val tierLabel: StateFlow<String> = _tierLabel.asStateFlow()

    var lastRefreshLabel: String = "Never"
        private set

    init { refreshTierLabel() }

    fun refreshTierLabel() {
        _tierLabel.value = when (LicenseManager.currentTier(ctx)) {
            Tier.FREE -> "FREE"
            Tier.PREMIUM -> "PREMIUM"
            Tier.PRO -> "PRO"
            Tier.LIFETIME -> "LIFETIME"
        }
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val cap = LicenseManager.validationCap(ctx)
            _statusMessage.value = "Fetching from ${SourceCatalog.DEFAULT.size} sources…"
            try {
                val raw = fetcher.fetchAll(SourceCatalog.DEFAULT)
                _statusMessage.value = "Fetched ${raw.size}. Validating (cap $cap)…"
                val alive = validator.validateBatch(raw.take(cap), concurrencyHint = 32)
                _workingProxies.value = alive
                lastRefreshLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                _statusMessage.value = "Done. ${alive.size} live. [${_tierLabel.value}]"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message ?: "unknown"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun testSingle(proxy: ProxyItem) {
        viewModelScope.launch {
            _statusMessage.value = "Testing ${proxy.address}…"
            val result = validator.validateBatch(listOf(proxy), concurrencyHint = 1)
            if (result.isNotEmpty() && result[0].isAlive) {
                _statusMessage.value = "${proxy.address} OK — ${result[0].speedMs}ms"
                val current = _workingProxies.value.toMutableList()
                if (current.none { it.address == proxy.address }) {
                    current.add(0, result[0])
                    _workingProxies.value = current
                }
            } else {
                _statusMessage.value = "${proxy.address} dead"
            }
        }
    }

    fun buildExportText(): Pair<Boolean, String> {
        val list = _workingProxies.value
        if (list.isEmpty()) return false to "No working proxies"
        val limit = LicenseManager.exportLimit(ctx)
        val slice = list.take(limit)
        val text = slice.joinToString("\n") { it.address }
        return if (list.size > limit) {
            false to "Free limit: exported ${slice.size}/${list.size}. Upgrade for unlimited."
        } else {
            true to text
        }
    }
}
