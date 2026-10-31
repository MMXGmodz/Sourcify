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
    val playlists by viewModel.browsePlaylists.collectAsStateWithLifecycle()
    val artwork by viewModel.categoryArtwork.collectAsStateWithLifecycle()
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
    SearchBrowseContent(primary + backend.ifEmpty { fallback }, onSearch, { navController.navigate(it) }, accountButton, playlists, viewModel::loadBrowsePlaylists, artwork, viewModel::loadCategoryArtwork)
}

@Composable
internal fun SearchBrowseContent(categories: List<BrowseCategory>, onSearch: () -> Unit, onCategory: (String) -> Unit,
    accountButton: @Composable () -> Unit = {}, playlists: List<com.metrolist.innertube.models.PlaylistItem> = emptyList(), onRetry: () -> Unit = {}, artwork: Map<String,String?> = emptyMap(), onNeedArtwork: (String)->Unit = {}) {
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    fun category(title: String, query: String, color: Long) = BrowseCategory(title, Color(color), R.drawable.album, SearchRoutes.resultRoute(query))
    val entries = categories.take(2) + listOf(
        category(stringResource(R.string.s6_audiobooks), "Hörbücher", 0xFF243264),
        category(stringResource(R.string.s6_live), "live concerts", 0xFF9600DD),
        category(stringResource(R.string.s6_video), "music video shows", 0xFF1476DB),
        category(stringResource(R.string.s6_fitness), "fitness workout music", 0xFF666666))
    val recommendations = listOfNotNull(categories.getOrNull(2), categories.getOrNull(3)) + listOf(
        category(stringResource(R.string.s6_upcoming), "new music releases", 0xFF006855),
        category("OFFCULT", "alternative music", 0xFF777777),
        category("Halloween", "Halloween music", 0xFFDD3500),
        category(stringResource(R.string.s6_charts), "top music charts", 0xFF8865A8))
    val genreItems = listOf("Hip-Hop", "Dance / Electronic", "Pop", "Rock", "R&B / Soul", "Jazz", "K-Pop", "Latin", "Metal", "Country")
        .mapIndexed { index, name -> category(name, "$name music", if(index%2==0)0xFF3044B5 else 0xFF773C56) }
    val moodNames = listOf(R.string.s6_party, R.string.s6_fitness, R.string.s6_gaming, R.string.s6_mood, R.string.s6_car, R.string.s6_love, R.string.s6_relax, R.string.s6_focus)
    val moodQueries = listOf("party music", "fitness workout music", "gaming music", "feel good music", "driving music", "love songs", "relax music", "focus music")
    val moodItems = moodNames.mapIndexed { index, id -> category(stringResource(id), moodQueries[index], listOf(0xFF8865A8,0xFF777777,0xFFDA2142,0xFFD0087F,0xFF3044B5)[index%5]) }
    val listening = listOf(category(stringResource(R.string.s6_decades), "90s 80s hits", 0xFFA96B57),
        category(stringResource(R.string.s6_radio), "radio music mixes", 0xFFD0087F),
        category(stringResource(R.string.s6_discover), "new artists", 0xFF883D91), category("LEGENDS", "classic music legends", 0xFF6C252D))
    val genresTitle=stringResource(R.string.s6_genres)
    val moodsTitle=stringResource(R.string.s6_moods)
    val listenTitle=stringResource(R.string.s6_listen)
    Column(Modifier.fillMaxSize().background(Color(0xFF101010)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start=4.dp,top=12.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
            accountButton();Text(stringResource(R.string.search),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.padding(start=8.dp))
        }
        Row(Modifier.padding(horizontal=16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
            .clickable(onClick=onSearch).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.search),null,tint=Color.Black)
            Text(stringResource(R.string.search_listen_prompt),color=Color.Black)
        }
        LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.weight(1f),contentPadding=PaddingValues(16.dp,24.dp,16.dp,bottom+16.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(entries) { BrowseTile(it,onCategory,artwork[it.title],onNeedArtwork,2.6f) }
            item(span={GridItemSpan(maxLineSpan)}) { SectionHeading(stringResource(R.string.s6_playlists)) }
            item(span={GridItemSpan(maxLineSpan)}) {
                if(playlists.isEmpty()) TextButton(onClick=onRetry){Text(stringResource(R.string.s6_retry_playlists))}
                else androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(playlists.size) { index -> val playlist=playlists[index]
                        Box(Modifier.width(150.dp).height(220.dp).clip(RoundedCornerShape(12.dp)).clickable { onCategory("online_playlist/${Uri.encode(playlist.id)}") }) {
                            coil3.compose.AsyncImage(playlist.thumbnail,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.85f)))))
                            Text(playlist.title,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomStart).padding(12.dp),maxLines=3)
                        }
                    }
                }
            }
            item(span={GridItemSpan(maxLineSpan)}){SectionHeading(stringResource(R.string.s6_more))}
            items(recommendations){BrowseTile(it,onCategory,artwork[it.title],onNeedArtwork)}
            fun section(key: String,title: String,all: List<BrowseCategory>) {
                item(span={GridItemSpan(maxLineSpan)}){SectionHeading(title,stringResource(R.string.s6_all),{expanded=if(key in expanded)expanded-key else expanded+key})}
                items(if(key in expanded)all else all.take(6)){BrowseTile(it,onCategory,artwork[it.title],onNeedArtwork)}
            }
            section("genres",genresTitle,genreItems)
            section("moods",moodsTitle,moodItems)
            section("listen",listenTitle,listening + categories.drop(4))
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String?=null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
        if(action!=null)TextButton(onClick=onClick){Text(action,color=Color.LightGray)}
    }
}
@Composable
private fun BrowseTile(category: BrowseCategory,onCategory:(String)->Unit,artwork:String?,onNeedArtwork:(String)->Unit,aspect:Float=1.6f) {
    LaunchedEffect(category.title) { onNeedArtwork(category.title) }
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(8.dp)).background(category.color).clickable{onCategory(category.route)}) {
        Box(Modifier.align(Alignment.BottomEnd).offset(x=10.dp,y=12.dp).rotate(23f).size(70.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha=.25f)),contentAlignment=Alignment.Center) {
            if(artwork!=null) coil3.compose.AsyncImage(artwork,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(painterResource(category.icon),null,tint=Color.White,modifier=Modifier.size(40.dp))
        }
        Text(category.title,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=3,modifier=Modifier.padding(12.dp).fillMaxWidth(.8f))
    }
}
