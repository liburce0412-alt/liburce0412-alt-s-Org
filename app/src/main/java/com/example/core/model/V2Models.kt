package com.example.core.model

import java.io.Serializable

enum class UserRole {
    USER, MODERATOR, ADMIN, SUPER_ADMIN;
    
    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.lowercase()) {
                "moderator" -> MODERATOR
                "admin" -> ADMIN
                "super_admin" -> SUPER_ADMIN
                else -> USER
            }
        }
    }
}

data class SupabaseUser(
    val id: String,
    val nickname: String,
    val avatar: String,
    val email: String,
    val school: String,
    val college: String,
    val grade: String,
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val role: String = "user",
    val isBlocked: Boolean = false
) : Serializable {
    fun userRole() = UserRole.fromString(role)
}

data class SupabasePost(
    val id: Int = 0,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val imageUrl: String = "",
    val tags: String = "", // comma separated
    val topic: String = "",
    val isAnonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isApproved: Boolean = true
) : Serializable

data class SupabaseComment(
    val id: Int = 0,
    val postId: Int,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val parentId: Int? = null
) : Serializable

data class SupabaseLike(
    val id: Int = 0,
    val postId: Int,
    val userId: String
) : Serializable

data class SupabaseProduct(
    val id: Int = 0,
    val title: String,
    val description: String,
    val imageUrl: String = "",
    val price: Double,
    val originalPrice: Double = 0.0,
    val category: String,
    val condition: String = "九成新",
    val location: String = "北校区",
    val sellerId: String,
    val sellerName: String,
    val sellerAvatar: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending", // pending, active, completed, canceled, review_rejected
    val isApproved: Boolean = true
) : Serializable

data class SupabaseOrder(
    val id: Int = 0,
    val productId: Int,
    val productTitle: String,
    val productImageUrl: String,
    val buyerId: String,
    val buyerName: String,
    val sellerId: String,
    val sellerName: String,
    val price: Double,
    val status: String, // "待付款", "待交易", "已完成", "已取消"
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

data class SupabaseFavorite(
    val id: Int = 0,
    val productId: Int,
    val userId: String
) : Serializable

data class SupabaseConversation(
    val id: Int = 0,
    val participantA: String,
    val participantB: String,
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val partnerName: String = "",
    val partnerAvatar: String = ""
) : Serializable

data class SupabaseMessage(
    val id: Int = 0,
    val conversationId: Int,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val imageUrl: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

data class SupabaseReport(
    val id: Int = 0,
    val reporterId: String,
    val reportedType: String, // "post", "comment", "product", "user"
    val reportedId: String,   // can be dynamic string ID or Int
    val reason: String,       // "垃圾广告", "违法内容", "诈骗", "辱骂", "色情", "其他"
    val details: String = "",
    val status: String = "pending", // "pending", "resolved", "ignored"
    val createdAt: Long = System.currentTimeMillis(),
    val reportedContentDigest: String = "" // helper for admin visual preview
) : Serializable

data class SupabaseAnnouncement(
    val id: Int = 0,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
