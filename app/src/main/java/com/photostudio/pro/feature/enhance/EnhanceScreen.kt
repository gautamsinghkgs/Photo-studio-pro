package com.photostudio.pro.feature.enhance

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * M10 — Photo Enhance.
 *
 * The honest-limitations notice is rendered on screen, not buried in the listing.
 * Users who expect generative restoration from a classical pipeline are the ones
 * who leave the 1-star reviews that push a Photo & Video app below the 4.0 ASO
 * floor, so the expectation is set inside the tool itself.
 */
@Composable
fun EnhanceScreen(adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
                  vm: EnhanceViewModel = viewModel()) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val ent by ServiceLocator.entitlements.state.collectAsState()
    val activity = ctx as? Activity

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) vm.onPhotoPicked(ctx, it)
    }
    val picked = remember { mutableStateOf<Uri?>(null) }
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

    ToolScaffold(stringResource(R.string.tool_enhance), onBack, ent.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {

                when (st.ui) {
                    is EnhanceViewModel.Ui.Idle -> EmptyState(
                        Icons.Filled.AddPhotoAlternate, stringResource(R.string.tool_enhance),
                        stringResource(R.string.enh_honest), stringResource(R.string.pick_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    else -> Unit
                }

                (st.ui as? EnhanceViewModel.Ui.Ready)?.let { ui ->
                    // Hold to compare against the original.
                    Box(
                        Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        vm.setCompare(true)
                                        tryAwaitRelease()
                                        vm.setCompare(false)
                                    })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val show = if (st.showOriginal) vm.original() else ui.preview
                        if (show != null) {
                            Image(show.asImageBitmap(), null, Modifier.fillMaxSize(),
                                  contentScale = ContentScale.Fit)
                        }
                        if (st.showOriginal) {
                            Text("ORIGINAL", Modifier.align(Alignment.TopStart).padding(10.dp)
                                .clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(0.55f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp)
                        }
                    }
                    Text(stringResource(R.string.enh_compare), fontSize = 11.sp,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)

                    SecondaryAction(stringResource(R.string.enh_auto)) { vm.auto() }

                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        EnhSlider(stringResource(R.string.enh_brightness),
                            st.adj.brightness.toFloat(), -100f, 100f,
                            { vm.update { copy(brightness = it.toInt()) } }) { "${st.adj.brightness}" }
                        EnhSlider(stringResource(R.string.enh_contrast),
                            st.adj.contrast, -1f, 1f,
                            { vm.update { copy(contrast = it) } }) { "%.2f".format(st.adj.contrast) }
                        EnhSlider(stringResource(R.string.enh_saturation),
                            st.adj.saturation, -1f, 1f,
                            { vm.update { copy(saturation = it) } }) { "%.2f".format(st.adj.saturation) }
                        EnhSlider(stringResource(R.string.enh_sharpness),
                            st.adj.sharpness, 0f, 1.5f,
                            { vm.update { copy(sharpness = it) } }) { "%.2f".format(st.adj.sharpness) }
                        EnhSlider(stringResource(R.string.enh_denoise),
                            st.adj.denoise.toFloat(), 0f, 60f,
                            { vm.update { copy(denoise = it.toInt()) } }) { "${st.adj.denoise}" }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.reset() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.enh_reset), fontSize = 13.sp)
                        }
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pick_photo), fontSize = 13.sp)
                        }
                    }

                    PrimaryAction(stringResource(R.string.export), enabled = !st.busy) {
                        vm.export(ctx, activity, picked.value) { out ->
                            if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
                        }
                    }
                    TextButton(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (picked.value == null) stringResource(R.string.batch_choose_folder)
                             else stringResource(R.string.batch_save_to), fontSize = 13.sp)
                    }
                }

                // Always visible once a photo is open — this is the expectation
                // setting that prevents the "this isn't AI restore" 1-star review.
                if (st.ui is EnhanceViewModel.Ui.Ready) NoticeCard(stringResource(R.string.enh_honest))
            }
            BusyOverlay(stringResource(R.string.processing),
                st.ui is EnhanceViewModel.Ui.Working || st.busy)
        }
    }
}

@Composable
private fun EnhSlider(label: String, value: Float, min: Float, max: Float,
                      onChange: (Float) -> Unit, valueLabel: () -> String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp)
            Text(valueLabel(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}
