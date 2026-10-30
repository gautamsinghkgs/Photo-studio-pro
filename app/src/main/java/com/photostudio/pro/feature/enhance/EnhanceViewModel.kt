package com.photostudio.pro.feature.enhance

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.EnhanceOps
import com.photostudio.pro.core.util.ExportFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M10 — Photo Enhance.
 *
 * ⚠️ NAMING AND CLAIMS. Every operation here is a classical image algorithm:
 * histogram auto-levels, grey-world white balance, unsharp masking, and an
 * edge-preserving denoise. None of it is machine learning and none of it is
 * generative.
 *
 * That is why this tool is called "Photo Enhance" and NOT "AI Enhance", "AI
 * Restore" or "AI Retouch". Play's AI & deception policy names "AI photo
 * enhancers" as facing immediate rejection, and users who expect Remini-grade
 * restoration from a classical sharpen leave 1-star reviews that push the rating
 * below the 4.0 ASO floor. The honest-limitations string is shown in the UI.
 */
class EnhanceViewModel : ViewModel() {

    data class Adjust(
        val brightness: Int = 0,        // -100..100
        val contrast: Float = 0f,       // -1..1
        val saturation: Float = 0f,     // -1..1
        val sharpness: Float = 0f,      // 0..1.5
        val denoise: Int = 0,           // 0..60
    )

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data class Ready(val preview: Bitmap) : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val ui: Ui = Ui.Idle,
        val adj: Adjust = Adjust(),
        val autoApplied: Boolean = false,
        val busy: Boolean = false,
        val showOriginal: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var source: Bitmap? = null

    fun onPhotoPicked(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val bmp = withContext(Dispatchers.IO) { BitmapOps.loadSampled(ctx, uri, maxEdgePx = 2600) }
            if (bmp == null) { _state.value = _state.value.copy(ui = Ui.Failed("load_failed")); return@launch }
            source?.recycle(); source = bmp
            _state.value = _state.value.copy(adj = Adjust(), autoApplied = false)
            render()
        }
    }

    fun update(f: Adjust.() -> Adjust) {
        _state.value = _state.value.copy(adj = _state.value.adj.f())
        render()
    }

    fun auto() {
        _state.value = _state.value.copy(adj = Adjust(), autoApplied = true)
        render(auto = true)
    }

    fun reset() {
        _state.value = _state.value.copy(adj = Adjust(), autoApplied = false)
        render()
    }

    fun setCompare(on: Boolean) { _state.value = _state.value.copy(showOriginal = on) }

    /**
     * Re-render the preview. Works on a downscaled copy so slider drags stay
     * responsive; the export re-runs the same pipeline at full resolution.
     */
    private fun render(auto: Boolean = false) {
        val src = source ?: return
        viewModelScope.launch {
            val a = _state.value.adj
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    val small = if (src.width > 1200 || src.height > 1200)
                        BitmapOps.scaleToLongestEdge(src, 1200) else src
                    val base = if (auto) EnhanceOps.auto(small) else small
                    EnhanceOps.adjust(
                        base,
                        brightness = a.brightness,
                        contrast = a.contrast,
                        saturation = a.saturation,
                        sharpness = a.sharpness,
                        denoise = a.denoise,
                    ).also { if (it !== base && base !== small) base.recycle() }
                }.getOrNull()
            }
            if (bmp == null) { _state.value = _state.value.copy(ui = Ui.Failed("render_failed")); return@launch }
            _state.value = _state.value.copy(ui = Ui.Ready(bmp))
        }
    }

    /** Full-resolution export through the shared money path. */
    fun export(ctx: Context, activity: android.app.Activity?, treeUri: Uri?,
               onDone: (ExportFlow.Outcome) -> Unit) {
        val src = source ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val a = _state.value.adj
            val full = withContext(Dispatchers.Default) {
                runCatching {
                    val base = if (_state.value.autoApplied) EnhanceOps.auto(src) else src
                    EnhanceOps.adjust(base, a.brightness, a.contrast, a.saturation, a.sharpness, a.denoise)
                }.getOrNull()
            }
            if (full == null) {
                _state.value = _state.value.copy(busy = false)
                return@launch onDone(ExportFlow.Outcome.Failed("render_failed"))
            }
            val out = ExportFlow.run(
                ctx = ctx, activity = activity, source = full,
                toolId = "enhance", fileName = "enhanced_${System.currentTimeMillis()}",
                treeUri = treeUri,
            ) {
                _state.value = _state.value.copy(busy = false)
                if (full !== src) full.recycle()
            }
            onDone(out)
        }
    }

    /** The untouched source, for the hold-to-compare gesture. */
    fun original(): Bitmap? = source

    override fun onCleared() { source?.recycle(); super.onCleared() }
}
