package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R

@Composable
internal fun ProfileButton(imageUrl: String?, hasUpdate: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (hasUpdate) Badge() }) {
            if (imageUrl != null) AsyncImage(imageUrl, stringResource(R.string.account), Modifier.size(24.dp).clip(CircleShape))
            else Icon(painterResource(R.drawable.account), stringResource(R.string.account), Modifier.size(24.dp))
        }
    }
}
