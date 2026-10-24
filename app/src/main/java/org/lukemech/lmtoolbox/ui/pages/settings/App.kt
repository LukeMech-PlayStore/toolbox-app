package org.lukemech.lmtoolbox.ui.pages.settings

import org.lukemech.lmtoolbox.ui.components.ConfirmedActionButton
import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppReset
import org.lukemech.lmtoolbox.ui.components.FeatureButton

// Settings of the "App" section: the app's version, the discussion group and resetting

@Composable
fun PkgInfoSection() {
    val activity = LocalActivity.current as ComponentActivity
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val pkgInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
    val versionName = pkgInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

    val packageInfo = "${activity.packageName}\n${versionName ?: ""} ($versionCode)"
    val playStoreAppUrl = "https://play.google.com/store/apps/details?id=" + activity.packageName

    FeatureButton(
        title = activity.packageName,
        subtitle = "v$versionName ($versionCode)",
        titleSubtitleSwap = true,
        icon = Icons.Default.Inventory2,
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, playStoreAppUrl.toUri())
            activity.startActivity(intent)
        },
        onLongClick = {
            coroutineScope.launch {
                val clip = ClipData.newPlainText("pkgInfo", packageInfo).toClipEntry()
                clipboard.setClipEntry(clip)
            }
        }
    )
}

@Composable
fun DiscussionGroupSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val url = "https://groups.google.com/g/lmtoolbox"

    FeatureButton(
        title = stringResource(R.string.s_discussion_group),
        icon = Icons.Default.Group,
        icon2 = Icons.AutoMirrored.Default.OpenInNew,
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        },
        onLongClick = {
            coroutineScope.launch {
                val clip = ClipData.newPlainText("discussionGroup", url).toClipEntry()
                clipboard.setClipEntry(clip)
            }
        }
    )

}

/** Resets everything the app keeps (the Linux subsystem too; permissions stay); the app starts anew. */
@Composable
fun AppResetButton() {
    val context = LocalContext.current
    ConfirmedActionButton(
        title = stringResource(R.string.s_app_reset),
        subtitle = null,
        icon = Icons.Default.RestartAlt,
        confirmTitle = stringResource(R.string.s_app_reset_confirm_title),
        confirmText = stringResource(R.string.s_app_reset_confirm_msg),
        confirmLabel = stringResource(R.string.s_app_reset_confirm)
    ) { AppReset.everything(context) }
}
