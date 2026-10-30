package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.DownloadStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadFolderSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { DownloadStorage.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val folder = remember(revision) { store.folderUri }
    val contract = remember { object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: android.content.Context, input: Uri?): Intent =
            super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    } }
    val picker = rememberLauncherForActivityResult(contract) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    val valid = context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                        it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                            it.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
                    } == true
                    require(valid)
                    store.setFolder(uri)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, R.string.sourcify_download_folder_error, Toast.LENGTH_LONG).show() }
        }
    }
    Material3SettingsGroup(title = stringResource(R.string.sourcify_download_folder), items = buildList {
        add(Material3SettingsItem(icon = painterResource(R.drawable.download), title = { Text(stringResource(R.string.sourcify_download_folder_choose)) },
            description = { Column {
                Text(folder?.lastPathSegment?.substringAfter(':') ?: stringResource(R.string.sourcify_download_folder_auto))
                Text(stringResource(R.string.sourcify_download_folder_description))
            } }, onClick = { picker.launch(folder) }))
        if (folder != null) add(Material3SettingsItem(title = { Text(stringResource(R.string.sourcify_download_folder_auto)) },
            icon = painterResource(R.drawable.close), onClick = { store.setFolder(null) }))
    })
}
