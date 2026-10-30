package com.metrolist.music.ui.screens.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.viewmodels.MoodAndGenresViewModel

internal data class BrowseCategory(val title: String, val color: Color, val icon: Int, val route: String)

@Composable
fun SearchBrowseScreen(onSearch: () -> Unit, accountButton: @Composable () -> Unit = {}, viewModel: MoodAndGenresViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val genres by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val primary = listOf(
        BrowseCategory(stringResource(R.string.browse_music), Color(0xFFB60D82), R.drawable.music_note, SearchRoutes.resultRoute("music")),
        BrowseCategory(stringResource(R.string.browse_podcasts), Color(0xFF213967), R.drawable.radio, SearchRoutes.resultRoute("podcasts")),
        BrowseCategory(stringResource(R.string.search_made_for_you), Color(0xFF8865A8), R.drawable.library_music, "home"),
        BrowseCategory(stringResource(R.string.search_new_releases), Color(0xFFCA1749), R.drawable.album, "new_release"),
    )
    val backend = genres.orEmpty().flatMap { it.items }.distinctBy { it.title }.map { item ->
        val params = item.endpoint.params?.let { "?params=${Uri.encode(it)}" }.orEmpty()
        BrowseCategory(item.title, Color(item.stripeColor), R.drawable.album, "youtube_browse/${Uri.encode(item.endpoint.browseId)}$params")
    }
    val fallback = listOf(
        BrowseCategory("Pop", Color(0xFF8865A8), R.drawable.album, SearchRoutes.resultRoute("pop music")),
        BrowseCategory("Hip-Hop", Color(0xFF427E91), R.drawable.album, SearchRoutes.resultRoute("hip hop music")),
        BrowseCategory("Rock", Color(0xFFBA1F35), R.drawable.album, SearchRoutes.resultRoute("rock music")),
        BrowseCategory("Latin", Color(0xFFD0087F), R.drawable.album, SearchRoutes.resultRoute("latin music")),
    )
    SearchBrowseContent(primary + backend.ifEmpty { fallback }, onSearch, { navController.navigate(it) }, accountButton)
}

@Composable
internal fun SearchBrowseContent(categories: List<BrowseCategory>, onSearch: () -> Unit, onCategory: (String) -> Unit, accountButton: @Composable () -> Unit = {}) {
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D12)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            accountButton()
            Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF242424)).clickable(onClick = onSearch).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.search), contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            Text(stringResource(R.string.search_listen_prompt), color = Color.White.copy(alpha = 0.75f))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.search_browse_all), style = MaterialTheme.typography.headlineSmall,
                    color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(categories) { category ->
                Box(Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(10.dp))
                    .background(category.color).clickable { onCategory(category.route) }) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 12.dp).rotate(22f).size(76.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(category.icon), contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(46.dp))
                    }
                    Text(category.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 2, modifier = Modifier.padding(12.dp).fillMaxWidth(0.85f))
                }
            }
        }
    }
}
