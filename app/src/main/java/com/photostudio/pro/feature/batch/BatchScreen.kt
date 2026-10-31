package com.photostudio.pro.feature.batch

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photostudio.pro.R
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.ui.BusyOverlay
import com.photostudio.pro.core.ui.EmptyState
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.ui.PrimaryAction
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import com.photostudio.pro.monetization.ads.RewardGuard
import kotlinx.coroutines.launch

@Composable
fun BatchScreen(
    adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
    vm: BatchViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = ctx as? Activity

    LaunchedEffect(Unit) { vm.refreshEntitlement() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BatchViewModel.PRO_BATCH_LIMIT)
    ) { uris -> if (uris.isNotEmpty()) vm.onPhotosPicked(ctx, uris) }

    /**
     * ⚠️ The output FOLDER must be chosen by the user through SAF.
     *
     * Writing to `cacheDir` instead is a real data-loss bug: Android deletes
     * cache contents whenever storage runs low, and the user has no way to
     * browse to it. `OpenDocumentTree` returns a tree Uri we can persist with
     * `takePersistableUriPermission`, so the folder survives app restarts and
     * the user always knows where their resized photos went.
     */
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree -> if (tree != null) vm.setTreeUri(ctx, tree) }

    ToolScaffold(stringResource(R.string.tool_batch), onBack, state.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (state.items.isEmpty()) {
                EmptyState(Icons.Filled.PhotoLibrary, stringResource(R.string.tool_batch),
                    stringResource(R.string.tool_batch_desc), stringResource(R.string.pick_photos),
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text(stringResource(R.string.batch_selected_count, state.items.size), fontWeight = FontWeight.SemiBold)

                    // ---- The free/Pro boundary, stated plainly ----
                    if (state.blockedCount > 0) {
                        NoticeCard(stringResource(R.string.batch_free_limit_body))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (adsReady && activity != null) scope.launch {
                                    // ⭐ The primary rewarded placement. RewardGuard
                                    // enforces the daily cap on the GRANT side.
                                    ServiceLocator.adCoordinator.requestRewarded(
                                        activity = activity,
                                        kind = RewardGuard.RewardKind.BATCH_PLUS_5,
                                        onGranted = { vm.addBatchBonus(5) },
                                        onDenied = { onGoPro() },
                                        onError = { onGoPro() },
                                    )
                                } else onGoPro()
                            }) { Text(stringResource(R.string.batch_watch_ad), fontSize = 13.sp) }
                            TextButton(onClick = onGoPro) {
                                Text(stringResource(R.string.batch_go_pro), fontSize = 13.sp)
                            }
                        }
                    }

                    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.batch_mode), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        BatchViewModel.Mode.entries.forEach { m ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = state.mode == m,
                                    onClick = { vm.update { copy(mode = m) } })
                                Text(labelFor(m), fontSize = 13.sp)
                            }
                        }
                        when (state.mode) {
                            BatchViewModel.Mode.LONGEST_EDGE -> SliderRow("Longest edge",
                                state.longestEdge.toFloat(), 320f, 4096f,
                                { vm.update { copy(longestEdge = it.toInt()) } }) { "${state.longestEdge} px" }
                            BatchViewModel.Mode.EXACT_WIDTH -> SliderRow("Width",
                                state.exactWidth.toFloat(), 200f, 4096f,
                                { vm.update { copy(exactWidth = it.toInt()) } }) { "${state.exactWidth} px" }
                            BatchViewModel.Mode.PERCENT -> SliderRow("Scale",
                                state.percent.toFloat(), 10f, 100f,
                                { vm.update { copy(percent = it.toInt()) } }) { "${state.percent}%" }
                            BatchViewModel.Mode.TARGET_SIZE -> SliderRow(stringResource(R.string.batch_target_size),
                                state.targetKb.toFloat(), 20f, 2000f,
                                { vm.update { copy(targetKb = it.toInt()) } }) { "${state.targetKb} KB" }
                        }
                        if (state.mode != BatchViewModel.Mode.TARGET_SIZE) {
                            SliderRow("Quality", state.quality.toFloat(), 40f, 100f,
                                { vm.update { copy(quality = it.toInt()) } }) { "${state.quality}" }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Switch(checked = state.format == ExportOps.Format.PNG,
                                onCheckedChange = { vm.update {
                                    copy(format = if (it) ExportOps.Format.PNG else ExportOps.Format.JPEG) } })
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(stringResource(R.string.batch_format_png), fontSize = 12.sp)
                        }
                        // Not a toggle. Bitmap.compress() cannot carry EXIF, so
                        // exports are always stripped — an OFF switch here would be
                        // a false promise about data the app never keeps.
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(stringResource(R.string.batch_strip_exif), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }

                    if (state.finished) {
                        NoticeCard(stringResource(R.string.batch_summary,
                            state.items.count { it.done },
                            humanBytes(state.totalIn), humanBytes(state.savedBytes)))
                    }

                    Card { Row(Modifier.fillMaxWidth().padding(14.dp),
                                 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                 horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.batch_save_to), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(state.folderLabel ?: stringResource(R.string.batch_save_to_none),
                                 fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { folderPicker.launch(null) }) {
                            Text(stringResource(R.string.batch_choose_folder), fontSize = 13.sp)
                        }
                    } }

                    PrimaryAction(if (state.finished) stringResource(R.string.done) else stringResource(R.string.export),
                                  enabled = state.treeUri != null) {
                        if (state.finished) onBack()
                        else vm.run(ctx, activity, treeUri = state.treeUri) { out ->
                            // ⭐ Export finished = natural break point — but the ad
                            // decision belongs to ExportFlow.runBatch now. Calling
                            // afterExportCompleted here as well would fire TWO decisions
                            // for one batch (README §6 P1, the double-ad trap).
                            // The rewarded "+5 photos" button above still uses
                            // `adsReady` and `scope`; that is a different placement.
                            if (out is ExportFlow.BatchOutcome.NeedsPro) onGoPro()
                        }
                    }
                    TextButton(onClick = { picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.batch_add_more), fontSize = 13.sp)
                    }

                    state.items.take(60).forEachIndexed { i, it ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${i + 1}. ${it.name}", fontSize = 11.5.sp, maxLines = 1,
                                 modifier = Modifier.weight(1f))
                            Text(when {
                                it.error -> "✖"; it.done -> "✔ ${humanBytes(it.outBytes)}"
                                i >= state.allowedCount -> "🔒"
                                else -> "·"
                            }, fontSize = 11.5.sp)
                        }
                    }
                }
            }
            BusyOverlay(stringResource(R.string.processing), state.running)
        }
    }
}

private fun labelFor(m: BatchViewModel.Mode) = when (m) {
    BatchViewModel.Mode.LONGEST_EDGE -> "Longest edge (px)"
    BatchViewModel.Mode.EXACT_WIDTH  -> "Exact width (px)"
    BatchViewModel.Mode.PERCENT      -> "Percentage"
    BatchViewModel.Mode.TARGET_SIZE  -> "Target file size"
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float,
                      onChange: (Float) -> Unit, valueLabel: () -> String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp); Text(valueLabel(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

private fun humanBytes(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024      -> "%.0f KB".format(b / 1024.0)
    else           -> "$b B"
}
