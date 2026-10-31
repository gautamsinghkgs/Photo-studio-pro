package com.photostudio.pro.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photostudio.pro.BuildConfig
import com.photostudio.pro.R
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.ui.ToolScaffold
import com.photostudio.pro.core.util.ServiceLocator

/**
 * Settings + legal.
 *
 * The privacy policy MUST be reachable from inside the app, not only from the
 * store listing. Google Play requires an in-app path, and reviewers test it.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onGoPro: () -> Unit = {}) {
    val ctx = LocalContext.current
    val entitlements by ServiceLocator.entitlements.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_data)) },
            text = { Text(stringResource(R.string.settings_delete_data_warn)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Wipes DataStore, cached entitlements, ad counters and the
                    // private export cache, then kills the process so the app
                    // restarts genuinely clean.
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE)
                        as? android.app.ActivityManager
                    am?.clearApplicationUserData()
                }) { Text(stringResource(R.string.settings_delete_confirm),
                           color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // TODO(you): your Cloudflare Pages URL (YOUR-TURN.md step 4). The same URL
    // goes in Play Console -> App content -> Privacy policy, and the same
    // domain root hosts app-ads.txt.
    val privacyUrl = "https://photostudiopro.pages.dev/"

    ToolScaffold(stringResource(R.string.settings), onBack, entitlements.isPro) { pad: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(6.dp)) {

            NoticeCard(stringResource(R.string.pillar_no_upload) + ". " +
                       "No account, no email, no photo ever leaves this device.")

            RowItem(stringResource(R.string.settings_privacy),
                    stringResource(R.string.settings_privacy_summary)) {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(privacyUrl)))
                }
            }

            RowItem(stringResource(R.string.settings_rate), "If this app saved you time, tell others") {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=${ctx.packageName}")))
                }
            }

            RowItem(stringResource(R.string.settings_share), "Send the app to a friend") {
                runCatching {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Photo Studio Pro")
                        putExtra(Intent.EXTRA_TEXT,
                            "Offline photo toolkit — background remover, ID photos, batch resize. " +
                            "Photos never leave your phone.\nhttps://play.google.com/store/apps/details?id=${ctx.packageName}")
                    }, null))
                }
            }

            if (!entitlements.isPro) {
                RowItem("Upgrade to Pro", "Unlimited exports, batch of 100, no watermark, no ads") {
                    onGoPro()
                }
            }

            /**
             * The Data Safety form answers YES to "do you provide a way for users
             * to request that their data is deleted?", and the privacy policy
             * promises an in-app path. This row is that path. It uses the system
             * API rather than clearing DataStore by hand, so nothing can be left
             * behind — including the ad counters and the cached entitlement.
             *
             * It shows a confirmation first, because it is destructive and
             * irreversible.
             */
            RowItem(stringResource(R.string.settings_delete_data),
                    stringResource(R.string.settings_delete_data_summary)) {
                confirmDelete = true
            }

            Spacer(Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.settings_about), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), fontSize = 12.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_about_processing),
                 fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_about_oss),
                 fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
