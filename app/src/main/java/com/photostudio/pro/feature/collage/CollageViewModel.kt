package com.photostudio.pro.feature.collage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.CollageOps
import com.photostudio.pro.core.util.ExportFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M7 — Collage / Templates.
 *
 * This is the app's main rewarded-ad surface. A locked template unlocks for 24h
 * after one video (RewardKind.TEMPLATE_24H). That pairing is deliberate: it is a
 * small, temporary reward, so forging the client-side callback buys 24 hours of
 * layouts the user could otherwise get by watching the video. There is no path
 * from a rewarded ad to Pro — see RewardGuardTest.noDangerousRewardKindCanEverBeAdded.
 */
class CollageViewModel : ViewModel() {

    sealed interface Ui {
        data object Idle : Ui
        data object Working : Ui
        data class Ready(val preview: Bitmap) : Ui
        data class Failed(val message: String) : Ui
    }

    data class State(
        val photos: List<Bitmap> = emptyList(),
        val uris: List<Uri> = emptyList(),
        val templateId: String = CollageOps.templates.first().id,
        val ratioName: String = CollageOps.ratios.keys.first(),
        val spacing: Int = 12,
        val border: Int = 24,
        val corner: Int = 8,
        val bgColor: Int = Color.WHITE,
        /** Set when a rewarded ad unlocks all templates; holds the expiry. */
        val unlockedUntil: Long = 0L,
        val ui: Ui = Ui.Idle,
        val busy: Boolean = false,
    ) {
        val template: CollageOps.Template
            get() = CollageOps.byId(templateId) ?: CollageOps.templates.first()

        val templatesUnlocked: Boolean
            get() = unlockedUntil > System.currentTimeMillis()

        /** Enough photos for the chosen layout? */
        val hasEnough: Boolean get() = photos.size >= template.count

        /** A template the user may use right now. */
        fun isTemplateAvailable(t: CollageOps.Template, isPro: Boolean): Boolean =
            !t.pro || isPro || templatesUnlocked
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(f: State.() -> State) {
        _state.value = _state.value.f()
        if (_state.value.photos.isNotEmpty()) render()
    }

    /** Called by the rewarded-ad grant path with a 24h window. */
    fun unlockAllTemplates(durationMs: Long) {
        _state.value = _state.value.copy(unlockedUntil = System.currentTimeMillis() + durationMs)
    }

    fun onPhotosPicked(ctx: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ui = Ui.Working, uris = uris)
            // Decode sequentially: 20 parallel decodes is how photo apps OOM on
            // mid-range devices.
            val bmps = withContext(Dispatchers.IO) {
                uris.mapNotNull { BitmapOps.loadSampled(ctx, it, maxEdgePx = 1600) }
            }
            _state.value.photos.forEach { runCatching { it.recycle() } }
            _state.value = _state.value.copy(photos = bmps)
            if (bmps.isEmpty()) { _state.value = _state.value.copy(ui = Ui.Failed("load_failed")); return@launch }
            render()
        }
    }

    private fun render() {
        val s = _state.value
        if (s.photos.isEmpty()) return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    val (rw, rh) = CollageOps.ratios[s.ratioName] ?: (1f to 1f)
                    // Preview is capped at 1280 on the long edge; export goes higher.
                    val long = 1280
                    val w: Int; val h: Int
                    if (rw >= rh) { w = long; h = (long * rh / rw).toInt().coerceAtLeast(1) }
                    else          { h = long; w = (long * rw / rh).toInt().coerceAtLeast(1) }
                    CollageOps.render(s.photos, s.template, w, h, s.spacing, s.border, s.corner, s.bgColor)
                }.getOrNull()
            }
            _state.value = _state.value.copy(
                ui = if (bmp == null) Ui.Failed("render_failed") else Ui.Ready(bmp))
        }
    }

    fun export(ctx: Context, activity: android.app.Activity?, treeUri: Uri?, isPro: Boolean,
               onDone: (ExportFlow.Outcome) -> Unit) {
        val s = _state.value
        if (s.photos.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val full = withContext(Dispatchers.Default) {
                runCatching {
                    val (rw, rh) = CollageOps.ratios[s.ratioName] ?: (1f to 1f)
                    // Pro gets 4K on the long edge; free is capped by ExportFlow anyway.
                    val long = if (isPro) 3840 else 2048
                    val w: Int; val h: Int
                    if (rw >= rh) { w = long; h = (long * rh / rw).toInt().coerceAtLeast(1) }
                    else          { h = long; w = (long * rw / rh).toInt().coerceAtLeast(1) }
                    CollageOps.render(s.photos, s.template, w, h, s.spacing, s.border, s.corner, s.bgColor)
                }.getOrNull()
            }
            if (full == null) {
                _state.value = _state.value.copy(busy = false)
                return@launch onDone(ExportFlow.Outcome.Failed("render_failed"))
            }
            val out = ExportFlow.run(
                ctx = ctx, activity = activity, source = full,
                toolId = "collage", fileName = "collage_${s.template.id}_${System.currentTimeMillis()}",
                treeUri = treeUri,
            ) {
                _state.value = _state.value.copy(busy = false)
                full.recycle()
            }
            onDone(out)
        }
    }

    override fun onCleared() {
        _state.value.photos.forEach { runCatching { it.recycle() } }
        super.onCleared()
    }
}
