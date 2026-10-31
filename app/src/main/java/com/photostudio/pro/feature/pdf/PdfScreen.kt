package com.photostudio.pro.feature.pdf

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photostudio.pro.R
import com.photostudio.pro.core.image.PdfOps
import com.photostudio.pro.core.ui.BusyOverlay
import com.photostudio.pro.core.ui.EmptyState
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.ui.PrimaryAction
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun PdfScreen(adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
              vm: PdfViewModel = viewModel()) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val ent by ServiceLocator.entitlements.state.collectAsState()
    val saved = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = ctx as? Activity

    val imgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { if (it.isNotEmpty()) vm.onImagesPicked(ctx, it) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onPdfPicked(ctx, uri)
    }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            saved.value = vm.saveTo(ctx, tree)
            // NATURAL BREAK POINT. The save completed, so ask AdPolicy whether an
            // ad is allowed. `adsReady` was already plumbed in from Nav.kt but was
            // never read here, which meant M8 was the only one of the eight tools
            // that produced no ad decision at all. Never call AdManager directly.
            if (saved.value && adsReady && activity != null) {
                scope.launch {
                    ServiceLocator.adCoordinator.afterExportCompleted(activity) { }
                }
            }
        }
    }

    ToolScaffold(stringResource(R.string.tool_pdf), onBack, ent.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ---- direction ----
                Card { Column(Modifier.padding(12.dp)) {
                    PdfViewModel.Direction.entries.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = st.direction == d,
                                onClick = { vm.update { copy(direction = d, ui = PdfViewModel.Ui.Idle) } })
                            Text(if (d == PdfViewModel.Direction.IMAGES_TO_PDF)
                                    stringResource(R.string.pdf_to_pdf)
                                 else stringResource(R.string.pdf_to_images), fontSize = 13.sp)
                        }
                    }
                } }

                if (st.direction == PdfViewModel.Direction.IMAGES_TO_PDF) {

                    if (st.photos.isEmpty()) {
                        EmptyState(Icons.Filled.PictureAsPdf, stringResource(R.string.pdf_to_pdf),
                            stringResource(R.string.pdf_empty), stringResource(R.string.pick_photos),
                            { imgPicker.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    } else {
                        Text(stringResource(R.string.pdf_status_count, st.photos.size),
                             fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        st.photoNames.take(8).forEachIndexed { i, n ->
                            Text("${i + 1}. $n", fontSize = 11.sp, maxLines = 1,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(stringResource(R.string.pdf_page_size), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PdfOps.pageSizes.keys.take(3).forEach { p ->
                                FilterChip(selected = st.pageSize == p,
                                    onClick = { vm.update { copy(pageSize = p) } },
                                    label = { Text(p, fontSize = 11.sp) })
                            }
                        }

                        Card { Column(Modifier.padding(14.dp)) {
                            PdfOps.Fit.entries.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = st.fit == f,
                                        onClick = { vm.update { copy(fit = f) } })
                                    Text(if (f == PdfOps.Fit.FIT) stringResource(R.string.pdf_fit)
                                         else stringResource(R.string.pdf_fill), fontSize = 12.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.pdf_margin), fontSize = 12.sp)
                                Text("${st.marginPt.toInt()}pt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Slider(value = st.marginPt, onValueChange = { vm.update { copy(marginPt = it) } },
                                   valueRange = 0f..72f)
                        } }

                        PrimaryAction(stringResource(R.string.pdf_to_pdf), enabled = !st.busy) {
                            saved.value = false
                            vm.buildPdf(ctx)
                        }
                    }
                } else {
                    // PDF -> images
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pdf_render_dpi), fontSize = 12.sp)
                        Text("${st.dpi}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(value = st.dpi.toFloat(),
                           onValueChange = { vm.update { copy(dpi = (it / 25).toInt() * 25) } },
                           valueRange = 75f..300f, steps = 8)
                    PrimaryAction(stringResource(R.string.pdf_pick_pdf), enabled = !st.busy) {
                        saved.value = false
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                    Text(stringResource(R.string.pdf_dpi_note), fontSize = 11.sp,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                when (val ui = st.ui) {
                    is PdfViewModel.Ui.Progress -> Column {
                        Text(stringResource(R.string.pdf_rendering_progress, ui.done, ui.total), fontSize = 12.sp)
                        LinearProgressIndicator(progress = { ui.done.toFloat() / ui.total.coerceAtLeast(1) },
                                                modifier = Modifier.fillMaxWidth())
                    }
                    is PdfViewModel.Ui.PdfReady -> {
                        NoticeCard(stringResource(R.string.pdf_pages_out, ui.pages) +
                                   " · ${(ui.bytes.size / 1024)} KB")
                        PrimaryAction(stringResource(R.string.export)) { folder.launch(null) }
                    }
                    is PdfViewModel.Ui.ImagesReady -> {
                        NoticeCard(stringResource(R.string.pdf_images_out, ui.bitmaps.size))
                        PrimaryAction(stringResource(R.string.export)) { folder.launch(null) }
                    }
                    is PdfViewModel.Ui.Failed -> NoticeCard(stringResource(R.string.err_generic) + ": " + ui.message)
                    else -> Unit
                }

                if (saved.value) NoticeCard("Saved ✓")

                TextButton(onClick = {
                    if (st.direction == PdfViewModel.Direction.IMAGES_TO_PDF)
                        imgPicker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else pdfPicker.launch(arrayOf("application/pdf"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pick_photos), fontSize = 13.sp)
                }
            }
            BusyOverlay(stringResource(R.string.processing), st.busy)
        }
    }
}
