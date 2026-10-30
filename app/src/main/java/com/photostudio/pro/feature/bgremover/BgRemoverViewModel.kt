package com.photostudio.pro.feature.bgremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.image.MaskOps
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M1 Background Remover.
 *
 * The state machine below exists because Subject Segmentation is UNBUNDLED and
 * Google documents that "requests made before the download has completed
 * produce NO RESULTS" — silently. Without these explicit states the user would
 * see a blank image and assume the app is broken.
 */
class BgRemoverViewModel : ViewModel() {

    sealed interface Ui {
        data object Idle : Ui
        data object CheckingModel : Ui
        data class Downloading(val label: String) : Ui
        data object NeedsNetwork : Ui
        data object Unsupported : Ui
        data class SourceReady(val uri: Uri, val preview: Bitmap) : Ui
        data object Working : Ui
        data class Done(val foreground: Bitmap, val composite: Bitmap, val coverage: Float) : Ui
        data class Empty(val coverage: Float) : Ui
        data class Failed(val message: String) : Ui
    }

    enum class BgChoice { TRANSPARENT, WHITE, BLACK, CUSTOM }

    data class State(
        val ui: Ui = Ui.Idle,
        val bg: BgChoice = BgChoice.TRANSPARENT,
        val customColor: Int = Color.WHITE,
        val exporting: Boolean = false,
        val lastExportUri: Uri? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: SubjectSegmentationEngine? = null
    private var sourceFull: Bitmap? = null
    private var foregroundFull: Bitmap? = null

    private fun engineFor(ctx: Context) = engine ?: SubjectSegmentationEngine(ctx).also { engine = it }

    /** Step 1: check the model is on the device before the user invests effort. */
    fun prepare(ctx: Context) {
        if (_state.value.ui !is Ui.Idle) return
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.CheckingModel)
            when (withContext(Dispatchers.IO) { engineFor(ctx).ensureModelReady() }) {
                SubjectSegmentationEngine.ModelState.READY -> {
                    // Google's own tip: the first inference is slower because of
                    // model initialisation. Warm it up now, off the user's clock.
                    launch(Dispatchers.Default) { engineFor(ctx).warmUp() }
                    _state.value = _state.value.copy(ui = Ui.Idle)
                }
                SubjectSegmentationEngine.ModelState.DOWNLOADING ->
                    _state.value = _state.value.copy(ui = Ui.Downloading("bg_model_downloading"))
                SubjectSegmentationEngine.ModelState.NEEDS_NETWORK ->
                    _state.value = _state.value.copy(ui = Ui.NeedsNetwork)
                SubjectSegmentationEngine.ModelState.UNSUPPORTED ->
                    _state.value = _state.value.copy(ui = Ui.Unsupported)
                SubjectSegmentationEngine.ModelState.UNKNOWN ->
                    _state.value = _state.value.copy(ui = Ui.Idle)
            }
        }
    }

    /** Step 2: user picked a photo. */
    fun onPhotoPicked(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    // Full resolution kept for export quality.
                    val full = BitmapOps.loadSampled(ctx, uri, maxEdgePx = 3000)
                        ?: return@runCatching null
                    sourceFull?.recycle()
                    sourceFull = full
                    val small = BitmapOps.loadSampled(ctx, uri,
                        maxEdgePx = SubjectSegmentationEngine.WORKING_EDGE) ?: full
                    small to full
                }.getOrNull()
            }
            if (out == null) { _state.value = _state.value.copy(ui = Ui.Failed("load_failed")); return@launch }
            _state.value = _state.value.copy(ui = Ui.SourceReady(uri, out.first))
            segment(ctx, uri)
        }
    }

    private fun segment(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val res = withContext(Dispatchers.Default) {
                runCatching { engineFor(ctx).segmentUri(uri) }.getOrNull()
            }
            if (res == null) {
                // Most likely cause: the module is still downloading and the
                // request silently produced nothing. Say so honestly.
                com.photostudio.pro.analytics.Events.logSegmentationModelNotReady("silent_empty_result")
                _state.value = _state.value.copy(ui = Ui.NeedsNetwork)
                return@launch
            }
            if (res.foreground == null || res.mask == null || res.coverage < 0.005f) {
                _state.value = _state.value.copy(ui = Ui.Empty(res.coverage))
                return@launch
            }
            withContext(Dispatchers.Default) { buildComposite(res) }
        }
    }

    /**
     * Rebuild the composite from the stored foreground when the user changes the
     * background choice, without re-running inference.
     */
    fun setBackground(choice: BgChoice, color: Int = Color.WHITE) {
        _state.value = _state.value.copy(bg = choice, customColor = color)
        val fg = foregroundFull ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val comp = compositeFor(fg, _state.value.bg, _state.value.customColor)
            _state.value = _state.value.copy(
                ui = (_state.value.ui as? Ui.Done)?.copy(composite = comp) ?: _state.value.ui
            )
        }
    }

    private fun buildComposite(res: SubjectSegmentationEngine.Result) {
        // res.foreground is at working resolution; upscale the ALPHA onto the
        // full-resolution source so exports stay sharp.
        val full = sourceFull ?: return
        val alpha = MaskOps.maskToAlpha(res.mask!!, res.maskWidth, res.maskHeight)
        val feathered = MaskOps.feather(alpha, res.maskWidth, res.maskHeight,
            radius = (res.maskWidth * 0.004f).toInt().coerceAtLeast(1), passes = 2)
        val cutout = MaskOps.applyAlpha(full, feathered, res.maskWidth, res.maskHeight)
        foregroundFull?.recycle()
        foregroundFull = cutout

        val comp = compositeFor(cutout, _state.value.bg, _state.value.customColor)
        _state.value = _state.value.copy(ui = Ui.Done(cutout, comp, res.coverage))
    }

    private fun compositeFor(fg: Bitmap, bg: BgChoice, color: Int): Bitmap = when (bg) {
        BgChoice.TRANSPARENT -> fg
        BgChoice.WHITE       -> BitmapOps.compositeOverColor(fg, Color.WHITE)
        BgChoice.BLACK       -> BitmapOps.compositeOverColor(fg, Color.BLACK)
        BgChoice.CUSTOM      -> BitmapOps.compositeOverColor(fg, color)
    }

    /**
     * Export. This is a NATURAL BREAK POINT, so it is the only place an
     * interstitial may be considered — and AdPolicy still decides.
     *
     * Free tier: SD + watermark + limited exports/day.
     * Pro: original resolution, no watermark, unlimited.
     */
    fun export(ctx: Context, format: ExportOps.Format, onNeedPro: () -> Unit, onDone: (Uri?) -> Unit) {
        val fg = foregroundFull ?: return onDone(null)
        val isPro = ServiceLocator.entitlements.snapshot().isPro
        val counters = ServiceLocator.usage

        if (!isPro && counters.exportsToday >= ServiceLocator.flags.freeExportsPerDay) return onNeedPro()

        _state.value = _state.value.copy(exporting = true)
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    // Free tier is capped at 1080px on the longest edge.
                    val quality = if (isPro) 96 else 88
                    // Free tier: capped at 1080px on the longest edge + watermark.
                    // Pro: original resolution, no watermark.
                    val sized = if (isPro) fg else BitmapOps.scaleToLongestEdge(fg, 1080)
                    val final = if (isPro) sized else ExportOps.watermark(sized, "Photo Studio Pro")
                    val bytes = ExportOps.encode(final, format, quality)
                    val uri = ExportOps.toShareUri(
                        ctx, final, "cutout_${System.currentTimeMillis()}", format, quality)
                    // Recycle the intermediates, never the source `fg`.
                    if (final !== sized) final.recycle()
                    if (sized !== fg) sized.recycle()
                    uri?.also {
                        ServiceLocator.prefs.noteExport()
                        ServiceLocator.usage.refresh()
                        com.photostudio.pro.analytics.Events.logExportCompleted(
                            "bgremove", fg.width, fg.height, bytes.size, isPro)
                    }
                }.getOrNull()
            }
            _state.value = _state.value.copy(exporting = false, lastExportUri = uri)
            onDone(uri)
        }
    }

    override fun onCleared() {
        engine?.close()
        sourceFull?.recycle(); foregroundFull?.recycle()
        super.onCleared()
    }
}
