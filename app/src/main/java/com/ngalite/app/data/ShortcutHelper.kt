package com.ngalite.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ngalite.app.MainActivity

/**
 * 将板块添加到桌面快捷方式。
 *
 * 支持 Android 8.0+（minSdk=26），使用 [ShortcutManagerCompat] 统一处理：
 * - Android 8.0+ 使用 Pinned Shortcuts API，系统弹出"添加到主屏幕"确认对话框
 * - 通过 Intent extra 携带 fid 和板块名，[MainActivity] 接收后直接跳转帖子列表
 */
object ShortcutHelper {

    private const val EXTRA_FID = "extra_fid"
    private const val EXTRA_FORUM_NAME = "extra_forum_name"

    /**
     * 请求为指定板块创建桌面快捷方式。
     * @return true 表示请求已提交（系统会弹出确认对话框），false 表示不支持或添加失败
     */
    fun requestPinShortcut(context: Context, forum: Forum): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_FID, forum.fid)
            putExtra(EXTRA_FORUM_NAME, forum.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val icon = buildIcon(context, forum)
        val shortcut = ShortcutInfoCompat.Builder(context, "forum_${forum.fid}")
            .setShortLabel(forum.name)
            .setLongLabel("NgaLite - ${forum.name}")
            .setIcon(icon)
            .setIntent(intent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /** 从启动 Intent 中提取 fid，用于快捷方式启动时直接跳转 */
    fun extractFid(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_FID)
    }

    /** 从启动 Intent 中提取板块名 */
    fun extractForumName(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_FORUM_NAME)
    }

    /**
     * 构建快捷方式图标：优先使用 assets 中的板块图标，无图标则生成首字占位图。
     */
    private fun buildIcon(context: Context, forum: Forum): IconCompat {
        // 尝试从 assets 加载板块图标
        val iconAssetName = "icons/f${forum.fid}.png"
        try {
            val inputStream = context.assets.open(iconAssetName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap != null) {
                return IconCompat.createWithAdaptiveBitmap(bitmap)
            }
        } catch (_: Exception) {
            // 无板块图标，走占位逻辑
        }

        // 生成首字占位图标
        val bitmap = renderTextIcon(forum.name.firstOrNull()?.toString() ?: "?")
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /** 渲染单字为正方形 Bitmap，用于无图标板块的快捷方式占位 */
    private fun renderTextIcon(text: String): Bitmap {
        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F3F3"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#636366")
            textAlign = Paint.Align.CENTER
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val rect = Rect()
        paint.getTextBounds(text, 0, text.length, rect)
        val x = size / 2f
        val y = size / 2f + (rect.height() / 2f)
        canvas.drawText(text, x, y, paint)
        return bitmap
    }
}
