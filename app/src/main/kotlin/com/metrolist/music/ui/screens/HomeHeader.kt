package com.metrolist.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

@Composable
internal fun HomeHero(onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sourcify_home_label), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.sourcify_home_heading), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            }
            Image(painterResource(R.drawable.sourcify_mark), null, Modifier.size(76.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .55f)).clickable(onClick = onSearch).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.search), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.search), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun HomeLibraryShortcuts(onNavigate: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(R.string.liked, R.drawable.favorite, "auto_playlist/liked"),
            Triple(R.string.downloaded_songs, R.drawable.offline, "auto_playlist/downloaded")).forEach { (title, icon, route) ->
            Card(onClick = { onNavigate(route) }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(title), minLines = 2, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
