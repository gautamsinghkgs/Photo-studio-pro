package com.photostudio.pro.feature.idphoto

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.EnhanceOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M2 — Passport / ID Photo. The install magnet: "passport photo" is a
 * high-volume, low-competition search term, and it is the reason a seller who
 * came for background removal keeps the app.
 *
 * Face Detection is BUNDLED (16.1.7) so this works offline from the very first
 * run — unlike Subject Segmentation.
 */
class IdPhotoViewModel : ViewModel() {

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data object NoFace : Ui
        data object MultipleFaces : Ui
        data class Ready(
            val cropPreview: Bitmap,
            val printSheet: Bitmap,
            val copies: Int,
            val checks: List<IdPhotoMath.Check>,
            val outW: Int, val outH: Int, val estKb: Int,
        ) : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val ui: Ui = Ui.Idle,
        val spec: CountrySpec? = null,
        val sheetName: String = "4x6 in",
        val query: String = "",
        val results: List<CountrySpec> = emptyList(),
        val exporting: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var source: Bitmap? = null
    private var sourceUri: Uri? = null

    init {
        // Priority-1 markets first so the picker is useful without scrolling.
        _state.value = _state.value.copy(results = ServiceLocator.countries.priorityOnes())
    }

    fun search(q: String) {
        _state.value = _state.value.copy(query = q, results = ServiceLocator.countries.search(q))
    }

    fun selectSpec(spec: CountrySpec) {
        // Free tier is limited to N specs; Pro unlocks all 30+.
        val isPro = ServiceLocator.entitlements.snapshot().isPro
        if (!isPro) {
            val free = ServiceLocator.countries.freeSubset(ServiceLocator.flags.freeIdPhotoCountries)
            if (free.none { it.id == spec.id }) {
                _state.value = _state.value.copy(spec = spec)
                return   // caller shows the paywall; see needsProFor(spec)
            }
        }
        _state.value = _state.value.copy(spec = spec)
        source?.let { process(it, spec, _state.value.sheetName) }
    }

    fun needsProFor(spec: CountrySpec): Boolean {
        if (ServiceLocator.entitlements.snapshot().isPro) return false
        return ServiceLocator.countries.freeSubset(ServiceLocator.flags.freeIdPhotoCountries)
            .none { it.id == spec.id }
    }

    fun setSheet(name: String) {
        _state.value = _state.value.copy(sheetName = name)
        val s = source; val spec = _state.value.spec
        if (s != null && spec != null) process(s, spec, name)
    }

    fun onPhotoPicked(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working)
            val bmp = withContext(Dispatchers.IO) { BitmapOps.loadSampled(ctx, uri, maxEdgePx = 2600) }
            if (bmp == null) { _state.value = _state.value.copy(ui = Ui.Failed("load_failed")); return@launch }
            source?.recycle(); source = bmp; sourceUri = uri

            val spec = _state.value.spec ?: ServiceLocator.countries.priorityOnes().firstOrNull()
                ?: ServiceLocator.countries.all().first()
            _state.value = _state.value.copy(spec = spec)

            val engine = FaceEngine(ctx)
            val faces = runCatching { withContext(Dispatchers.Default) { engine.detectBitmap(bmp) } }
                .getOrDefault(emptyList())
            engine.close()

            when {
                faces.isEmpty() -> _state.value = _state.value.copy(ui = Ui.NoFace)
                faces.size > 1  -> _state.value = _state.value.copy(ui = Ui.MultipleFaces)
                else -> process(bmp, spec, _state.value.sheetName, faces.first())
            }
        }
    }

    private fun process(
        src: Bitmap, spec: CountrySpec, sheetName: String,
        face: FaceEngine.FaceGeometry? = null,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val out = runCatching {
                val f = face
                val cropRect = if (f != null) {
                    IdPhotoMath.computeCropWithBox(
                        src.width, src.height,
                        f.box.left, f.box.top, f.box.right, f.box.bottom,
                        spec.widthMm, spec.heightMm,
                    )
                } else null

                val cropped = if (cropRect != null)
                    BitmapOps.crop(src, cropRect[0], cropRect[1], cropRect[2], cropRect[3]) ?: src
                else src

                // Exact output pixel size for this spec, at its DPI.
                val photo = BitmapOps.scaleExact(cropped, spec.widthPx, spec.heightPx,
                    recycleSource = cropped !== src)

                // Normalise the background toward the spec colour. Sellers and
                // students routinely shoot against a coloured wall; this is the
                // single most-asked-for fix in 1-star reviews of competitors.
                val cleaned = EnhanceOps.autoWhiteBalance(photo)
                if (cleaned !== photo) photo.recycle()

                // Try BOTH orientations and keep whichever yields more copies.
                // Rotating a 35x45 photo onto a 4x6 sheet gives 8 instead of 6,
                // which is free paper and ink for the user and the kind of detail
                // that earns a 5-star review from someone printing visa photos.
                // The photo itself is never rotated (a sideways face is invalid);
                // only the tiling grid is transposed.
                val dims = IdPhotoMath.SHEETS[sheetName]
                val straight = if (dims != null)
                    IdPhotoMath.fitOnSheet(dims.first, dims.second, spec.widthMm, spec.heightMm)
                    else Triple(0, 0, 0)
                val rotated = if (dims != null)
                    IdPhotoMath.fitOnSheet(dims.first, dims.second, spec.heightMm, spec.widthMm)
                    else Triple(0, 0, 0)
                val useRotated = rotated.third > straight.third
                val chosen = if (useRotated) rotated else straight
                val cols = if (useRotated) chosen.second else chosen.first
                val rows = if (useRotated) chosen.first else chosen.second
                val copies = chosen.third

                val sheet = buildPrintSheet(cleaned, sheetName, cols, rows, spec.dpi, rotated = useRotated)

                val checks = buildList {
                    if (f != null && spec.hasHeadBand) {
                        val headPct = f.headHeightPx.toDouble() /
                                (cropRect?.get(3)?.toDouble() ?: src.height.toDouble())
                        add(IdPhotoMath.checkHeadSize(headPct, spec.headMinPct, spec.headMaxPct))
                    }
                    if (f != null && spec.hasEyeBand) {
                        val eyeFromTop = (f.eyeLineY - (cropRect?.get(1) ?: 0)).toDouble() /
                                (cropRect?.get(3)?.toDouble() ?: src.height.toDouble())
                        add(IdPhotoMath.checkEyeLine(eyeFromTop, spec.eyeMinPct, spec.eyeMaxPct))
                    }
                    val border = EnhanceOps.averageBorder(cleaned)
                    add(IdPhotoMath.checkBackground(border, spec.backgroundArgb))
                }

                val estKb = IdPhotoMath.estimateJpegBytes(spec.widthPx, spec.heightPx) / 1024
                PhotoOut(cleaned, sheet, copies, checks, spec.widthPx, spec.heightPx, estKb)
            }.getOrNull()

            if (out == null) { _state.value = _state.value.copy(ui = Ui.Failed("process_failed")); return@launch }
            _state.value = _state.value.copy(
                ui = Ui.Ready(out.photo, out.sheet, out.copies, out.checks, out.outW, out.outH, out.estKb)
            )
        }
    }

    private data class PhotoOut(
        val photo: Bitmap, val sheet: Bitmap, val copies: Int,
        val checks: List<IdPhotoMath.Check>, val outW: Int, val outH: Int, val estKb: Int,
    )

    /**
     * Lay [copies] photos on a print sheet at the sheet's real DPI. Printing at
     * the wrong DPI is the most common reason a home-printed ID photo is the
     * wrong physical size, so the DPI is taken from the sheet definition and
     * the sheet is built at exactly that resolution.
     */
    private fun buildPrintSheet(
        photo: Bitmap, sheetName: String, cols: Int, rows: Int, dpi: Int, rotated: Boolean,
    ): Bitmap {
        if (cols <= 0 || rows <= 0) return photo
        val (swMm, shMm) = IdPhotoMath.SHEETS[sheetName] ?: return photo
        val wPx = IdPhotoMath.mmToPx(swMm, dpi)
        val hPx = IdPhotoMath.mmToPx(shMm, dpi)
        // Guard against an absurd allocation on a huge sheet.
        if (wPx.toLong() * hPx > 40_000_000L) return photo

        val sheet = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sheet)
        c.drawColor(android.graphics.Color.WHITE)
        val marginPx = IdPhotoMath.mmToPx(3.0, dpi)
        val gapPx = IdPhotoMath.mmToPx(2.0, dpi)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        // Cut lines: a hairline grid so the user can trim accurately.
        val line = android.graphics.Paint().apply {
            color = 0xFFCCCCCC.toInt(); strokeWidth = 1f; style = android.graphics.Paint.Style.STROKE
        }
        // When tiling rotated, each cell is photo.height wide and photo.width
        // tall, and the bitmap is drawn through a 90-degree matrix.
        val cellW = if (rotated) photo.height else photo.width
        val cellH = if (rotated) photo.width else photo.height
        val matrix = android.graphics.Matrix().apply {
            if (rotated) postRotate(90f)
        }
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                val x = marginPx + col * (cellW + gapPx)
                val y = marginPx + r * (cellH + gapPx)
                if (x + cellW > wPx || y + cellH > hPx) continue
                if (rotated) {
                    c.save()
                    c.translate(x.toFloat(), y.toFloat())
                    c.drawBitmap(photo, matrix, paint)
                    c.restore()
                } else {
                    c.drawBitmap(photo, x.toFloat(), y.toFloat(), paint)
                }
                c.drawRect(x.toFloat(), y.toFloat(), (x + cellW).toFloat(), (y + cellH).toFloat(), line)
            }
        }
        c.setBitmap(null)
        return sheet
    }

    /**
     * Export. Now routed through [ExportFlow.run] like every other image tool.
     *
     * The KB ceiling stays the reason this tool exists: government portals hard-reject
     * oversized uploads, and hitting that ceiling is the detail that earns a 5-star
     * review from a student applying for a visa. `Plan.FixedDimensions` hands the
     * quality search to ExportFlow so it runs on the FINAL pixels (after any
     * watermark), and tells it to skip the free-tier 1080px downscale — these files
     * are validated on pixel dimensions, so shrinking them would break the very check
     * the tool exists to pass.
     *
     * Do NOT call `AdCoordinator.afterExportCompleted` from the Screen: `run` already
     * does, and both export buttons share this one path.
     */
    fun export(
        ctx: Context,
        activity: android.app.Activity?,
        treeUri: Uri?,
        asSheet: Boolean,
        onDone: (ExportFlow.Outcome) -> Unit,
    ) {
        val ui = _state.value.ui as? Ui.Ready ?: return onDone(ExportFlow.Outcome.Failed("no_image"))
        val spec = _state.value.spec ?: return onDone(ExportFlow.Outcome.Failed("no_spec"))

        _state.value = _state.value.copy(exporting = true)
        viewModelScope.launch {
            val out = ExportFlow.run(
                ctx = ctx, activity = activity,
                source = if (asSheet) ui.printSheet else ui.cropPreview,
                toolId = "idphoto",
                fileName = "idphoto_${spec.id}_${if (asSheet) "sheet" else "single"}" +
                           "_${System.currentTimeMillis()}",
                format = ExportOps.Format.JPEG,
                treeUri = treeUri,
                plan = ExportFlow.Plan.FixedDimensions(maxBytes = spec.maxKb * 1024),
            ) { _state.value = _state.value.copy(exporting = false) }
            _state.value = _state.value.copy(exporting = false)
            onDone(out)
        }
    }

    override fun onCleared() { source?.recycle(); super.onCleared() }
}
