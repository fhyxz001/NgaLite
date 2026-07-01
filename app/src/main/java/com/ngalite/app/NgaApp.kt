package com.ngalite.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import com.ngalite.app.data.NgaApi
import java.util.concurrent.TimeUnit

class NgaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 把已保存的登录 Cookie 注入 OkHttp CookieJar，避免请求时丢失登录态
        NgaApi.setLoginCookies(com.ngalite.app.data.CookieStore.get())
    }

    /** 配置 Coil 全局图片加载器：内存/磁盘缓存 + 图片复用连接池 */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // 不使用淡入：缓存命中的图片立即显示，减少感知延迟
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .connectionPool(ConnectionPool(8, 3, TimeUnit.MINUTES))
                    .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: NgaApp
            private set
    }
}
