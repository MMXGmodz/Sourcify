package com.metrolist.music.ui.screens.settings

import com.metrolist.music.ui.utils.backToMain

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.utils.ArtistBlocks
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedArtistsScreen(nav: NavController) {
 val context = LocalContext.current
 val blocks by remember { ArtistBlocks.observe(context) }.collectAsStateWithLifecycle(initialValue = emptyMap())
 val scope = rememberCoroutineScope()
 SettingsPage(stringResource(R.string.s80_blocked), { nav.navigateUp() },
     bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(), onBackToMain = nav::backToMain) {
  if (blocks.isEmpty()) Text(stringResource(R.string.s80_no_blocked), Modifier.padding(24.dp))
  blocks.forEach { (id, name) ->
   ListItem(headlineContent = { Text(name) }, trailingContent = {
    TextButton(onClick = { scope.launch { ArtistBlocks.set(context, id, name, false) } }) { Text(stringResource(R.string.s80_unblock)) }
   })
  }
 }
}
