package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.utils.ArtistConcert
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An abstract stage keeps missing or unavailable event posters distinct from artist photos. */
@Composable
internal fun ConcertArtwork(concert: ArtistConcert, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF18162E), Color(0xFF274C62), Color(0xFF19262C))))) {
        Canvas(Modifier.fillMaxSize()) {
            for ((origin, end) in listOf(.18f to .7f, .82f to .3f)) {
                val beam = Path().apply {
                    moveTo(size.width * origin, 0f)
                    lineTo(size.width * (end - .25f), size.height)
                    lineTo(size.width * (end + .25f), size.height)
                    close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(Color(0xFF93E9FF).copy(alpha = .35f), Color.Transparent)))
                drawCircle(Color(0xFFD0FAFF), 3.dp.toPx(), Offset(size.width * origin, 4.dp.toPx()))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0D1423)), startY = size.height * .5f, endY = size.height), topLeft = Offset(0f, size.height * .5f), size = androidx.compose.ui.geometry.Size(size.width, size.height * .5f))
            repeat(14) { index ->
                val radius = (9 + index % 3 * 3).dp.toPx()
                val center = Offset(size.width * index / 13f, size.height - radius * .6f)
                drawCircle(Color(0xFF0A101C), radius, center)
            }
        }
        val month = concert.day?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
            ?: runCatching { YearMonth.parse(concert.date).format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())) }.getOrNull()
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .12f)).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month?.uppercase(Locale.getDefault()) ?: concert.date.take(4), color = Color.White,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            concert.day?.let { Text(it.dayOfMonth.toString(), color = Color.White,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        }
    }
}
