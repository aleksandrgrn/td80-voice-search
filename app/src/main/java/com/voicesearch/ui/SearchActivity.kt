package com.voicesearch.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var pendingVoiceStart = false

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.voiceButton.isEnabled = true
            maybeStartVoiceSearch()
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
        binding.btnSimulateVoice.setOnClickListener {
            simulateVoiceSearch()
        }

        // SpeechRecognizer initialization + voice button setup
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(voiceRecognitionListener)
            binding.voiceButton.isEnabled = true
            binding.voiceButton.contentDescription = getString(R.string.voice_search_button)
        } else {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            binding.voiceButton.isEnabled = false
            binding.voiceButton.contentDescription = getString(R.string.voice_error_not_available)
            Toast.makeText(this, R.string.voice_error_not_available, Toast.LENGTH_LONG).show()
        }
        binding.voiceButton.imageTintList = ContextCompat.getColorStateList(this, R.color.state_voice_mic_tint)
        binding.voiceButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                maybeStartVoiceSearch()
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

        if (fromAssistKey && SpeechRecognizer.isRecognitionAvailable(this)) {
            pendingVoiceStart = true
        }

        // Check accessibility service
        checkAccessibilityServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceStatus()
        // lifecycle-aware auto-start (R4 fix)
        if (pendingVoiceStart && SpeechRecognizer.isRecognitionAvailable(this)) {
            pendingVoiceStart = false
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                maybeStartVoiceSearch()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
        val fromAssistKey = intent?.getBooleanExtra(AssistantService.EXTRA_FROM_ASSIST_KEY, false) ?: false
        Log.i(TAG, "SearchActivity re-launched via onNewIntent, fromAssistKey=$fromAssistKey")
        if (fromAssistKey) {
            pendingVoiceStart = true  // lifecycle-aware: реальный старт в onResume
        }
    }

    override fun onPause() {
        super.onPause()
        // Остановить распознавание если Activity уходит в background
        if (isListening) {
            stopListening()
        }
        pendingVoiceStart = false  // сбросить флаг — пользователь должен нажать кнопку сам
    }

    override fun onDestroy() {
        super.onDestroy()
        // R1 mitigation: prevent SpeechRecognizer leak
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // ===== Voice recognition =====

    private val voiceRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "SpeechRecognizer: ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "SpeechRecognizer: beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Hook for future VU meter animation
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // No-op
        }

        override fun onEndOfSpeech() {
            Log.i(TAG, "SpeechRecognizer: end of speech")
            // Do NOT call setListeningState(false) here — onEndOfSpeech is informational.
            // A stale onEndOfSpeech from a prior stopListening()/cancel() can arrive
            // after a new session has started (setListeningState(true)), which would
            // incorrectly reset the UI state. The final state will be set by
            // onResults() or onError() for the current session.
            binding.searchInput.hint = getString(R.string.voice_processing_hint)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank() && text != binding.searchInput.text.toString()) {
                binding.searchInput.setText(text)
                // НЕ вызываем performSearch() на partial results
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            setListeningState(false)
            binding.searchInput.hint = getString(R.string.search_hint)
            if (!text.isNullOrBlank()) {
                binding.searchInput.setText(text)
                performSearch()
            } else {
                Toast.makeText(this@SearchActivity, R.string.voice_error_no_match, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(errorCode: Int) {
            // Guard against stale ERROR_CLIENT from a prior cancel() — if we're
            // actively listening in a new session, that error belongs to the old one.
            if (errorCode == SpeechRecognizer.ERROR_CLIENT && isListening) {
                Log.d(TAG, "SpeechRecognizer: ignoring stale ERROR_CLIENT (new session active)")
                return
            }

            setListeningState(false)
            binding.searchInput.hint = getString(R.string.search_hint)
            Log.e(TAG, "SpeechRecognizer error: $errorCode (${errorCodeToString(errorCode)})")

            when (errorCode) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    speechRecognizer?.cancel()
                    Toast.makeText(this@SearchActivity, R.string.voice_error_speech_busy, Toast.LENGTH_SHORT).show()
                }
                SpeechRecognizer.ERROR_NO_MATCH ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_no_match, Toast.LENGTH_SHORT).show()
                SpeechRecognizer.ERROR_NETWORK ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_network, Toast.LENGTH_SHORT).show()
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_timeout, Toast.LENGTH_SHORT).show()
                SpeechRecognizer.ERROR_CLIENT ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_client, Toast.LENGTH_SHORT).show()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_permission, Toast.LENGTH_SHORT).show()
                else ->
                    Toast.makeText(this@SearchActivity, R.string.voice_error_client, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Deprecated, no-op
        }

        private fun errorCodeToString(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
            else -> "UNKNOWN($code)"
        }
    }

    private fun maybeStartVoiceSearch() {
        if (isListening) {
            // Уже слушаем → остановить (toggle behaviour)
            stopListening()
            return
        }

        // R2 mitigation: cancel-before-start
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            setListeningState(true)
            Log.i(TAG, "SpeechRecognizer: startListening (ru-RU, partial=true)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            setListeningState(false)
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        setListeningState(false)
        binding.searchInput.hint = getString(R.string.search_hint)
        Log.i(TAG, "SpeechRecognizer: stopped")
    }

    private fun setListeningState(listening: Boolean) {
        isListening = listening
        binding.voiceButton.isActivated = listening
        if (listening) {
            binding.searchInput.hint = getString(R.string.voice_listening_hint)
        }
        // hint сбрасывается вызывающим кодом при выходе из listening
    }

    private fun simulateVoiceSearch() {
        val simulatedText = "Матрица"
        Log.i(TAG, "Debug: simulating voice search with query='$simulatedText'")
        binding.searchInput.setText(simulatedText)
        performSearch()
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

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Performing search: $query")
        }
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
