package com.photostudio.pro.feature.batch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * M3 — Batch Resize & Compress. THE SUBSCRIPTION DRIVER.
 *
 * Free = 1 photo per batch. Pro = 100. This is the highest-intent paywall in
 * the app because the user has already selected 40 photos and can see exactly
 * what they are missing. A rewarded ad offers +5 photos, which is the
 * deliberately small, temporary reward that keeps abuse unprofitable.
 *
 * MEMORY: processed strictly sequentially or in a pool of at most 2.
 * Parallelising 100 decodes is how photo apps die with OOM on mid-range
 * devices. SPEC.md architectural rule #4.
 */
class BatchViewModel : ViewModel() {

    enum class Mode { LONGEST_EDGE, EXACT_WIDTH, PERCENT, TARGET_SIZE }

    data class Item(
        val uri: Uri,
        val name: String,
        val inBytes: Long = 0,
        val outBytes: Long = 0,
        val done: Boolean = false,
        val error: Boolean = false,
    )

    data class State(
        val items: List<Item> = emptyList(),
        val mode: Mode = Mode.LONGEST_EDGE,
        val longestEdge: Int = 1080,
        val exactWidth: Int = 1200,
        val percent: Int = 50,
        val targetKb: Int = 200,
        val quality: Int = 88,
        val format: ExportOps.Format = ExportOps.Format.JPEG,
        val running: Boolean = false,
        val finished: Boolean = false,
        val totalIn: Long = 0,
        val totalOut: Long = 0,
        val allowedCount: Int = 1,
        val isPro: Boolean = false,
        /** SAF tree the user picked. Null until they choose a folder. */
        val treeUri: Uri? = null,
        val folderLabel: String? = null,
    ) {
        val savedBytes get() = (totalIn - totalOut).coerceAtLeast(0)
        val blockedCount get() = (items.size - allowedCount).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    companion object { const val PRO_BATCH_LIMIT = 100 }

    fun refreshEntitlement() {
        val isPro = ServiceLocator.entitlements.snapshot().isPro
        _state.value = _state.value.copy(
            isPro = isPro,
            allowedCount = if (isPro) PRO_BATCH_LIMIT
                           else ServiceLocator.flags.freeBatchSize + ServiceLocator.usage.batchExtraToday,
        )
    }

    fun onPhotosPicked(ctx: Context, uris: List<Uri>) {
        refreshEntitlement()
        val items = uris.take(PRO_BATCH_LIMIT).mapIndexed { i, u ->
            Item(u, nameFor(ctx, u, i), inBytes = sizeOf(ctx, u))
        }
        _state.value = _state.value.copy(items = items, finished = false, totalIn = items.sumOf { it.inBytes },
                                         totalOut = 0)
    }

    fun update(transform: State.() -> State) { _state.value = _state.value.transform() }

    /** +5 photos granted by a rewarded ad. Small, temporary, single-batch. */
    fun addBatchBonus(n: Int) {
        viewModelScope.launch {
            ServiceLocator.prefs.addBatchExtra(n)
            ServiceLocator.usage.refresh()
            refreshEntitlement()
        }
    }

    /**
     * Take a persistable grant so the chosen folder still works after a restart,
     * then remember both the Uri and a human-readable label for it.
     */
    fun setTreeUri(ctx: Context, tree: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        _state.value = _state.value.copy(treeUri = tree, folderLabel = labelFor(ctx, tree))
    }

    private fun labelFor(ctx: Context, tree: Uri): String {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, tree)
        return doc?.name ?: tree.lastPathSegment ?: "Selected folder"
    }

    /**
     * Run the batch. One run = ONE commercial event: a single daily-cap check, a
     * single export counted, a single analytics event, a single ad decision at the
     * end. All four live in [ExportFlow.runBatch].
     *
     * What stays here is the per-file work, which is this tool's actual product
     * (four resize modes plus a KB target). What used to stay here and should not
     * have: `noteExport()` was called on every run but the daily cap was NEVER
     * checked, so a free user could resize unlimited photos all day by running
     * one-file batches (bug #30).
     *
     * The Screen must not call `afterExportCompleted` — `runBatch` already does, and
     * a second call would fire two ad decisions for one export (README §6 P1).
     */
    fun run(
        ctx: Context,
        activity: android.app.Activity?,
        treeUri: Uri?,
        onFinished: (ExportFlow.BatchOutcome) -> Unit,
    ) {
        if (_state.value.running) return
        // Never fall back to cacheDir: Android wipes it under storage pressure
        // and the user cannot browse to it. Fail loudly instead.
        if (treeUri == null) return onFinished(ExportFlow.BatchOutcome.Failed("no_folder"))
        _state.value = _state.value.copy(running = true, finished = false, totalOut = 0)

        viewModelScope.launch {
            val s = _state.value

            // Sequential on purpose — see the class doc. ExportFlow drives the loop so
            // that the cap check and the ad decision cannot drift out of this file.
            val out = ExportFlow.runBatch(
                activity = activity,
                toolId = "batch",
                fileCount = s.items.size,
            ) { index ->
                val item = _state.value.items.getOrNull(index)
                when {
                    item == null -> -1L
                    // Beyond the free/Pro batch size the file is left untouched, and
                    // deliberately NOT marked failed: it was never attempted.
                    index >= s.allowedCount -> {
                        _state.value = _state.value.copy(
                            items = _state.value.items.toMutableList().also {
                                it[index] = item.copy(error = false)
                            })
                        -1L
                    }
                    else -> {
                        // processOne signals failure with a negative sentinel, and
                        // runCatching with null. Treat both as failure — recording -1
                        // as a successful export showed "✔ -1 B" in the list (bug #31).
                        val n = runCatching { processOne(ctx, item.uri, s, treeUri) }.getOrNull() ?: -1L
                        val ok = n >= 0
                        _state.value = _state.value.copy(
                            items = _state.value.items.toMutableList().also {
                                it[index] = if (ok) item.copy(outBytes = n, done = true)
                                            else item.copy(error = true)
                            },
                            totalOut = _state.value.totalOut + if (ok) n else 0L)
                        if (ok) n else -1L
                    }
                }
            }

            _state.value = _state.value.copy(
                running = false,
                // NeedsPro means nothing was written, so this is not a "finished" run:
                // the Export button must stay live behind the paywall.
                finished = out !is ExportFlow.BatchOutcome.NeedsPro,
            )
            onFinished(out)
        }
    }

    private fun processOne(ctx: Context, uri: Uri, s: State, treeUri: Uri?): Long {
        val src = BitmapOps.loadSampled(ctx, uri, maxEdgePx = 4096) ?: return -1L
        try {
            val scaled: Bitmap = when (s.mode) {
                Mode.LONGEST_EDGE -> BitmapOps.scaleToLongestEdge(src, s.longestEdge, recycleSource = true)
                Mode.EXACT_WIDTH  -> {
                    val h = (src.height.toFloat() * s.exactWidth / src.width).toInt().coerceAtLeast(1)
                    BitmapOps.scaleExact(src, s.exactWidth, h, recycleSource = true)
                }
                Mode.PERCENT -> {
                    val w = (src.width * s.percent / 100f).toInt().coerceAtLeast(1)
                    val h = (src.height * s.percent / 100f).toInt().coerceAtLeast(1)
                    BitmapOps.scaleExact(src, w, h, recycleSource = true)
                }
                Mode.TARGET_SIZE -> src   // quality is searched below instead
            }

            val (bytes, quality) = if (s.mode == Mode.TARGET_SIZE) {
                ExportOps.searchQualityForTargetSize(
                    targetBytes = s.targetKb * 1024, tolerancePct = 10,
                ) { q -> ExportOps.encode(scaled, s.format, q).size }
                    .let { (sz, q) -> ExportOps.encode(scaled, s.format, q) to q }
            } else {
                ExportOps.encode(scaled, s.format, s.quality) to s.quality
            }

            val name = baseName(item_name(uri)) + "_${scaled.width}x${scaled.height}.${s.format.ext}"
            // SAF only. There is deliberately no cacheDir fallback — see run().
            requireNotNull(treeUri) { "no output folder chosen" }
            ExportOps.saveToTree(ctx, treeUri, name, bytes, s.format.mime)
            if (scaled !== src) scaled.recycle()
            return bytes.size.toLong()
        } finally {
            runCatching { if (!src.isRecycled) src.recycle() }
        }
    }

    private fun item_name(uri: Uri) = uri.lastPathSegment ?: "photo"
    private fun baseName(s: String) = s.substringBeforeLast('.', s).take(40)

    private fun nameFor(ctx: Context, uri: Uri, i: Int): String {
        val seg = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return seg ?: "photo_${i + 1}"
    }

    private fun sizeOf(ctx: Context, uri: Uri): Long = runCatching {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)
}
