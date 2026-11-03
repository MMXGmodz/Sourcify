package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.PlayButtonStyle

@Composable
fun PlayerPlayButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int,
    contentDescription: String,
    style: PlayButtonStyle = PlayButtonStyle.STANDARD,
    accentColor: Color = Color(0xFF38E578),
    containerColor: Color = Color.White,
    iconColor: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .size(72.dp)
            .alpha(if (enabled) 1f else .45f)
            .clip(CircleShape)
            .then(if (style == PlayButtonStyle.METAL_RING) Modifier.metalPlayButtonFace(accentColor) else Modifier.background(containerColor))
            .clickable(enabled = enabled, role = Role.Button, onClick = { if (enabled) onClick() })
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (style == PlayButtonStyle.METAL_RING) Color.White else iconColor,
            modifier = Modifier.fillMaxSize(.43f),
        )
    }
}

private fun Modifier.metalPlayButtonFace(accentColor: Color) = drawWithCache {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val metal = Brush.sweepGradient(
        listOf(Color(0xFF6F7479), Color(0xFFF7FAFC), Color(0xFF585E64), Color(0xFFB8BDC2),
            Color(0xFF33383D), Color(0xFFE9EDF0), Color(0xFF6F7479)),
        center = center,
    )
    val face = Brush.sweepGradient(
        listOf(Color(0xFF34393E), Color(0xFF777C81), Color(0xFF24282C), Color(0xFF555B61),
            Color(0xFF181C20), Color(0xFF6B7177), Color(0xFF34393E)),
        center = center,
    )
    val glow = Brush.radialGradient(
        .75f to Color.Transparent,
        .86f to accentColor.copy(alpha = .42f),
        .94f to accentColor.copy(alpha = .12f),
        1f to Color.Transparent,
        center = center,
        radius = radius,
    )
    onDrawBehind {
        drawCircle(glow, radius)
        drawCircle(Color(0xFF15191D), radius * .91f)
        drawCircle(accentColor.copy(alpha = .25f), radius * .88f, style = Stroke(radius * .095f))
        drawCircle(accentColor, radius * .86f, style = Stroke(radius * .038f))
        drawCircle(Color(0xFF101417), radius * .818f)
        drawCircle(metal, radius * .793f)
        drawCircle(Color(0xFF101316), radius * .713f)
        drawCircle(face, radius * .695f)
        // Fine concentric tooling marks keep the procedural face looking brushed at every size.
        repeat(24) { index ->
            drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .055f else .025f),
                radius * (.12f + index * .024f), style = Stroke(.35.dp.toPx()))
        }
        drawCircle(Color.White.copy(alpha = .2f), radius * .69f, style = Stroke(.5.dp.toPx()))
    }
}
