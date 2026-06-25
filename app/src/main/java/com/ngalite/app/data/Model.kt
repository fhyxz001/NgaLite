package com.ngalite.app.data

/** 帖子列表条目 */
data class Topic(
    val tid: String,
    val title: String,
    val replies: String,
    val author: String,
    val replyTime: String
)

/** 帖子详情中的单条回复（楼层） */
data class Post(
    val floor: String,
    val author: String,
    val date: String,
    val content: String
)
