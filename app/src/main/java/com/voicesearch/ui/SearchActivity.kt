package com.voicesearch.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voicesearch.BuildConfig
import com.voicesearch.R
import com.voicesearch.dispatch.IntentDispatcher
import com.voicesearch.databinding.ActivitySearchBinding
import com.voicesearch.provider.TmdbSearchProvider
import com.voicesearch.service.AssistantService
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceSearch"
    }

    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var tmdbProvider: TmdbSearchProvider
    private var hasShownAccessibilityDialog = false

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.voiceButton.isEnabled = true
            Toast.makeText(this, R.string.voice_search_future, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.voice_search_no_mic_permission, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TMDB provider
        tmdbProvider = TmdbSearchProvider(BuildConfig.TMDB_API_KEY)

        // Debug simulate button
        binding.btnSimulateVoice.visibility = if (BuildConfig.DEBUG) android.view.View.VISIBLE else android.view.View.GONE

        // Voice button: request permission on click
        binding.voiceButton.isEnabled = true
        binding.voiceButton.contentDescription = getString(R.string.voice_search_button)
        binding.voiceButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.voice_search_future, Toast.LENGTH_SHORT).show()
            } else {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Search input
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // RecyclerView setup
        searchAdapter = SearchAdapter { result ->
            onResultClick(result)
        }
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = searchAdapter
        }

        // App buttons
        setupAppButtons()

        // Log launch source
        val fromAssistKey = intent?.getBooleanExtra(AssistantService.EXTRA_FROM_ASSIST_KEY, false) ?: false
        Log.i(TAG, "SearchActivity launched, fromAssistKey=$fromAssistKey")

        // Check accessibility service
        checkAccessibilityServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
        val fromAssistKey = intent?.getBooleanExtra(AssistantService.EXTRA_FROM_ASSIST_KEY, false) ?: false
        Log.i(TAG, "SearchActivity re-launched via onNewIntent, fromAssistKey=$fromAssistKey")
        // TODO: restart voice search when fromAssistKey=true (task 5)
    }

    // ===== Accessibility =====

    private fun checkAccessibilityServiceStatus() {
        val isRunning = AssistantService.isRunning
        val isEnabledViaManager = checkEnabledViaAccessibilityManager()
        val serviceEnabled = isRunning || isEnabledViaManager

        Log.i(TAG, "AccessibilityService status: isRunning=$isRunning, " +
                "managerEnabled=$isEnabledViaManager, result=$serviceEnabled")

        updateServiceStatusText(serviceEnabled)

        if (!serviceEnabled && !hasShownAccessibilityDialog) {
            hasShownAccessibilityDialog = true
            showAccessibilityNotEnabledDialog()
        }
    }

    private fun checkEnabledViaAccessibilityManager(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any { it.id.startsWith("$packageName/") }
    }

    private fun updateServiceStatusText(enabled: Boolean) {
        if (enabled) {
            binding.serviceStatusText.text = getString(R.string.accessibility_service_active)
            binding.serviceStatusText.setTextColor(getColor(R.color.service_status_active))
        } else {
            binding.serviceStatusText.text = getString(R.string.accessibility_service_not_enabled)
            binding.serviceStatusText.setTextColor(getColor(R.color.service_status_inactive))
        }
    }

    private fun showAccessibilityNotEnabledDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_not_enabled_title)
            .setMessage(R.string.accessibility_not_enabled_message)
            .setPositiveButton(R.string.accessibility_open_settings) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open accessibility settings", e)
                }
            }
            .setNegativeButton(R.string.accessibility_skip, null)
            .setCancelable(true)
            .show()
    }

    // ===== Search =====

    private fun performSearch() {
        val query = binding.searchInput.text.toString().trim()
        if (query.isBlank()) return

        Log.i(TAG, "Performing search: $query")
        lifecycleScope.launch {
            try {
                val results = tmdbProvider.search(query)
                searchAdapter.submitList(results)
                binding.emptyStateText.visibility = if (results.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.resultsRecyclerView.visibility = if (results.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                searchAdapter.submitList(emptyList())
                binding.emptyStateText.visibility = android.view.View.VISIBLE
                binding.resultsRecyclerView.visibility = android.view.View.GONE
            }
        }
    }

    // ===== Result click → DetailActivity =====

    private fun onResultClick(result: com.voicesearch.model.SearchResult) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_TITLE, result.title)
            putExtra(DetailActivity.EXTRA_POSTER_URL, result.posterUrl)
            putExtra(DetailActivity.EXTRA_YEAR, result.year)
            putExtra(DetailActivity.EXTRA_OVERVIEW, result.overview)
            putExtra(DetailActivity.EXTRA_GENRE, result.metadata["genre"])
            putExtra(DetailActivity.EXTRA_DURATION, result.metadata["duration"])
        }
        startActivity(intent)
    }

    // ===== App buttons =====

    private fun setupAppButtons() {
        val allApps = IntentDispatcher.getAllApps()
        val installedApps = IntentDispatcher.getInstalledApps(this)
        val installedPackages = installedApps.map { it.packageName }.toSet()

        val buttonAppMap = mapOf(
            binding.btnNum to "ru.yourok.num",
            binding.btnSmartTube to "org.smarttube.stable",
            binding.btnLampa to "ru.yourok.lampa",
            binding.btnLazyMedia to "com.laxymedia.deluxe",
        )

        buttonAppMap.forEach { (button, packageName) ->
            val app = allApps.find { it.packageName == packageName }
            if (app != null && packageName in installedPackages) {
                button.text = app.displayName
                button.isEnabled = true
                button.setOnClickListener {
                    val query = binding.searchInput.text.toString().trim()
                    IntentDispatcher.launch(this, app, query)
                }
            } else {
                button.text = app?.displayName ?: packageName
                button.isEnabled = false
            }
        }
    }
}
