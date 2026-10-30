/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val insets = LocalPlayerAwareWindowInsets.current
    var showLicenses by remember { mutableStateOf(false) }
    val notices = remember(context) {
        context.assets.open("sourcify_licenses.txt").bufferedReader().use { it.readText() }
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 72.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.secondaryContainer)))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.sourcify_mark), stringResource(R.string.app_name), Modifier.size(80.dp))
            Text(stringResource(R.string.sourcify_wordmark), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text(stringResource(R.string.sourcify_credits_tagline), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.sourcify_credits), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        listOf(
            Triple(R.string.sourcify_mmxg, R.string.sourcify_mmxg_role, "M"),
            Triple(R.string.sourcify_vibecoded, R.string.sourcify_vibecoded_role, "V"),
        ).forEach { (name, role, initial) ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(initial, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(name), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(role), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Surface(onClick = { uriHandler.openUri("https://github.com/MMXGmodz/Sourcify") },
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.link), null)
                Text(stringResource(R.string.sourcify_github))
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(onClick = { showLicenses = true }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.info), null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.sourcify_licenses), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(painterResource(R.drawable.arrow_forward), null)
            }
        }
    }
    TopAppBar(title = { Text(stringResource(R.string.sourcify_credits)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
            }
        },
    )
    if (showLicenses) {
        AlertDialog(onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.sourcify_licenses)) },
            text = {
                SelectionContainer {
                    Text(notices, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
