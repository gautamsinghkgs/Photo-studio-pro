package com.photostudio.pro.feature.batch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun run(ctx: Context, treeUri: Uri?, onFinished: () -> Unit) {
        if (_state.value.running) return
        // Never fall back to cacheDir: Android wipes it under storage pressure
        // and the user cannot browse to it. Fail loudly instead.
        if (treeUri == null) return onFinished()
        _state.value = _state.value.copy(running = true, finished = false, totalOut = 0)

        viewModelScope.launch {
            val s = _state.value
            val allowed = s.allowedCount
            var outTotal = 0L

            // Sequential on purpose. See the class doc.
            s.items.forEachIndexed { index, item ->
                if (index >= allowed) {
                    _state.value = _state.value.copy(
                        items = _state.value.items.toMutableList().also {
                            it[index] = item.copy(error = false)   // left untouched, not marked failed
                        })
                    return@forEachIndexed
                }
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { processOne(ctx, item.uri, s, treeUri) }.getOrNull()
                }
                if (bytes == null) {
                    _state.value = _state.value.copy(items = _state.value.items.toMutableList().also {
                        it[index] = item.copy(error = true)
                    })
                } else {
                    outTotal += bytes
                    _state.value = _state.value.copy(items = _state.value.items.toMutableList().also {
                        it[index] = item.copy(outBytes = bytes, done = true)
                    }, totalOut = outTotal)
                }
            }

            val done = _state.value.items.count { it.done }
            ServiceLocator.prefs.noteExport()
            ServiceLocator.usage.refresh()
            com.photostudio.pro.analytics.Events.logExportCompleted(
                "batch", done, 0, outTotal.toInt(), _state.value.isPro)
            _state.value = _state.value.copy(running = false, finished = true)
            onFinished()
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
