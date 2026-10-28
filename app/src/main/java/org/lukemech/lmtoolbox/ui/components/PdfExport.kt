package org.lukemech.lmtoolbox.ui.components

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore

/** A PDF to make: its file's [name] (".pdf" added), and [write] drawing it (away from the main thread). */
class PdfFile(val name: String, val write: (OutputStream) -> Unit)

/**
 * A page's action making a PDF of what it shows: [pdf] taken as it's tapped (what's in sight then, however it
 * goes on changing), saved to Downloads and opened; a spinner in its place while it's made.
 */
@Composable
fun PdfExportAction(enabled: Boolean = true, pdf: () -> PdfFile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPdf by rememberUpdatedState(pdf)
    var making by remember { mutableStateOf(false) }
    IconButton(enabled = enabled && !making, onClick = {
        making = true
        val file = currentPdf()
        val name = safeFileName(file.name, "LM Toolbox") + ".pdf"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    saveToMediaStore(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS, name, PDF, file.write)
                }
            }.onSuccess { uri ->
                Toast.makeText(context, context.getString(R.string.pdf_saved, name), Toast.LENGTH_SHORT).show()
                openFile(context, uri, PDF)
            }.onFailure {
                Toast.makeText(context, R.string.pdf_failed, Toast.LENGTH_SHORT).show()
            }
            making = false
        }
    }) {
        if (making) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.pdf_export))
    }
}

private const val PDF = "application/pdf"
