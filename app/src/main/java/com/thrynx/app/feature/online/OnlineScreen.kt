package com.thrynx.app.feature.online

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineScreen(
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var inputQuery by remember { mutableStateOf("") }
    var currentWebUrl by remember { mutableStateOf(if (initialTab == 0) "https://m.youtube.com" else "https://m.facebook.com/watch") }
    var isConnected by remember { mutableStateOf(isNetworkAvailable(context)) }

    // Local Search History (Phase 4 requirement)
    var searchHistory by remember { mutableStateOf(listOf("Cinematic Trailer", "Travel Vlog", "Bangla Music", "Nature 4K")) }

    fun runSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            if (!searchHistory.contains(trimmed)) {
                searchHistory = (listOf(trimmed) + searchHistory).take(6)
            }
            currentWebUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                if (selectedTab == 0) {
                    "https://m.youtube.com/results?search_query=${Uri.encode(trimmed)}"
                } else {
                    "https://m.facebook.com/watch"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(title = stringResource(id = R.string.online_title))

        // Platform Selector Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ThrynxCyan
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    currentWebUrl = "https://m.youtube.com"
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.online_youtube_tab), fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    currentWebUrl = "https://m.facebook.com/watch"
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.online_facebook_tab), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Network Status Check & Banner
        if (!isConnected) {
            Surface(
                color = ThrynxCoral.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = ThrynxCoral)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Internet Connection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThrynxCoral
                        )
                        Text(
                            text = "Online discovery requires an active network. Local player and editor remain 100% functional offline.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { isConnected = isNetworkAvailable(context) }) {
                        Text("Retry", color = ThrynxCoral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = {
                    Text(
                        if (selectedTab == 0) "Search YouTube or paste video URL…"
                        else "Paste shared Facebook video link…"
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputQuery.isNotBlank()) {
                        IconButton(onClick = { inputQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { runSearch(inputQuery) },
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(id = R.string.online_search_button),
                    color = ThrynxBgDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Search History Chips (Phase 4 requirement)
        if (searchHistory.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                searchHistory.forEach { query ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                inputQuery = query
                                runSearch(query)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(query, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Official App Handoff & Sharing Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    try {
                        val uri = Uri.parse(currentWebUrl)
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open external application", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedTab == 0) "Open in YouTube App" else "Open in Facebook App",
                    color = ThrynxCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row {
                // Copy Link
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentWebUrl))
                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }

                // Share Link
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, currentWebUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = ThrynxCyan, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Platform Policy Disclaimer Card
        ThrynxCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Policy, contentDescription = null, tint = ThrynxCobalt, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.online_policy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Embedded Browser Canvas with official platform player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 72.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(currentWebUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != currentWebUrl) {
                        webView.loadUrl(currentWebUrl)
                    }
                }
            )
        }
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}
