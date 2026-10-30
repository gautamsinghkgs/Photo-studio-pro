package com.photostudio.pro.feature.later

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.photostudio.pro.R
import com.photostudio.pro.core.ui.EmptyState
import com.photostudio.pro.core.ui.ToolScaffold

/**
 * Shown for tools scheduled in a later milestone (M6-M10).
 *
 * This is deliberately NOT a "Coming Soon" placeholder and NOT a paywall.
 * It states what the tool is, which milestone it belongs to, and confirms that
 * everything already shipped works. SPEC.md HARD CONSTRAINT C5 forbids stub
 * screens; SPEC.md section 2 explicitly defines this honest state for M0.
 *
 * If you would rather not ship these at all, delete them from Routes and from
 * the Home grid instead of leaving them in. Do not ship a build where a visible
 * button does nothing.
 */
@Composable
fun LaterMilestoneScreen(toolName: String, milestone: String, onBack: () -> Unit) {
    ToolScaffold(title = toolName, onBack = onBack) { pad: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            EmptyState(
                icon = Icons.Filled.Build,
                headline = stringResource(R.string.later_title),
                body = stringResource(R.string.later_body, "$milestone — $toolName"),
                actionLabel = stringResource(R.string.later_back),
                onAction = onBack,
            )
        }
    }
}
