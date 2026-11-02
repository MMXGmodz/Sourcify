package com.metrolist.music.ui.screens.library

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.music.R

internal const val LIBRARY_TOOLBAR_ACTION = "libraryToolbarAction"
internal const val LIBRARY_SEARCH_ACTION = "search"
internal const val LIBRARY_CREATE_ACTION = "create"

@Composable
fun LibraryTopActions(navController: NavController) {
    IconButton(onClick = {
        navController.currentBackStackEntry?.savedStateHandle?.set(LIBRARY_TOOLBAR_ACTION, LIBRARY_SEARCH_ACTION)
    }) {
        Icon(painterResource(R.drawable.search), stringResource(R.string.search_library))
    }
    IconButton(onClick = {
        navController.currentBackStackEntry?.savedStateHandle?.set(LIBRARY_TOOLBAR_ACTION, LIBRARY_CREATE_ACTION)
    }) {
        Icon(painterResource(R.drawable.add), stringResource(R.string.create_playlist))
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
