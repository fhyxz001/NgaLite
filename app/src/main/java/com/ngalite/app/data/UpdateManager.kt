package com.ngalite.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用更新管理器：通过 GitHub Releases API 检查新版本，下载并安装 APK。
 */
object UpdateManager {

    private val releaseApiMirrors = listOf(
        "https://gh-proxy.com/https://api.github.com/repos/fhyxz001/NgaLite/releases/latest",
        "https://ghfast.top/https://api.github.com/repos/fhyxz001/NgaLite/releases/latest",
        "https://api.github.com/repos/fhyxz001/NgaLite/releases/latest"
    )

    /** 更新检查结果 */
    data class UpdateResult(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val message: String
    )

    private val client by lazy {
        NgaApi.sharedClientBuilder().build()
    }

    private val downloadClient by lazy {
        NgaApi.sharedClientBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 检查最新版本，与当前版本比较 */
    suspend fun checkUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        var lastError = "检查更新失败"
        for (apiUrl in releaseApiMirrors) {
            try {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "NgaLite")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "检查更新失败 (HTTP ${resp.code})"
                        return@use
                    }
                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) {
                        lastError = "检查更新失败：响应为空"
                        return@use
                    }
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    if (tagName.isBlank()) {
                        lastError = "检查更新失败：无法解析版本号"
                        return@use
                    }
                    // 从 assets 中获取下载地址
                    var downloadUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i)
                            val name = asset?.optString("name", "") ?: ""
                            if (name.endsWith(".apk")) {
                                // 通过镜像加速 GitHub 下载
                                downloadUrl = "https://ghcr.nju.edu.cn/" + asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    val hasUpdate = compareVersions(tagName, currentVersion) > 0
                    return@withContext if (hasUpdate) {
                        UpdateResult(true, tagName, downloadUrl, "发现新版本 v$tagName")
                    } else {
                        UpdateResult(false, tagName, null, "当前已是最新版本")
                    }
                }
            } catch (e: Exception) {
                lastError = "检查更新失败：${e.message}"
            }
        }
        UpdateResult(false, currentVersion, null, "$lastError，挂梯子试试")
    }

    /** 下载 APK 到缓存目录，onProgress 回调进度 (0-100) */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("下载失败 (HTTP ${resp.code})，挂梯子试试")
            val body = resp.body ?: throw RuntimeException("下载失败：响应为空，挂梯子试试")
            val totalBytes = body.contentLength()
            val file = File(context.externalCacheDir, "ngalite_update.apk")
            file.outputStream().use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        onProgress(((totalRead * 100) / totalBytes).toInt())
                    }
                }
                output.flush()
            }
            file
        }
    }

    /** 触发系统 APK 安装界面 */
    fun installApk(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                @Suppress("DEPRECATION")
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
        }
        context.startActivity(intent)
    }

    /** 版本号比较：返回正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等 */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
