package com.example.core.model

import java.io.Serializable

data class TimeRecord(
    val id: Int = 0,
    val title: String,
    val category: String, // Learning, Reading, Workout, Project, Course, Others
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Long,
    val remark: String,
    val userId: String = "local_user"
) : Serializable

data class Goods(
    val id: Int = 0,
    val title: String,
    val description: String,
    val imageUrl: String,
    val price: Double,
    val sellerName: String,
    val sellerId: String = "local_user",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : Serializable

data class Friend(
    val id: String,
    val nickname: String,
    val avatarUrl: String,
    val bio: String,
    val status: String // "pending", "friend"
) : Serializable

data class ChatMessage(
    val id: Int = 0,
    val friendId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) : Serializable

data class UserMessage(
    val id: Int = 0,
    val profileUserId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isApproved: Boolean = true // For Admin Review Filter
) : Serializable

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String, // Material Icon name or descriptor
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val criteriaDescription: String
) : Serializable

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val updateLog: String,
    val apkUrl: String,
    val isForceUpdate: Boolean,
    val isGrayUpdate: Boolean = false
) : Serializable
