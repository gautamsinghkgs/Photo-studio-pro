package com.photostudio.pro.feature.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.image.PdfOps
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M8 — Image ↔ PDF, via PdfBox-Android (Apache 2.0), entirely on-device.
 *
 * No cloud conversion service. That matters here more than for the other tools:
 * the documents people convert are invoices, ID documents, forms and contracts —
 * exactly the files least willing to be uploaded to a stranger's server.
 */
class PdfViewModel : ViewModel() {

    enum class Direction { IMAGES_TO_PDF, PDF_TO_IMAGES }

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data class Progress(val done: Int, val total: Int) : Ui
        data class PdfReady(val bytes: ByteArray, val pages: Int) : Ui
        data class ImagesReady(val bitmaps: List<Bitmap>) : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val direction: Direction = Direction.IMAGES_TO_PDF,
        val photos: List<Bitmap> = emptyList(),
        val photoNames: List<String> = emptyList(),
        val pageSize: String = "A4",
        val fit: PdfOps.Fit = PdfOps.Fit.FIT,
        val marginPt: Float = 24f,
        val dpi: Int = 150,
        val ui: Ui = Ui.Idle,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(f: State.() -> State) { _state.value = _state.value.f() }

    fun onImagesPicked(ctx: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val bmps = withContext(Dispatchers.IO) {
                uris.mapNotNull { BitmapOps.loadSampled(ctx, it, maxEdgePx = 2400) }
            }
            _state.value.photos.forEach { runCatching { it.recycle() } }
            _state.value = _state.value.copy(
                photos = bmps,
                photoNames = uris.mapIndexed { i, u -> u.lastPathSegment?.substringAfterLast('/') ?: "page_${i + 1}" },
                ui = if (bmps.isEmpty()) Ui.Failed("load_failed") else Ui.Idle,
            )
        }
    }

    fun onPdfPicked(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working, busy = true)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                _state.value = _state.value.copy(ui = Ui.Failed("pdf_read_failed"), busy = false); return@launch
            }
            val out = ArrayList<Bitmap>()
            val n = withContext(Dispatchers.Default) {
                runCatching {
                    PdfOps.pdfToImages(ctx, bytes, dpi = _state.value.dpi, maxPages = 100) { i, total, bmp ->
                        out.add(bmp)
                        _state.value = _state.value.copy(ui = Ui.Progress(i + 1, total))
                    }
                }.getOrElse {
                    // Failure path (corrupt page, JPX image, OOM): the pages decoded
                    // before the throw are ours to clean up, or they leak as bitmaps.
                    out.forEach { runCatching { it.recycle() } }
                    out.clear()
                    -1
                }
            }
            _state.value = _state.value.copy(
                busy = false,
                ui = if (n <= 0) Ui.Failed("pdf_render_failed") else Ui.ImagesReady(out),
            )
        }
    }

    /** Build the PDF in memory; the caller decides where to write it. */
    fun buildPdf(ctx: Context) {
        val s = _state.value
        if (s.photos.isEmpty()) { _state.value = s.copy(ui = Ui.Failed("no_images")); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, ui = Ui.Working)
            val res = withContext(Dispatchers.Default) {
                runCatching {
                    PdfOps.imagesToPdf(ctx, s.photos, s.pageSize, s.fit, s.marginPt)
                }.getOrNull()
            }
            _state.value = _state.value.copy(busy = false)
            if (res == null) { _state.value = _state.value.copy(ui = Ui.Failed("pdf_build_failed")); return@launch }
            val pages = s.photos.size
            ServiceLocator.prefs.noteExport()
            ServiceLocator.usage.refresh()
            com.photostudio.pro.analytics.Events.logExportCompleted(
                "pdf", pages, 0, res.size, ServiceLocator.entitlements.snapshot().isPro)
            _state.value = _state.value.copy(ui = Ui.PdfReady(res, pages))
        }
    }

    /** Write the built PDF, or the extracted images, into a user-picked folder. */
    fun saveTo(ctx: Context, treeUri: Uri): Boolean {
        val s = _state.value
        return when (val ui = s.ui) {
            is Ui.PdfReady -> ExportOps.saveToTree(
                ctx, treeUri, "document_${System.currentTimeMillis()}.pdf", ui.bytes, "application/pdf") != null
            is Ui.ImagesReady -> ui.bitmaps.mapIndexed { i, bmp -> i to bmp }.all { (i, bmp) ->
                val bytes = ExportOps.encode(bmp, ExportOps.Format.JPEG, 92)
                ExportOps.saveToTree(ctx, treeUri, "page_${i + 1}.jpg", bytes, "image/jpeg") != null
            }
            else -> false
        }
    }

    override fun onCleared() {
        _state.value.photos.forEach { runCatching { it.recycle() } }
        (_state.value.ui as? Ui.ImagesReady)?.bitmaps?.forEach { runCatching { it.recycle() } }
        super.onCleared()
    }
}
