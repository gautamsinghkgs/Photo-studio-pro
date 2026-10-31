package com.photostudio.pro.feature.bgremover

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photostudio.pro.R
import com.photostudio.pro.core.image.BitmapOps
import com.photostudio.pro.core.image.ExportOps
import com.photostudio.pro.core.ui.BusyOverlay
import com.photostudio.pro.core.ui.EmptyState
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.ui.PrimaryAction
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun BgRemoverScreen(
    adsReady: Boolean,
    onBack: () -> Unit,
    onGoPro: () -> Unit,
    vm: BgRemoverViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val entitlements by ServiceLocator.entitlements.state.collectAsState()

    LaunchedEffect(Unit) { vm.prepare(ctx) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.onPhotoPicked(ctx, uri) }

    // Where the export lands. Null = share sheet; a tree Uri = a folder the user
    // chose, which is usually what "save my cut-out" means. BG Remover used to be
    // share-sheet only while four other tools could save to a folder (README §6 P1).
    val picked = remember { mutableStateOf<android.net.Uri?>(null) }
    val folder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            // Take the persistable grant, otherwise the folder stops working after a
            // restart and the next export silently falls back to the share sheet.
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            picked.value = tree
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_bgremover),
        onBack = onBack,
        isPro = entitlements.isPro,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val ui = state.ui) {

                    is BgRemoverViewModel.Ui.Idle,
                    is BgRemoverViewModel.Ui.CheckingModel -> EmptyState(
                        icon = Icons.Filled.AddPhotoAlternate,
                        headline = stringResource(R.string.tool_bgremover),
                        body = stringResource(R.string.tool_bgremover_desc),
                        actionLabel = stringResource(R.string.pick_photo),
                        onAction = { picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )

                    is BgRemoverViewModel.Ui.Downloading -> {
                        NoticeCard(stringResource(R.string.bg_model_downloading_body))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.bg_model_downloading),
                             fontWeight = FontWeight.SemiBold)
                    }

                    is BgRemoverViewModel.Ui.NeedsNetwork -> EmptyState(
                        icon = Icons.Filled.CloudOff,
                        headline = stringResource(R.string.bg_model_needs_network),
                        body = stringResource(R.string.bg_model_needs_network_body),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { vm.prepare(ctx) },
                    )

                    is BgRemoverViewModel.Ui.Unsupported -> EmptyState(
                        icon = Icons.Filled.ImageNotSupported,
                        headline = "Not supported on this device",
                        body = "Background removal needs Google Play services. Your other tools still work — " +
                               "and your photos are never uploaded.",
                        actionLabel = stringResource(R.string.later_back),
                        onAction = onBack,
                    )

                    is BgRemoverViewModel.Ui.SourceReady,
                    is BgRemoverViewModel.Ui.Working -> {
                        (state.ui as? BgRemoverViewModel.Ui.SourceReady)?.preview?.let {
                            PreviewCard(it, stringResource(R.string.before))
                        }
                    }

                    is BgRemoverViewModel.Ui.Done -> {
                        PreviewCard(ui.composite, stringResource(R.string.after))
                        BackgroundPicker(
                            selected = state.bg,
                            onSelect = { vm.setBackground(it) },
                        )
                        Text(stringResource(R.string.bg_coverage, (ui.coverage * 100).toInt()),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)

                        val activity = ctx as? Activity
                        PrimaryAction(stringResource(R.string.export)) {
                            // ⭐ NATURAL BREAK POINT — but the ad decision now lives in
                            // ExportFlow.run, not here. Calling afterExportCompleted from
                            // the Screen as well would fire TWO decisions for one export
                            // (README §6 P1, "the double-ad trap"). `adsReady` is
                            // deliberately not consulted any more either: whether an ad
                            // may show is AdPolicy's answer, not the Screen's.
                            vm.export(ctx, activity, picked.value, ExportOps.Format.PNG) { out ->
                                if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                            }
                        }
                        TextButton(onClick = { folder.launch(null) },
                                   modifier = Modifier.fillMaxWidth()) {
                            Text(if (picked.value == null) stringResource(R.string.batch_choose_folder)
                                 else stringResource(R.string.batch_save_to))
                        }
                    }

                    is BgRemoverViewModel.Ui.Empty -> EmptyState(
                        icon = Icons.Filled.ImageNotSupported,
                        headline = stringResource(R.string.bg_result_empty),
                        body = stringResource(R.string.bg_result_empty_body),
                        actionLabel = stringResource(R.string.pick_photo),
                        onAction = { picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )

                    is BgRemoverViewModel.Ui.Failed -> EmptyState(
                        icon = Icons.Filled.ImageNotSupported,
                        headline = stringResource(R.string.err_generic),
                        body = ui.message,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
            }
            BusyOverlay(stringResource(R.string.processing),
                state.ui is BgRemoverViewModel.Ui.Working || state.exporting)
        }
    }
}

@Composable
private fun PreviewCard(bmp: Bitmap, label: String) {
    // Checkerboard behind so transparency is visible — without it a transparent
    // PNG looks like a white background and users think the tool failed.
    val checker = androidx.compose.ui.graphics.painter.BitmapPainter(
        BitmapOps.checkerboard(bmp.width.coerceAtMost(720), bmp.height.coerceAtMost(720)).asImageBitmap())
    Box(
        Modifier.fillMaxWidth().height(320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(checker, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Text(label, Modifier.align(Alignment.TopStart).padding(10.dp)
            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BackgroundPicker(
    selected: BgRemoverViewModel.BgChoice,
    onSelect: (BgRemoverViewModel.BgChoice) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            BgRemoverViewModel.BgChoice.TRANSPARENT to stringResource(R.string.bg_transparent),
            BgRemoverViewModel.BgChoice.WHITE       to "White",
            BgRemoverViewModel.BgChoice.BLACK       to "Black",
        ).forEach { (choice, label) ->
            FilterChip(selected = selected == choice, onClick = { onSelect(choice) }, label = { Text(label) })
        }
    }
}
