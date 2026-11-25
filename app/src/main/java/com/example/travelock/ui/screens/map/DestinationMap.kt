package com.example.travelock.ui.screens.map

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.model.DestinationMapItem
import com.example.travelock.data.model.DestinationMapStatus
import com.example.travelock.data.model.RoutePlan
import com.example.travelock.ui.theme.LocalThemeMode
import com.example.travelock.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private const val MAP_ORIGIN = "https://map.travelock.invalid"
private enum class MapLoadState { LOADING, READY, FAILED }

@Composable
internal fun DestinationMap(
    items: List<DestinationMapItem>,
    route: RoutePlan?,
    location: DeviceLocationResult.Available?,
    centerRequest: Int,
    savedCamera: DoubleArray?,
    onCameraChanged: (DoubleArray) -> Unit,
    attempt: Int,
    onRetry: () -> Unit,
    onSelect: (DestinationMapItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = when (LocalThemeMode.current) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = if (dark) "dark" else "liberty"
    val styleUrl = "https://tiles.openfreemap.org/styles/$style"
    val latestItems by rememberUpdatedState(remember(items) { items.associateBy { it.destination.destinationId } })
    val select by rememberUpdatedState(onSelect)
    val saveCamera by rememberUpdatedState(onCameraChanged)
    val template = remember(context) {
        context.assets.open("destination-map.html").bufferedReader().use { it.readText() }
    }

    key(attempt) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var state by remember { mutableStateOf(MapLoadState.LOADING) }
        val initialCamera = remember { savedCamera }
        val initialCenterRequest = remember { centerRequest }
        val initialStyle = remember { styleUrl }
        LaunchedEffect(state) {
            if (state == MapLoadState.LOADING) {
                delay(25_000)
                state = MapLoadState.FAILED
            }
        }
        LaunchedEffect(webView, state, items, route, location, centerRequest) {
            if (state == MapLoadState.READY) {
                webView?.evaluateJavascript(
                    "updateMap(${mapData(items, route, location, centerRequest)})",
                    null,
                )
            }
        }
        LaunchedEffect(webView, state, styleUrl) {
            if (state == MapLoadState.READY) {
                webView?.evaluateJavascript("setMapStyle(${JSONObject.quote(styleUrl)})", null)
            }
        }

        Box(modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        setBackgroundColor(if (dark) Color.DKGRAY else Color.WHITE)
                        // This view only displays our bundled map page. There is no native JavaScript interface.
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return true
                                val url = request.url
                                if (url.scheme == "https" && url.host == "map.travelock.invalid") {
                                    when (url.path) {
                                        "/ready" -> state = MapLoadState.READY
                                        "/failed" -> state = MapLoadState.FAILED
                                        "/marker" -> latestItems[url.getQueryParameter("id")]?.let(select)
                                        "/camera" -> {
                                            val camera = listOf("longitude", "latitude", "zoom")
                                                .mapNotNull { url.getQueryParameter(it)?.toDoubleOrNull() }
                                            if (camera.size == 3 && camera.all { it.isFinite() }) {
                                                saveCamera(camera.toDoubleArray())
                                            }
                                        }
                                    }
                                } else if (url.scheme == "https" && url.host in setOf(
                                        "openfreemap.org", "openmaptiles.org", "www.openstreetmap.org",
                                    )) {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    try {
                                        viewContext.startActivity(intent)
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        // The map is still usable when there is no external browser.
                                    }
                                }
                                return true
                            }
                        }
                        val config = JSONObject()
                            .put("style", initialStyle)
                            .put("camera", initialCamera?.let { JSONArray(it.toList()) })
                            .put("centerRequest", initialCenterRequest)
                        loadDataWithBaseURL(
                            MAP_ORIGIN,
                            template.replace("/* MAP_CONFIG */", config.toString().replace("<", "\\u003c")),
                            "text/html",
                            "UTF-8",
                            null,
                        )
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            when (state) {
                MapLoadState.LOADING -> Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.medium,
                ) { CircularProgressIndicator(Modifier.padding(12.dp)) }
                MapLoadState.FAILED -> Surface(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load the street map")
                        Button(onClick = onRetry) { Text("Retry map") }
                    }
                }
                MapLoadState.READY -> Unit
            }
        }
    }
}

private fun mapData(
    items: List<DestinationMapItem>,
    route: RoutePlan?,
    location: DeviceLocationResult.Available?,
    centerRequest: Int,
): JSONObject {
    val markers = JSONArray()
    items.forEach { item ->
        val destination = item.destination
        markers.put(JSONObject()
            .put("id", destination.destinationId)
            .put("name", destination.name)
            .put("point", JSONArray(listOf(destination.longitude, destination.latitude)))
            .put("unlocked", item.status == DestinationMapStatus.UNLOCKED))
    }
    val routePoints = JSONArray()
    route?.start?.let { routePoints.put(JSONArray(listOf(it.longitude, it.latitude))) }
    route?.stops.orEmpty().forEach { routePoints.put(JSONArray(listOf(it.longitude, it.latitude))) }
    return JSONObject()
        .put("markers", markers)
        .put("route", routePoints)
        .put("position", location?.let { JSONArray(listOf(it.longitude, it.latitude)) })
        .put("centerRequest", centerRequest)
}
