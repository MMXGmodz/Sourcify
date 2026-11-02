package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R

@Composable
internal fun ProfileDrawerContent(name: String, image: String?, loggedIn: Boolean, hasUpdate: Boolean,
    onNavigate: (String) -> Unit, onAccountOptions: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxHeight().background(Color(0xFF202020)).systemBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.close), stringResource(R.string.close), tint = Color.White) }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF282828))) {
            Row(Modifier.fillMaxWidth().clickable { onNavigate(if (loggedIn) "account" else "login") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (image != null) AsyncImage(image, null, Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Icon(painterResource(R.drawable.account), null, tint = Color.White, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name.ifBlank { stringResource(R.string.sourcify_wordmark) }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (loggedIn) R.string.s7_view_profile else R.string.login), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .06f))
            ProfileDrawerRow(if (loggedIn) R.drawable.account else R.drawable.add,
                stringResource(if (loggedIn) R.string.switch_youtube_channel else R.string.s7_add_account),
                { onNavigate(if (loggedIn) "switch_channel" else "login") })
        }
        Spacer(Modifier.height(12.dp))
        ProfileDrawerRow(R.drawable.stats, stringResource(R.string.stats), { onNavigate("stats") })
        ProfileDrawerRow(R.drawable.history, stringResource(R.string.history), { onNavigate("history") })
        ProfileDrawerRow(R.drawable.notification, stringResource(R.string.search_new_releases), { onNavigate("new_release") })
        ProfileDrawerRow(R.drawable.integration, stringResource(R.string.integrations), { onNavigate("settings/integrations") })
        ProfileDrawerRow(R.drawable.settings, stringResource(R.string.s7_settings_privacy), { onNavigate("settings") }, hasUpdate)
        ProfileDrawerRow(R.drawable.account, stringResource(R.string.s7_account_options), onAccountOptions)
        ProfileDrawerRow(R.drawable.update, stringResource(R.string.s1382_updates),
            { onNavigate("settings/updater") }, hasUpdate)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileDrawerRow(icon: Int, title: String, onClick: () -> Unit, badge: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        if (badge) Badge(containerColor = Color(0xFF4C91FF))
    }
}

@Composable
internal fun ProfileNavigationDrawer(state: DrawerState, sheetContent: @Composable () -> Unit, content: @Composable () -> Unit) {
    var width by remember { mutableFloatStateOf(0f) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    ModalNavigationDrawer(drawerState = state, gesturesEnabled = state.isOpen, scrimColor = Color.Black.copy(alpha = .55f),
        drawerContent = {
            ModalDrawerSheet(drawerState = state, modifier = Modifier.fillMaxWidth(.9f).onSizeChanged { width = it.width.toFloat() },
                drawerShape = androidx.compose.ui.graphics.RectangleShape, drawerContainerColor = Color(0xFF202020), windowInsets = WindowInsets(0, 0, 0, 0)) { sheetContent() }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val offset = state.currentOffset
            translationX = if (offset.isFinite()) (width + offset).coerceIn(0f, width) * direction else 0f
        }) { content() }
    }
}
