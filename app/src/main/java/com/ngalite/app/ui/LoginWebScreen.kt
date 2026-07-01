package com.ngalite.app.ui

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.NgaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.charset.Charset

private const val LOGIN_URL = "https://bbs.nga.cn/nuke.php?__lib=login&__act=account&login"
private const val BASE_URL = "https://bbs.nga.cn/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWebScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun captureCookies(): Boolean {
        val cookies = CookieManager.getInstance().getCookie(LOGIN_URL) ?: ""
        if (cookies.isBlank() || !cookies.contains("ngaPassportUid")) return false
        CookieStore.save(cookies)
        val uid = Regex("ngaPassportUid=(\\d+)").find(cookies)?.groupValues?.lastOrNull().orEmpty()
        if (uid.isNotBlank()) CookieStore.saveAccountName("UID:$uid")
        return true
    }

    fun loadPage(wv: WebView) {
        isLoading = true
        loadError = null
        scope.launch {
            try {
                val html = withContext(Dispatchers.IO) { fetchLoginPageHtml() }
                wv.loadDataWithBaseURL(BASE_URL, html, "text/html", "GBK", null)
            } catch (e: Exception) {
                loadError = "页面加载失败：${e.message}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NGA 账号登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (captureCookies()) {
                            Toast.makeText(context, "Cookie 已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "请先在页面中完成登录", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("完成登录", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx ->
                    createWebView(ctx) { isLoading = it }.also { wv -> loadPage(wv) }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (isLoading && loadError == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            loadError?.let { msg ->
                TextButton(onClick = {
                    // 需要 WebView 引用才能重试，但这里拿不到
                    // 用户可点返回重新进入
                }) {
                    Text("$msg\n请返回后重试", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val pageClient by lazy { NgaApi.sharedClientBuilder().build() }

private fun fetchLoginPageHtml(): String {
    val req = Request.Builder()
        .url(LOGIN_URL)
        .header("User-Agent", NgaApi.UA)
        .build()
    pageClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
        val bytes = resp.body?.bytes() ?: throw RuntimeException("响应为空")
        return String(bytes, Charset.forName("GBK"))
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    onLoadingChanged: (Boolean) -> Unit
): WebView {
    return WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = NgaApi.UA
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            allowContentAccess = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                msg?.let {
                    Log.d("LoginWebView", "[${it.messageLevel()}] ${it.message()} — ${it.sourceId()}:${it.lineNumber()}")
                }
                return super.onConsoleMessage(msg)
            }
        }
    }
}
