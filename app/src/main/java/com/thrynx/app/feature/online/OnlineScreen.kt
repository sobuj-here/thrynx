package com.thrynx.app.feature.online

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var inputQuery by remember { mutableStateOf("") }
    var currentWebUrl by remember { mutableStateOf("https://m.youtube.com") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(title = stringResource(id = R.string.online_title))

        // Platform Tabs
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
                text = { Text(stringResource(id = R.string.online_youtube_tab)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    currentWebUrl = "https://m.facebook.com/watch"
                },
                text = { Text(stringResource(id = R.string.online_facebook_tab)) }
            )
        }

        // Search / Link Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = {
                    Text(
                        if (selectedTab == 0) "Search YouTube or paste URL…"
                        else stringResource(id = R.string.online_fb_paste_link)
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        currentWebUrl = if (inputQuery.startsWith("http://") || inputQuery.startsWith("https://")) {
                            inputQuery
                        } else {
                            if (selectedTab == 0) {
                                "https://m.youtube.com/results?search_query=${Uri.encode(inputQuery)}"
                            } else {
                                "https://m.facebook.com/watch"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(id = R.string.online_search_button), color = ThrynxBgDark, fontWeight = FontWeight.Bold)
            }
        }

        // Action: Open in Official App
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentWebUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.online_open_external), color = ThrynxCyan)
            }
        }

        // Platform Policy Disclaimer Card
        ThrynxCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Policy, contentDescription = null, tint = ThrynxCobalt, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.online_policy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Embedded Browser Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 80.dp)
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
