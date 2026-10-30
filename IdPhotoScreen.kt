package com.photostudio.pro.feature.idphoto

import android.app.Activity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photostudio.pro.R
import com.photostudio.pro.core.ui.BusyOverlay
import com.photostudio.pro.core.ui.EmptyState
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.ui.PrimaryAction
import com.photostudio.pro.core.ui.SecondaryAction
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator
import androidx.compose.material3.TextButton

@Composable
fun IdPhotoScreen(
    adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
    vm: IdPhotoViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val entitlements by ServiceLocator.entitlements.state.collectAsState()
    val activity = ctx as? Activity

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhotoPicked(ctx, uri)
    }

    // Where the export lands: null = share sheet, a tree Uri = a folder the user
    // chose. ID Photo used to be share-sheet only (README §6 P1), which for the print
    // sheet is the worst possible default — the user then has to re-save eight copies
    // from a viewer app instead of getting one file in their own folder.
    val picked = remember { mutableStateOf<android.net.Uri?>(null) }
    val folder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            // Persist the grant, or the folder silently stops working after a restart.
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            picked.value = tree
        }
    }

    ToolScaffold(stringResource(R.string.tool_idphoto), onBack, entitlements.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ⚠️ This disclaimer is a compliance requirement, not decoration.
                // Never claim guaranteed acceptance (Play Deceptive Behavior).
                NoticeCard(stringResource(R.string.id_disclaimer))

                OutlinedTextField(
                    value = state.query, onValueChange = vm::search,
                    label = { Text(stringResource(R.string.id_search_country)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results) { spec ->
                        FilterChip(
                            selected = state.spec?.id == spec.id,
                            onClick = {
                                if (vm.needsProFor(spec)) onGoPro() else vm.selectSpec(spec)
                            },
                            label = { Text("${spec.flag} ${spec.name}", fontSize = 12.sp) },
                        )
                    }
                }

                Text(stringResource(R.string.id_print_sheet), fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IdPhotoMath.SHEETS.keys.toList()) { name ->
                        FilterChip(selected = state.sheetName == name,
                            onClick = { vm.setSheet(name) }, label = { Text(name, fontSize = 12.sp) })
                    }
                }

                when (val ui = state.ui) {
                    is IdPhotoViewModel.Ui.Idle -> EmptyState(
                        Icons.Filled.Face, stringResource(R.string.tool_idphoto),
                        stringResource(R.string.tool_idphoto_desc),
                        stringResource(R.string.pick_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is IdPhotoViewModel.Ui.Working -> Spacer(Modifier.height(120.dp))

                    is IdPhotoViewModel.Ui.NoFace -> EmptyState(
                        Icons.Filled.Face, stringResource(R.string.id_no_face),
                        stringResource(R.string.id_no_face_body),
                        stringResource(R.string.pick_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is IdPhotoViewModel.Ui.MultipleFaces -> EmptyState(
                        Icons.Filled.Groups, stringResource(R.string.id_multiple_faces),
                        stringResource(R.string.id_no_face_body),
                        stringResource(R.string.pick_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is IdPhotoViewModel.Ui.Failed -> EmptyState(
                        Icons.Filled.Image, stringResource(R.string.err_generic), ui.message,
                        stringResource(R.string.retry),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is IdPhotoViewModel.Ui.Ready -> {
                        BmpCard(ui.cropPreview, stringResource(R.string.after), 240)
                        Text("${ui.outW} × ${ui.outH} px  ·  ~${ui.estKb} KB  ·  ${ui.copies} per sheet",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ui.checks.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (c.ok) "✅" else "⚠️", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(labelFor(c.key), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(c.detail, fontSize = 11.5.sp,
                                         color = if (c.ok) MaterialTheme.colorScheme.onSurfaceVariant
                                                  else MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        BmpCard(ui.printSheet, stringResource(R.string.id_print_sheet), 300)

                        // Both buttons share ONE export path. The ad decision lives in
                        // ExportFlow.run — a Screen-level afterExportCompleted here would
                        // fire a second decision for the same export (the double-ad trap,
                        // README §6 P1). Two *branches* is not a double fire; two call
                        // sites for one export is.
                        PrimaryAction(stringResource(R.string.export) + " · single photo") {
                            vm.export(ctx, activity, picked.value, asSheet = false) { out ->
                                if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                            }
                        }
                        SecondaryAction(stringResource(R.string.export) + " · print sheet") {
                            vm.export(ctx, activity, picked.value, asSheet = true) { out ->
                                if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                            }
                        }
                        TextButton(onClick = { folder.launch(null) },
                                   modifier = Modifier.fillMaxWidth()) {
                            Text(if (picked.value == null) stringResource(R.string.batch_choose_folder)
                                 else stringResource(R.string.batch_save_to))
                        }
                    }
                }
            }
            BusyOverlay(stringResource(R.string.processing),
                state.ui is IdPhotoViewModel.Ui.Working || state.exporting)
        }
    }
}

private fun labelFor(key: String) = when (key) {
    "head" -> "Head height"
    "eye"  -> "Eye line"
    "bg"   -> "Background"
    else   -> key
}

@Composable
private fun BmpCard(bmp: Bitmap, label: String, heightDp: Int) {
    Box(Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Text(label, Modifier.align(Alignment.TopStart).padding(8.dp)
            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp)
    }
}
