package com.metrolist.music.widget

import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.metrolist.music.R

data class WidgetColors(
    val background: Int,
    val foreground: Int,
    val secondary: Int,
    val accent: Int,
    val onAccent: Int,
    val track: Int,
) {
    companion object {
        internal const val FallbackSeed = 0xFF6750A4.toInt()
        val Fallback = fromSeed(FallbackSeed)

        fun fromSeed(seed: Int): WidgetColors {
            val opaqueSeed = seed or Color.BLACK
            val background = ColorUtils.blendARGB(opaqueSeed, Color.BLACK, .80f)
            val accent = ColorUtils.blendARGB(opaqueSeed, Color.WHITE, .60f)
            val onAccent = if (ColorUtils.calculateContrast(Color.BLACK, accent) >= 4.5) Color.BLACK else Color.WHITE
            return WidgetColors(
                background = background,
                foreground = Color.WHITE,
                secondary = ColorUtils.blendARGB(background, Color.WHITE, .78f),
                accent = accent,
                onAccent = onAccent,
                track = ColorUtils.blendARGB(background, accent, .28f),
            )
        }
    }

    internal fun applyTo(views: RemoteViews, layout: Int) {
        fun tint(id: Int, color: Int) = views.setInt(id, "setColorFilter", color)
        if (layout != R.layout.widget_turntable) tint(R.id.widget_surface, background)
        when (layout) {
            R.layout.widget_music_player -> {
                views.setTextColor(R.id.widget_song_title, foreground)
                views.setTextColor(R.id.widget_artist_name, secondary)
                tint(R.id.widget_play_background, accent)
                tint(R.id.widget_play_pause, onAccent)
                tint(R.id.widget_like_button, accent)
                tint(R.id.widget_progress_fill, accent)
                tint(R.id.widget_progress_track, track)
            }
            R.layout.widget_compact_square -> {
                tint(R.id.widget_compact_play_background, accent)
                tint(R.id.widget_compact_play_pause, onAccent)
            }
            R.layout.widget_compact_wide -> {
                views.setTextColor(R.id.widget_wide_song_title, foreground)
                views.setTextColor(R.id.widget_wide_artist_name, secondary)
                tint(R.id.widget_wide_play_background, accent)
                tint(R.id.widget_wide_play_pause, onAccent)
                tint(R.id.widget_wide_like_button, accent)
            }
            R.layout.widget_turntable -> {
                tint(R.id.widget_turntable_nav_background, background)
                tint(R.id.widget_turntable_prev_button, foreground)
                tint(R.id.widget_turntable_next_button, foreground)
                tint(R.id.widget_turntable_play_background, accent)
                tint(R.id.widget_turntable_play_pause, onAccent)
            }
            R.layout.widget_playlist -> {
                views.setTextColor(R.id.widget_playlist_song_title, foreground)
                views.setTextColor(R.id.widget_playlist_artist_name, secondary)
                tint(R.id.widget_playlist_play_background, accent)
                tint(R.id.widget_playlist_play_pause, onAccent)
                tint(R.id.widget_playlist_prev_icon, foreground)
                tint(R.id.widget_playlist_next_icon, foreground)
                tint(R.id.widget_playlist_like_button, accent)
                tint(R.id.widget_playlist_progress_fill, accent)
                tint(R.id.widget_playlist_progress_track, track)
            }
        }
    }
}
