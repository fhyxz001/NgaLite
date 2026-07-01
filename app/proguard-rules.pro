# Add project specific ProGuard rules here.

# 保留 Jsoup 解析能力
-keep class org.jsoup.** { *; }

# 保留 OkHttp 网络层
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# 保留数据模型（用于 JSON/序列化反射）
-keep class com.ngalite.app.data.** { *; }

# 保留 Coil 图片加载
-dontwarn coil.**

# 保留 ZXing 二维码
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 保留 Compose 相关
-dontwarn androidx.compose.**

# Kotlin 协程
-dontwarn kotlinx.coroutines.**

# 保留 RSA 加密相关
-keep class javax.crypto.** { *; }
-dontwarn javax.crypto.**
