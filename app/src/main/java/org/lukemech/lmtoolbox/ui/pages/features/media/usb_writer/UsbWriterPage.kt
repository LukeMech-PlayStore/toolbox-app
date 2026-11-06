package org.lukemech.lmtoolbox.ui.pages.features.media.usb_writer

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import org.lukemech.lmtoolbox.ui.components.ActionButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallDesktop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbDrive
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbDrives
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbWriter
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbWriter.Mode
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbWriter.Phase
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.ConfirmDialog
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.tasks.ShownWhile
import org.lukemech.lmtoolbox.features.media.usb_writer.UsbWriterFeature

private enum class Step { Image, Drive, Write }

/**
 * The USB drive maker in three steps: the image (looked into: written as it is, or a Windows ISO made into an
 * installer), the drive (those plugged in, listed as they come and go; Android asks to allow it), then the writing,
 * asked to confirm (it erases the drive), its progress in the steps' bar; it goes on as the page is left.
 */
@Composable
fun UsbWriterPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val image = UsbWriter.image
    val working = UsbWriter.working
    var step by rememberSaveable {
        mutableStateOf(if (working || UsbWriter.phase == Phase.Done || UsbWriter.phase == Phase.Failed) Step.Write else Step.Image)
    }
    var inspecting by remember { mutableStateOf(false) }
    var drives by remember { mutableStateOf(UsbDrives.list(context)) }
    val seen = remember { mutableStateListOf<UsbDrive>() }
    var asking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val supported = remember { UsbDrives.supported(context) }
    ShownWhile(UsbWriter.run)

    // The drives as they're plugged in and out; the one chosen forgotten once it's gone
    LaunchedEffect(Unit) {
        UsbDrives.changes(context).collect {
            drives = UsbDrives.list(context)
            if (!working && drives.none { it.key == UsbWriter.driveKey }) UsbWriter.driveKey = null
        }
    }
    LaunchedEffect(drives) { drives.forEach { d -> if (seen.none { it.key == d.key }) seen += d } }
    val drive = drives.firstOrNull { it.key == UsbWriter.driveKey }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            inspecting = true
            try {
                UsbWriter.image = UsbWriter.inspect(context, uri)
                if (UsbWriter.phase == Phase.Done || UsbWriter.phase == Phase.Failed) UsbWriter.phase = Phase.Idle
                step = Step.Drive
            } finally {
                inspecting = false
            }
        }
    }

    BackHandler(enabled = step != Step.Image) {
        step = if (step == Step.Write) Step.Drive else Step.Image
    }

    val phase = UsbWriter.phase
    // The image, the drive, writing, and checking (a step of its own when it's on, else none)
    val stepCount = if (UsbWriter.verify) Step.entries.size + 1 else Step.entries.size
    val steps = Steps(
        stepCount,
        when (phase) {
            Phase.Verifying -> Step.entries.size
            // All done: every bar full
            Phase.Done -> stepCount - 1
            else -> step.ordinal
        },
        inspecting,
        working = working,
        progress = if (working && UsbWriter.total > 0) UsbWriter.done.toFloat() / UsbWriter.total else null,
        caption = when (phase) {
            Phase.Preparing -> stringResource(R.string.f_UsbWriter_preparing)
            Phase.Writing -> stringResource(R.string.f_UsbWriter_writing)
            Phase.Verifying -> stringResource(R.string.f_UsbWriter_verifying)
            else -> null
        }?.takeIf { working },
        detail = if (working && UsbWriter.total > 0) {
            "${Formatter.formatShortFileSize(context, UsbWriter.done)} / ${Formatter.formatShortFileSize(context, UsbWriter.total)}"
        } else null,
        onCancel = if (working) UsbWriter.run::stop else null,
    )
    val title = stringResource(R.string.fp_UsbWriter)
    // A raw image bigger than the drive (Windows' are checked as they're laid out: their files, not the ISO)
    val tooSmall = image?.mode == Mode.Raw && UsbWriter.driveInfo?.let { image.size > it.size } == true

    Column(modifier = Modifier.fillMaxSize()) {
        when (step) {
            Step.Write if image != null -> StepWithButtons(
                content = {
                    // Groups FIELD_SPACING apart (what's written where; its option; the end), rows ROW_SPACING within
                    PageHeading(
                        steps = steps, title = title, onBack = { if (!working) step = Step.Drive },
                        verticalArrangement = FieldArrangement
                    ) {
                        Column(verticalArrangement = FieldArrangement) {
                            ImageCard(image)
                            val info = UsbWriter.driveInfo
                            Subject(
                                Icons.Default.Usb, UsbWriter.driveName ?: drive?.name.orEmpty(),
                                info?.let { listOf(it.description, Formatter.formatShortFileSize(context, it.size)).filter(String::isNotEmpty).joinToString(" · ") }
                            )
                        }
                        Column(verticalArrangement = RowArrangement) {
                            Note(stringResource(R.string.f_UsbWriter_too_small), visible = tooSmall)
                            Reveal(visible = !working && phase != Phase.Done) {
                                SwitchButton(
                                    stringResource(R.string.f_UsbWriter_verify), Icons.Default.Verified, UsbWriter.verify,
                                    subtitle = stringResource(R.string.f_UsbWriter_verify_desc),
                                ) { UsbWriter.verify = it }
                            }
                            Note(stringResource(R.string.f_UsbWriter_erase_note), visible = !working && phase == Phase.Idle)
                            Note(stringResource(R.string.f_UsbWriter_keep_plugged), visible = working)
                        }
                        Reveal(visible = phase == Phase.Done) { DoneCard() }
                    }
                },
                buttons = {
                    // What it takes, as the media downloader shows its: how fast the drive goes (written, or read back
                    // when checking: counted by the writer), the phone's storage and CPU
                    Reveal(visible = working) {
                        ResourceTiles(UsbWriterFeature.resourceTiles, usbBytes = { UsbWriter.done })
                    }
                    Reveal(visible = phase == Phase.Done) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(Icons.Default.Usb, stringResource(R.string.f_UsbWriter_another_drive)) {
                                UsbWriter.phase = Phase.Idle
                                step = Step.Drive
                            }
                            ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_UsbWriter_another)) {
                                UsbWriter.phase = Phase.Idle
                                step = Step.Image
                            }
                        }
                    }
                    Reveal(visible = phase != Phase.Done) {
                        BigButton(
                            Icons.Default.InstallDesktop,
                            stringResource(if (working) R.string.f_UsbWriter_writing else R.string.f_UsbWriter_write),
                            enabled = !working && drive != null && !tooSmall
                        ) { confirming = true }
                    }
                }
            )

            Step.Drive if image != null -> StepWithButtons(
                content = {
                    // The image, then the drives' group (its title, its rows) FIELD_SPACING under it
                    PageHeading(
                        steps = steps, title = title, onBack = { step = Step.Image },
                        verticalArrangement = FieldArrangement
                    ) {
                        Column(verticalArrangement = RowArrangement) {
                            ImageCard(image)
                            Note(stringResource(R.string.f_UsbWriter_not_bootable), visible = image.mode == Mode.Raw && !image.bootable)
                            Note(
                                stringResource(R.string.f_UsbWriter_no_uefi),
                                visible = image.mode == Mode.Windows && image.architectures.isEmpty()
                            )
                        }
                        Column(verticalArrangement = RowArrangement) {
                            SectionTitle(stringResource(R.string.f_UsbWriter_drives))
                            Note(stringResource(R.string.f_UsbWriter_no_host), visible = !supported)
                            Note(stringResource(R.string.f_UsbWriter_no_drives), visible = supported && drives.isEmpty())
                            // Every drive seen, folding in and out as it's plugged in and out (the arrangement keeps
                            // the rows apart, none left where one folded away)
                            seen.forEach { d ->
                                key(d.key) {
                                    Reveal(visible = drives.any { it.key == d.key }) {
                                        DriveRow(d, selected = d.key == UsbWriter.driveKey) { UsbWriter.driveKey = d.key }
                                    }
                                }
                            }
                        }
                    }
                },
                buttons = {
                    SecondaryButton(stringResource(R.string.f_UsbWriter_refresh)) { drives = UsbDrives.list(context) }
                    BigButton(Icons.Default.Usb, stringResource(R.string.f_UsbWriter_next), enabled = drive != null && !asking && !UsbWriter.probing) {
                        val chosen = drive ?: return@BigButton
                        scope.launch {
                            asking = true
                            try {
                                if (!UsbDrives.requestPermission(context, chosen)) {
                                    AppTasks.reportFailure(UsbWriterFeature, chosen.name, context.getString(R.string.f_UsbWriter_denied))
                                    return@launch
                                }
                                // What it is and how big: a card reader without a card fails here (in the bar)
                                UsbWriter.driveInfo = UsbWriter.probe(context, chosen) ?: return@launch
                                UsbWriter.driveName = chosen.name
                                step = Step.Write
                            } finally {
                                asking = false
                            }
                        }
                    }
                }
            )

            else -> StepWithButtons(
                content = {
                    PageHeading(steps = steps, title = title, verticalArrangement = Arrangement.Center) {
                        ToolIntro(
                            Icons.Default.InstallDesktop, stringResource(R.string.f_UsbWriter_intro_title),
                            listOf(
                                Icons.Default.Album to stringResource(R.string.f_UsbWriter_intro_1),
                                Icons.Default.Window to stringResource(R.string.f_UsbWriter_intro_2),
                                Icons.Default.Usb to stringResource(R.string.f_UsbWriter_intro_3),
                            ),
                            stringResource(R.string.f_UsbWriter_intro_tip)
                        )
                    }
                },
                buttons = {
                    // The image picked stays a tap away (kept while it folds away)
                    val last = remember { arrayOfNulls<UsbWriter.Image>(1) }
                    if (image != null) last[0] = image
                    Reveal(visible = image != null) {
                        val open = last[0] ?: return@Reveal
                        SecondaryButton(stringResource(R.string.f_UsbWriter_back_to, open.name)) {
                            step = if (working) Step.Write else Step.Drive
                        }
                    }
                    BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_UsbWriter_pick), enabled = !inspecting && !working) {
                        picker.launch(arrayOf("*/*"))
                    }
                }
            )
        }
    }

    if (confirming && image != null && drive != null) ConfirmDialog(
        Icons.Default.DeleteForever,
        stringResource(R.string.f_UsbWriter_confirm_title),
        stringResource(R.string.f_UsbWriter_confirm_text, drive.name, image.name),
        stringResource(R.string.f_UsbWriter_confirm),
        onDismiss = { confirming = false },
        onConfirm = {
            confirming = false
            UsbWriter.start(context, image, drive, UsbWriter.verify)
        }
    )
}

/** Written (and checked): as the other tools end, the drive ready to be unplugged. */
@Composable
private fun DoneCard() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.f_UsbWriter_done), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(if (UsbWriter.verify) R.string.f_UsbWriter_done_verified else R.string.f_UsbWriter_done_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** The image: its name, size, and how it's written. */
@Composable
private fun ImageCard(image: UsbWriter.Image) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, image.size)
    val how = when (image.mode) {
        Mode.Windows -> stringResource(
            R.string.f_UsbWriter_mode_windows,
            image.architectures.joinToString("/").ifEmpty { "?" }
        ) + if (image.splitsWim) " " + stringResource(R.string.f_UsbWriter_mode_split) else ""
        Mode.Raw -> stringResource(R.string.f_UsbWriter_mode_raw)
    }
    Subject(if (image.mode == Mode.Windows) Icons.Default.Window else Icons.Default.Album, image.name, "$size · $how")
}

@Composable
private fun Subject(icon: ImageVector, title: String, details: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            if (details != null) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DriveRow(drive: UsbDrive, selected: Boolean, onClick: () -> Unit) {
    FeatureButton(
        title = drive.name,
        icon = Icons.Default.Usb,
        buttonContent = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}
