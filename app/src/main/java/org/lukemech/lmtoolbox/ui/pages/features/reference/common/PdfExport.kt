package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.ReferencePdf
import org.lukemech.lmtoolbox.features.reference.common.Sheet
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore

/**
 * A page's action making a PDF of [sheet] (a [ReferencePdf]: [title], the tool's, over it), saved to Downloads as
 * "<title> - <sheet>.pdf" and opened; a spinner in its place while it's made.
 */
@Composable
fun PdfExportAction(title: String, sheet: Sheet) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var making by remember { mutableStateOf(false) }
    IconButton(enabled = !making, onClick = {
        making = true
        scope.launch {
            val resources = context.resources
            val name = safeFileName("$title - ${sheet.name.resolve(resources)}", "LM Toolbox") + ".pdf"
            runCatching {
                withContext(Dispatchers.IO) {
                    saveToMediaStore(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS, name, PDF) {
                        ReferencePdf(resources, title).write(sheet, it)
                    }
                }
            }.onSuccess { uri ->
                Toast.makeText(context, resources.getString(R.string.rf_pdf_saved, name), Toast.LENGTH_SHORT).show()
                openFile(context, uri, PDF)
            }.onFailure {
                Toast.makeText(context, R.string.rf_pdf_failed, Toast.LENGTH_SHORT).show()
            }
            making = false
        }
    }) {
        if (making) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.rf_export_pdf))
    }
}

private const val PDF = "application/pdf"
