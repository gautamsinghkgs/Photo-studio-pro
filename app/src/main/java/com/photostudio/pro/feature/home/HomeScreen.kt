package com.photostudio.pro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photostudio.pro.R
import com.photostudio.pro.Routes
import com.photostudio.pro.core.util.ServiceLocator

private data class Tool(
    val id: String, val route: String, val emoji: String,
    val titleRes: Int, val descRes: Int, val built: Boolean,
)

/**
 * The tool grid. Eight tools, one app — that is the whole ASO strategy: a
 * single listing ranking for "background remover", "passport photo", "photo
 * compressor", "collage" and "image to pdf" at once.
 *
 * Unbuilt tools are shown with an honest label rather than hidden, and are
 * never paywalled. SPEC.md section 2 / HARD CONSTRAINT C5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(adsReady: Boolean, onNavigate: (String) -> Unit) {

    val entitlements by ServiceLocator.entitlements.state.collectAsState()

    val tools = listOf(
        Tool("bgremove", Routes.BGREMOVE, "✂️", R.string.tool_bgremover, R.string.tool_bgremover_desc, true),
        Tool("idphoto",  Routes.IDPHOTO,  "🛂", R.string.tool_idphoto,   R.string.tool_idphoto_desc,   true),
        Tool("batch",    Routes.BATCH,    "🗂️", R.string.tool_batch,     R.string.tool_batch_desc,     true),
        Tool("whitebg",  Routes.WHITEBG,  "⬜", R.string.tool_whitebg,   R.string.tool_whitebg_desc,   true),
        Tool("collage",  Routes.COLLAGE,  "🧩", R.string.tool_collage,   R.string.tool_collage_desc,   true),
        Tool("pdf",      Routes.PDF,      "📄", R.string.tool_pdf,       R.string.tool_pdf_desc,       true),
        Tool("ocr",      Routes.OCR,      "🔤", R.string.tool_ocr,       R.string.tool_ocr_desc,       true),
        Tool("enhance",  Routes.ENHANCE,  "✨", R.string.tool_enhance,   R.string.tool_enhance_desc,   true),
    ).filter { ServiceLocator.flags.isToolEnabled(it.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    if (entitlements.isPro) {
                        Surface(Modifier.padding(end = 4.dp), shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary) {
                            Text(" ${stringResource(R.string.pro_badge)} ", color = MaterialTheme.colorScheme.onSecondary,
                                 fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                 modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {

            // The three honest pillars. Not marketing fluff — each is verifiable
            // and each is something an API-dependent competitor cannot claim.
            Pillar("🔒", stringResource(R.string.pillar_no_upload))
            Pillar("✈️", stringResource(R.string.pillar_offline))
            Pillar("♾️", stringResource(R.string.pillar_unlimited))

            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tools) { tool ->
                    ToolCard(tool = tool, onClick = {
                        com.photostudio.pro.analytics.Events.logToolOpened(tool.id)
                        onNavigate(tool.route)
                    })
                }
            }
        }
    }
}

@Composable
private fun Pillar(emoji: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolCard(tool: Tool, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(tool.emoji, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(tool.titleRes), style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(tool.descRes), style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, fontSize = 11.5.sp)
            if (!tool.built) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.home_building), fontSize = 10.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}
