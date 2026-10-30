package com.photostudio.pro.feature.collage

import android.app.Activity
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.photostudio.pro.core.image.CollageOps
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
fun CollageScreen(adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
                  vm: CollageViewModel = viewModel()) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val ent by ServiceLocator.entitlements.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = ctx as? Activity

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { if (it.isNotEmpty()) vm.onPhotosPicked(ctx, it) }

    val picked = remember { mutableStateOf<Uri?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            picked.value = tree
        }
    }

    ToolScaffold(stringResource(R.string.tool_collage), onBack, ent.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (st.photos.isEmpty()) {
                EmptyState(Icons.Filled.GridView, stringResource(R.string.tool_collage),
                    stringResource(R.string.tool_collage_desc), stringResource(R.string.pick_photos),
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    (st.ui as? CollageViewModel.Ui.Ready)?.let { ui ->
                        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                            Image(ui.preview.asImageBitmap(), null, Modifier.fillMaxSize(),
                                  contentScale = ContentScale.Fit)
                        }
                    }

                    Text("${st.photos.size} photo(s) · layout needs ${st.template.count}",
                         fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!st.hasEnough) NoticeCard(stringResource(R.string.collage_need_more, st.template.count))

                    // ---- templates, with the rewarded-unlock gate ----
                    Text(stringResource(R.string.collage_layout), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    LazyVerticalGrid(columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollageOps.templates) { t ->
                            val available = st.isTemplateAvailable(t, ent.isPro)
                            Card(onClick = {
                                if (available) vm.update { copy(templateId = t.id) }
                                else if (adsReady && activity != null) {
                                    // ⭐ The rewarded-unlock surface. RewardGuard caps
                                    // the grants per day and TEMPLATE_24H is one of
                                    // exactly five small, temporary kinds.
                                    scope.launch {
                                        ServiceLocator.adCoordinator.requestRewarded(
                                            activity = activity,
                                            kind = RewardGuard.RewardKind.TEMPLATE_24H,
                                            onGranted = { k ->
                                                vm.unlockAllTemplates(k.durationMs)
                                                vm.update { copy(templateId = t.id) }
                                            },
                                            onDenied = { onGoPro() },
                                            onError = { onGoPro() },
                                        )
                                    }
                                } else onGoPro()
                            }, shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.fillMaxWidth().padding(8.dp),
                                       horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row {
                                        Text(t.name, fontSize = 10.5.sp, maxLines = 2)
                                        if (!available) Text(" 🔒", fontSize = 10.sp)
                                    }
                                    Text("${t.count}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                         color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Text(stringResource(R.string.collage_ratio), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CollageOps.ratios.keys.take(3).forEach { r ->
                            FilterChip(selected = st.ratioName == r,
                                onClick = { vm.update { copy(ratioName = r) } },
                                label = { Text(r, fontSize = 10.5.sp) })
                        }
                    }

                    Card { Column(Modifier.padding(14.dp)) {
                        CollSlider(stringResource(R.string.collage_spacing), st.spacing.toFloat(), 0f, 60f,
                            { vm.update { copy(spacing = it.toInt()) } }) { "${st.spacing}px" }
                        CollSlider(stringResource(R.string.collage_border), st.border.toFloat(), 0f, 120f,
                            { vm.update { copy(border = it.toInt()) } }) { "${st.border}px" }
                        CollSlider(stringResource(R.string.collage_corners), st.corner.toFloat(), 0f, 120f,
                            { vm.update { copy(corner = it.toInt()) } }) { "${st.corner}px" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(Color.WHITE, Color.BLACK, Color(0xFFEFEFEF)).forEach { c ->
                                Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
                                    .background(c)
                                    .height(28.dp).fillMaxWidth(0.12f)
                                    .then(if (st.bgColor == c.value)
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(0.25f))
                                        else Modifier))
                            }
                            TextButton(onClick = { vm.update {
                                copy(bgColor = if (bgColor == Color.WHITE.value) Color.BLACK.value else Color.WHITE.value) } }) {
                                Text("Toggle bg", fontSize = 12.sp)
                            }
                        }
                    } }

                    PrimaryAction(stringResource(R.string.export),
                                  enabled = st.ui is CollageViewModel.Ui.Ready && !st.busy) {
                        vm.export(ctx, activity, picked.value, ent.isPro) { out ->
                            if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { folder.launch(null) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.batch_choose_folder), fontSize = 12.sp)
                        }
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pick_photos), fontSize = 12.sp)
                        }
                    }
                    if (!ent.isPro && !st.templatesUnlocked) NoticeCard(stringResource(R.string.collage_free_limit))
                }
            }
            BusyOverlay(stringResource(R.string.processing),
                st.ui is CollageViewModel.Ui.Working || st.busy)
        }
    }
}

@Composable
private fun CollSlider(label: String, value: Float, min: Float, max: Float,
                       onChange: (Float) -> Unit, valueLabel: () -> String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp)
            Text(valueLabel(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}
