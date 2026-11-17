package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.music.R
import com.metrolist.music.ui.menu.ExternalPlaylistImportDialog

internal const val LIBRARY_TOOLBAR_ACTION = "libraryToolbarAction"
internal const val LIBRARY_SEARCH_ACTION = "search"
internal const val LIBRARY_CREATE_ACTION = "create"

@Composable
fun LibraryTopActions(navController: NavController) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    IconButton(onClick = {
        navController.currentBackStackEntry?.savedStateHandle?.set(LIBRARY_TOOLBAR_ACTION, LIBRARY_SEARCH_ACTION)
    }) {
        Icon(painterResource(R.drawable.search), stringResource(R.string.search_library))
    }
    Box {
        IconButton(onClick = { showAddMenu = true }) {
            Icon(painterResource(R.drawable.add), stringResource(R.string.create_playlist))
        }
        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.create_playlist)) }, onClick = {
                showAddMenu = false
                navController.currentBackStackEntry?.savedStateHandle?.set(LIBRARY_TOOLBAR_ACTION, LIBRARY_CREATE_ACTION)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.import_playlist)) }, onClick = {
                showAddMenu = false
                showImport = true
            })
        }
    }
    if (showImport) {
        ExternalPlaylistImportDialog(onDismiss = { showImport = false }, onImported = { playlistId ->
            showImport = false
            navController.navigate("local_playlist/$playlistId")
        })
    }
}

@Composable
internal fun currentLibraryToolbarAction(navController: NavController): String? {
    val entry by navController.currentBackStackEntryAsState()
    return entry?.savedStateHandle
        ?.getStateFlow<String?>(LIBRARY_TOOLBAR_ACTION, null)
        ?.collectAsStateWithLifecycle()?.value
}

internal fun consumeLibraryToolbarAction(handle: SavedStateHandle, action: String): Boolean {
    if (handle.get<String>(LIBRARY_TOOLBAR_ACTION) != action) return false
    handle.set<String?>(LIBRARY_TOOLBAR_ACTION, null)
    return true
}
