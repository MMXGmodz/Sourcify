package com.metrolist.music.connect

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRouter
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import androidx.annotation.RequiresApi

// Public route types require Android 14; older devices use the classic audio router.
internal fun androidAudioRoutes(context: Context): AudioRoutes =
    if (Build.VERSION.SDK_INT >= 34) try { ModernAudioRoutes(context) } catch (_: Exception) { LegacyAudioRoutes(context) } else LegacyAudioRoutes(context)

@RequiresApi(34)
private class ModernAudioRoutes(private val context: Context) : AudioRoutes {
    private val router = MediaRouter2.getInstance(context)
    private var changed: ((List<AudioRoute>) -> Unit)? = null
    private var failed: (() -> Unit)? = null
    private var started = false
    private var available = emptyMap<String, MediaRoute2Info>()
    private val routes = object : MediaRouter2.RouteCallback() {
        override fun onRoutesAdded(routes: MutableList<MediaRoute2Info>) = publish()
        override fun onRoutesUpdated(routes: MutableList<MediaRoute2Info>) = publish()
        override fun onRoutesRemoved(routes: MutableList<MediaRoute2Info>) = publish()
        override fun onRoutesChanged(routes: MutableList<MediaRoute2Info>) = publish()
    }
    private val controller = object : MediaRouter2.ControllerCallback() {
        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) = publish()
    }
    private val transfers = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(oldController: MediaRouter2.RoutingController, newController: MediaRouter2.RoutingController) = publish()
        override fun onTransferFailure(route: MediaRoute2Info) { failed?.invoke(); publish() }
    }
    override fun start(changed: (List<AudioRoute>) -> Unit, failed: () -> Unit) {
        this.changed = changed; this.failed = failed
        // Passive system-route updates: pairing and Bluetooth discovery stay in Android Settings.
        router.registerRouteCallback(context.mainExecutor, routes,
            RouteDiscoveryPreference.Builder(listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO), false).build())
        started = true
        router.registerControllerCallback(context.mainExecutor, controller)
        router.registerTransferCallback(context.mainExecutor, transfers)
        publish()
    }
    private fun key(route: MediaRoute2Info) = if (route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) "phone" else route.id
    private fun publish() {
        if (!started) return
        try {
        val selected = router.systemController.selectedRoutes
        val all = (selected + router.routes).filter { it.isSystemRoute }.distinctBy(::key)
        available = all.associateBy(::key)
        changed?.invoke(all.map { route -> AudioRoute(key(route), route.name.toString(), when (route.type) {
            MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> DeviceIcon.PHONE
            MediaRoute2Info.TYPE_WIRED_HEADPHONES, MediaRoute2Info.TYPE_WIRED_HEADSET,
            MediaRoute2Info.TYPE_BLUETOOTH_A2DP, MediaRoute2Info.TYPE_BLE_HEADSET, MediaRoute2Info.TYPE_HEARING_AID -> DeviceIcon.HEADPHONES
            MediaRoute2Info.TYPE_HDMI, MediaRoute2Info.TYPE_HDMI_ARC, MediaRoute2Info.TYPE_HDMI_EARC -> DeviceIcon.TV
            MediaRoute2Info.TYPE_USB_HEADSET -> DeviceIcon.HEADPHONES
            else -> DeviceIcon.SPEAKER
        }, selected.any { it.id == route.id }) })
        } catch (_: SecurityException) { changed?.invoke(emptyList()) }
        catch (_: IllegalStateException) { changed?.invoke(emptyList()) }
    }
    override fun select(id: String): Boolean {
        val route = available[id] ?: return false
        router.transferTo(route)
        return true
    }
    override fun close() {
        changed = null; failed = null; available = emptyMap()
        if (!started) return
        started = false
        router.unregisterRouteCallback(routes)
        router.unregisterControllerCallback(controller)
        router.unregisterTransferCallback(transfers)
    }
}

@Suppress("DEPRECATION")
private class LegacyAudioRoutes(context: Context) : AudioRoutes {
    private val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
    private var changed: ((List<AudioRoute>) -> Unit)? = null
    private var available = emptyMap<String, MediaRouter.RouteInfo>()
    private var started = false
    private val callback = object : MediaRouter.SimpleCallback() {
        override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) = publish()
        override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) = publish()
        override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = publish()
        override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = publish()
        override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = publish()
    }
    // shortcut: the classic router has no persistent ID; identical Android names share one entry.
    private fun key(route: MediaRouter.RouteInfo) = if (route == router.defaultRoute) "phone" else "legacy:${route.deviceType}:${route.name}"
    override fun start(changed: (List<AudioRoute>) -> Unit, failed: () -> Unit) {
        this.changed = changed
        started = true
        router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, callback)
        publish()
    }
    private fun publish() {
        if (!started) return
        val selected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        val all = (0 until router.routeCount).map(router::getRouteAt).filter { it.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL }
        available = all.associateBy(::key)
        changed?.invoke(all.map { route -> AudioRoute(key(route), route.name.toString(),
            if (route == router.defaultRoute) DeviceIcon.PHONE else DeviceIcon.HEADPHONES, route == selected) })
    }
    override fun select(id: String): Boolean {
        val route = available[id]?.takeIf { it.isEnabled } ?: return false
        router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, route)
        return true
    }
    override fun close() { changed = null; available = emptyMap(); if (started) router.removeCallback(callback); started = false }
}
