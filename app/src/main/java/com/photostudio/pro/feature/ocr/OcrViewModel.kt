package com.photostudio.pro.feature.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M9 — Text Scanner (OCR).
 *
 * Text Recognition v2 is BUNDLED (16.0.1), so unlike Background Remover this
 * works offline from the very first run — no model download, no network check.
 *
 * Devanagari support is a real differentiator: most tools in this category ship
 * Latin only, and Hindi/Marathi document scanning is a genuine unmet need in the
 * largest Android market on earth.
 */
class OcrViewModel : ViewModel() {

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data class Found(val text: String, val script: OcrEngine.Script, val blocks: Int) : Ui
        data object Nothing : Ui
        data object LimitReached : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val ui: Ui = Ui.Idle,
        val uri: Uri? = null,
        val script: OcrEngine.Script = OcrEngine.Script.LATIN,
        val autoScript: Boolean = true,
        val remainingFree: Int = -1,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: OcrEngine? = null
    private fun engineFor(ctx: Context) = engine ?: OcrEngine(ctx).also { engine = it }

    init { refreshRemaining() }

    private fun refreshRemaining() {
        viewModelScope.launch {
            ServiceLocator.usage.refresh()
            val isPro = ServiceLocator.entitlements.snapshot().isPro
            _state.value = _state.value.copy(
                remainingFree = if (isPro) -1
                    else (ServiceLocator.flags.freeOcrScansPerDay - ServiceLocator.usage.ocrScansToday)
                        .coerceAtLeast(0))
        }
    }

    fun setScript(s: OcrEngine.Script) { _state.value = _state.value.copy(script = s, autoScript = false) }
    fun setAuto(on: Boolean) { _state.value = _state.value.copy(autoScript = on) }

    fun onPhotoPicked(ctx: Context, uri: Uri) {
        val s = _state.value
        // Free tier: N scans per day. -1 means Pro / unlimited.
        if (s.remainingFree == 0) {
            _state.value = s.copy(uri = uri, ui = Ui.LimitReached)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(uri = uri, ui = Ui.Working, busy = true)
            val res = withContext(Dispatchers.Default) {
                runCatching {
                    val e = engineFor(ctx)
                    if (_state.value.autoScript) {
                        val (text, script) = e.recogniseAuto(uri)
                        Triple(text, script, text.split('\n').count { it.isNotBlank() })
                    } else {
                        val text = e.recognise(uri, _state.value.script)
                        Triple(text, _state.value.script, text.split('\n').count { it.isNotBlank() })
                    }
                }.getOrNull()
            }
            _state.value = _state.value.copy(busy = false)
            if (res == null) {
                _state.value = _state.value.copy(ui = Ui.Failed("ocr_failed"))
                return@launch
            }
            if (res.first.isBlank()) {
                _state.value = _state.value.copy(ui = Ui.Nothing)
                return@launch
            }
            _state.value = _state.value.copy(
                ui = Ui.Found(res.first, res.second, res.third),
                script = res.second,
            )
            // Count the scan only after text actually came back, so a failed
            // attempt does not consume a free user's daily allowance.
            ServiceLocator.prefs.noteOcrScan()
            com.photostudio.pro.analytics.Events.logOcrCompleted(res.second.name.lowercase(), res.first.length)
            refreshRemaining()
        }
    }

    override fun onCleared() { engine?.close(); super.onCleared() }
}
