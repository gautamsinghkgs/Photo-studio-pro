package com.photostudio.pro.feature.whitebg

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ExportFlow
import com.photostudio.pro.core.util.ServiceLocator

@Composable
fun WhiteBgScreen(adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
                  vm: WhiteBgViewModel = viewModel()) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val ent by ServiceLocator.entitlements.state.collectAsState()
    val activity = ctx as? Activity

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) vm.onPhotoPicked(ctx, it)
    }
    // Declared before the launcher that writes to it.
    val picked = remember { mutableStateOf<android.net.Uri?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            picked.value = tree
        }
    }

    ToolScaffold(stringResource(R.string.tool_whitebg), onBack, ent.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {

                when (val ui = st.ui) {
                    is WhiteBgViewModel.Ui.Idle -> EmptyState(
                        Icons.Filled.AddPhotoAlternate, stringResource(R.string.tool_whitebg),
                        stringResource(R.string.wb_body), stringResource(R.string.pick_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is WhiteBgViewModel.Ui.Working -> Box(Modifier.fillMaxWidth().height(240.dp)) {}

                    is WhiteBgViewModel.Ui.Failed -> EmptyState(
                        Icons.Filled.ImageNotSupported, stringResource(R.string.err_generic), ui.message,
                        stringResource(R.string.retry),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is WhiteBgViewModel.Ui.Ready -> {
                        // Rendered below, outside the `when`, so the controls stay
                        // visible while the preview is on screen.
                    }
                }

                (st.ui as? WhiteBgViewModel.Ui.Ready)?.let { ui ->
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Image(ui.result.asImageBitmap(), null, Modifier.fillMaxSize(),
                              contentScale = ContentScale.Fit)
                    }
                    Text("Background whitened: ${(ui.coverage * 100).toInt()}% of the frame",
                         fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Honest failure mode: tell them to use the right tool
                    // instead of letting them assume the app is broken.
                    if (ui.wrongTool) NoticeCard(stringResource(R.string.wb_tip))
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.wb_strength), fontSize = 13.sp,
                             fontWeight = FontWeight.Medium)
                        Text("${(st.strength * 100).toInt()}%", fontSize = 13.sp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(value = st.strength, onValueChange = { vm.update { copy(strength = it) } })

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = st.evenLighting,
                               onCheckedChange = { vm.update { copy(evenLighting = it) } })
                        Text(stringResource(R.string.wb_even_lighting), Modifier.padding(start = 10.dp), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = st.soften, onCheckedChange = { vm.update { copy(soften = it) } })
                        Text("Soften edge (removes the white halo)", Modifier.padding(start = 10.dp), fontSize = 13.sp)
                    }
                }

                PrimaryAction(stringResource(R.string.export),
                              enabled = st.ui is WhiteBgViewModel.Ui.Ready && !st.busy) {
                    vm.export(ctx, activity, picked.value) { out ->
                        if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                    }
                }
                TextButton(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (picked.value == null) stringResource(R.string.batch_choose_folder)
                         else stringResource(R.string.batch_save_to), fontSize = 13.sp)
                }
                TextButton(onClick = { picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pick_photo), fontSize = 13.sp)
                }
            }
            BusyOverlay(stringResource(R.string.processing),
                st.ui is WhiteBgViewModel.Ui.Working || st.busy)
        }
    }
}
