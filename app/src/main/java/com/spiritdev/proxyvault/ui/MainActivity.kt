package com.spiritdev.proxyvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.spiritdev.proxyvault.R
import com.spiritdev.proxyvault.databinding.ActivityMainBinding
import com.spiritdev.proxyvault.model.ProxyItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ProxyAdapter

    private var fullList: List<ProxyItem> = emptyList()
    private var activeFilter: Filter = Filter.ALL
    private var searchQuery: String = ""

    private enum class Filter { ALL, HTTP, SOCKS, FAST }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ProxyAdapter(
            onCopy = { copyToClipboard(it); pulse(binding.tvStatus) },
            onTest = { viewModel.testSingle(it) }
        )
        binding.recyclerProxies.layoutManager = LinearLayoutManager(this)
        binding.recyclerProxies.adapter = adapter

        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent_green))
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.card_bg))
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.btnRefresh.setOnClickListener { animateClick(it); viewModel.refresh() }

        binding.btnExport.setOnClickListener {
            animateClick(it)
            val (ok, payload) = viewModel.buildExportText()
            if (!ok && payload.startsWith("No working")) { toast(payload); return@setOnClickListener }
            if (!ok && payload.startsWith("Free limit")) {
                val list = viewModel.workingProxies.value.take(50)
                copyToClipboardRaw(list.joinToString("\n") { p -> p.address })
                toast(payload)
                return@setOnClickListener
            }
            copyToClipboardRaw(payload)
            toast("Exported ${viewModel.workingProxies.value.size} proxies")
        }

        binding.btnRedeem.setOnClickListener { openRedeem() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilters()
            }
        })

        binding.chipAll.setOnClickListener { activeFilter = Filter.ALL; applyFilters() }
        binding.chipHttp.setOnClickListener { activeFilter = Filter.HTTP; applyFilters() }
        binding.chipSocks.setOnClickListener { activeFilter = Filter.SOCKS; applyFilters() }
        binding.chipFast.setOnClickListener { activeFilter = Filter.FAST; applyFilters() }

        observeState()
        viewModel.refresh()
    }

    private fun applyFilters() {
        var list = fullList
        list = when (activeFilter) {
            Filter.ALL -> list
            Filter.HTTP -> list.filter { it.protocol.equals("http", true) || it.protocol.equals("https", true) }
            Filter.SOCKS -> list.filter { it.protocol.contains("socks", ignoreCase = true) }
            Filter.FAST -> list.filter { it.speedMs in 0 until 300 }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.address.contains(searchQuery, true) ||
                    it.source.contains(searchQuery, true) ||
                    it.countryCode.contains(searchQuery, true) ||
                    it.protocol.contains(searchQuery, true)
            }
        }
        adapter.submit(list)
        binding.tvCount.text = "${list.size} working"
        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerProxies.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_redeem -> { openRedeem(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openRedeem() {
        RedeemDialog {
            viewModel.refreshTierLabel()
            toast("Tier updated")
            pulse(binding.tvTier)
        }.show(supportFragmentManager, "redeem")
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.workingProxies.collectLatest { list ->
                fullList = list
                applyFilters()
                binding.tvLastRefresh.text = "Last: ${viewModel.lastRefreshLabel}"
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnRefresh.isEnabled = !loading
                binding.swipeRefresh.isRefreshing = loading
            }
        }
        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { msg ->
                if (msg.isNotBlank()) binding.tvStatus.text = msg
            }
        }
        lifecycleScope.launch {
            viewModel.tierLabel.collectLatest { tier -> binding.tvTier.text = tier }
        }
    }

    private fun copyToClipboard(proxy: ProxyItem) {
        copyToClipboardRaw(proxy.address)
        toast("Copied ${proxy.address}")
    }

    private fun copyToClipboardRaw(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("proxy", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun animateClick(v: View) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    private fun pulse(v: View) {
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }
}
