package org.lukemech.lmtoolbox.ui.pages.settings

import org.lukemech.lmtoolbox.ui.components.ConfirmedActionButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppReset
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.helpers.copyText
import org.lukemech.lmtoolbox.helpers.openLink

// Settings of the "App" section: the app's version, the discussion group and resetting

@Composable
fun PkgInfoSection() {
    val activity = LocalActivity.current as ComponentActivity
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
        onClick = { openLink(activity, playStoreAppUrl) },
        onLongClick = { copyText(activity, packageInfo, "pkgInfo") }
    )
}

@Composable
fun DiscussionGroupSection() {
    val context = LocalContext.current

    val url = "https://groups.google.com/g/lmtoolbox"

    FeatureButton(
        title = stringResource(R.string.s_discussion_group),
        icon = Icons.Default.Group,
        icon2 = Icons.AutoMirrored.Default.OpenInNew,
        onClick = { openLink(context, url) },
        onLongClick = { copyText(context, url, "discussionGroup") }
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
