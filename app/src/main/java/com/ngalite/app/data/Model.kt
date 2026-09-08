package com.ngalite.app.data

/** 帖子列表条目 */
data class Topic(
    val tid: String,
    val title: String,
    val replies: String,
    val author: String,
    val replyTime: String,
    val previewImages: List<String> = emptyList(),
    /** 最后回复者 uid，用于用户名缺失时回查 */
    val uid: String = ""
)

/** 帖子详情中的单条回复（楼层） */
data class Post(
    val floor: String,
    val author: String,
    val date: String,
    val likes: String = "0",
    val views: String = "0",
    val contentNodes: List<ContentNode>,
    /** 楼层作者 uid，用于用户名/头像缺失时回查 */
    val uid: String = "",
    /** 楼层作者头像完整地址（NGA 头像短码已还原） */
    val avatarUrl: String = ""
)

/** 用户简要信息：NGA 内嵌用户表 / 用户信息接口里的字段 */
data class UserBrief(
    val name: String = "",
    /** 头像短码（如 `.a/12345_0.jpg`），需用 [NgaParser.avatarUrl] 还原为完整地址 */
    val avatar: String = ""
)

/** 帖子正文中的内容节点 */
sealed class ContentNode {
    /** 纯文本节点 */
    data class Text(val text: String) : ContentNode()
    /** 图片节点 */
    data class Image(val url: String) : ContentNode()
    /** 引用块节点 */
    data class Quote(val content: String) : ContentNode()
    /** 表情包图片（匹配 assets 中 ac/a2/ng/pst/dt/pg 文件夹下的同名 png） */
    data class Emoji(val folder: String, val name: String) : ContentNode()
}
