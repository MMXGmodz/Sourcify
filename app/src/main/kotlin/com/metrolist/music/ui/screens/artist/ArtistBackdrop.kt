package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalSwipeToSongContainerColor
import com.metrolist.music.ui.theme.screenHeaderBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistBackdrop(
    title: String,
    showTitle: Boolean,
    onNavigateUp: () -> Unit,
    onBackToMain: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = screenHeaderBrush()
    CompositionLocalProvider(LocalSwipeToSongContainerColor provides Color.Transparent) {
        Box(modifier.fillMaxSize().background(backdrop)) {
            content()
            TopAppBar(
                modifier = if (showTitle) Modifier.background(backdrop) else Modifier,
                title = { if (showTitle) Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, onLongClick = onBackToMain) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        }
    }
}
