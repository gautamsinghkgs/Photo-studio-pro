package com.photostudio.pro.feature.ocr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.photostudio.pro.core.util.ServiceLocator

@Composable
fun OcrScreen(adsReady: Boolean, onBack: () -> Unit, onGoPro: () -> Unit,
              vm: OcrViewModel = viewModel()) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val ent by ServiceLocator.entitlements.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) vm.onPhotoPicked(ctx, it)
    }

    ToolScaffold(stringResource(R.string.tool_ocr), onBack, ent.isPro) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Card { Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = st.autoScript, onCheckedChange = vm::setAuto)
                        Text("Auto-detect script (runs Latin + Devanagari, keeps the better result)",
                             Modifier.padding(start = 10.dp), fontSize = 12.sp)
                    }
                    if (!st.autoScript) {
                        Text(stringResource(R.string.ocr_script), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OcrEngine.Script.entries.forEach { s ->
                                FilterChip(selected = st.script == s, onClick = { vm.setScript(s) },
                                    label = { Text(if (s == OcrEngine.Script.LATIN) "Latin / English"
                                                    else "Devanagari / Hindi", fontSize = 11.5.sp) })
                            }
                        }
                    }
                    if (st.remainingFree >= 0) {
                        Text(stringResource(R.string.ocr_free_limit).let {
                            "Free scans left today: ${st.remainingFree}"
                        }, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }

                when (st.ui) {
                    is OcrViewModel.Ui.Idle -> EmptyState(
                        Icons.Filled.TextSnippet, stringResource(R.string.tool_ocr),
                        stringResource(R.string.tool_ocr_desc), stringResource(R.string.ocr_scan),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is OcrViewModel.Ui.Nothing -> EmptyState(
                        Icons.Filled.TextSnippet, stringResource(R.string.ocr_nothing),
                        stringResource(R.string.ocr_nothing_body), stringResource(R.string.retry),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                    is OcrViewModel.Ui.LimitReached -> {
                        NoticeCard(stringResource(R.string.ocr_free_limit))
                        PrimaryAction(stringResource(R.string.paywall_title)) { onGoPro() }
                    }

                    is OcrViewModel.Ui.Failed -> NoticeCard(stringResource(R.string.err_generic))

                    is OcrViewModel.Ui.Found -> {
                        val f = st.ui as OcrViewModel.Ui.Found
                        Text(stringResource(R.string.ocr_blocks, f.blocks), fontSize = 12.sp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Selectable text — the whole point of an OCR tool. A
                        // non-selectable result forces a screenshot, which is the
                        // #1 complaint in competitor reviews.
                        Card { SelectionContainer(Modifier.padding(14.dp)) {
                            Text(f.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp)
                        } }
                        SecondaryAction(stringResource(R.string.ocr_copy)) {
                            clipboard.setText(AnnotatedString(f.text))
                        }
                        Text(f.script.name, fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    else -> Unit
                }

                PrimaryAction(stringResource(R.string.ocr_scan)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            BusyOverlay(stringResource(R.string.processing), st.busy)
        }
    }
}
