package com.photostudio.pro.feature.whitebg

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.WhiteBgOps
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M6 — White Background. The marketplace-compliance tool: Amazon, eBay, Etsy and
 * Shopify all require a pure white (255,255,255) background for the main image.
 *
 * Classical flood-fill, not a model — see WhiteBgOps for why that distinction is
 * stated to the user rather than hidden.
 */
class WhiteBgViewModel : ViewModel() {

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data class Ready(val result: Bitmap, val coverage: Float, val wrongTool: Boolean) : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val ui: Ui = Ui.Idle,
        val strength: Float = 0.5f,
        val evenLighting: Boolean = true,
        val soften: Boolean = true,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var source: Bitmap? = null
    private var sourceUri: Uri? = null

    fun update(f: State.() -> State) {
        val prev = _state.value
        _state.value = prev.f()
        // Re-run only when a parameter that changes pixels actually changed.
        val s = _state.value
        if (source != null && _state.value.ui is Ui.Ready &&
            (prev.strength != s.strength || prev.evenLighting != s.evenLighting || prev.soften != s.soften)) {
            process()
        }
    }

    fun onPhotoPicked(ctx: Context, uri: Uri) {
        sourceUri = uri
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val bmp = withContext(Dispatchers.IO) { BitmapOps.loadSampled(ctx, uri, maxEdgePx = 2600) }
            if (bmp == null) { _state.value = _state.value.copy(ui = Ui.Failed("load_failed")); return@launch }
            source?.recycle(); source = bmp
            process()
        }
    }

    private fun process() {
        val src = source ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val s = _state.value
            val r = withContext(Dispatchers.Default) {
                runCatching { WhiteBgOps.makeWhite(src, s.strength, s.evenLighting, s.soften) }.getOrNull()
            }
            if (r == null) { _state.value = _state.value.copy(ui = Ui.Failed("process_failed")); return@launch }
            _state.value = _state.value.copy(ui = Ui.Ready(r.bitmap, r.coverage, r.probablyWrongTool))
        }
    }

    fun export(ctx: Context, activity: android.app.Activity?, treeUri: Uri?, onDone: (ExportFlow.Outcome) -> Unit) {
        val ui = _state.value.ui as? Ui.Ready ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val out = ExportFlow.run(
                ctx = ctx, activity = activity, source = ui.result,
                toolId = "whitebg", fileName = "whitebg_${System.currentTimeMillis()}",
                treeUri = treeUri,
            ) { _state.value = _state.value.copy(busy = false) }
            onDone(out)
        }
    }

    override fun onCleared() { source?.recycle(); super.onCleared() }
}
