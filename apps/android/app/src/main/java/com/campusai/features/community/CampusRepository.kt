package com.campusai.features.community

import com.campusai.core.network.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

data class CommunityPost(
    val id: String,
    val authorId: String,
    val author: String,
    val body: String,
    val topic: String,
    val mediaUrl: String,
    val anonymous: Boolean,
    val likes: Int,
    val comments: Int,
    val createdAt: String,
)

data class MarketplaceListing(
    val id: String,
    val sellerId: String,
    val seller: String,
    val title: String,
    val description: String,
    val priceCents: Int,
    val location: String,
    val mediaUrl: String,
    val status: String,
    val createdAt: String,
)

class CampusRepository {
    suspend fun loadPosts(): Result<List<CommunityPost>> = SupabaseClient.restGet(
        table = "posts",
        parameters = mapOf(
            "select" to "id,author_id,body,topic,media_paths,is_anonymous,like_count,comment_count,created_at,author:profiles!posts_author_id_fkey(display_name)",
            "deleted_at" to "is.null",
            "order" to "created_at.desc",
            "limit" to "50",
        ),
    ).map { rows -> List(rows.length()) { index -> parsePost(rows.getJSONObject(index)) } }

    suspend fun publishPost(userId: String, body: String, topic: String, anonymous: Boolean): Result<CommunityPost> =
        SupabaseClient.restInsert(
            "posts",
            JSONObject()
                .put("author_id", userId)
                .put("body", body.trim())
                .put("topic", topic.trim().ifBlank { JSONObject.NULL })
                .put("is_anonymous", anonymous),
        ).map(::parsePost)

    suspend fun togglePostLike(postId: String): Result<Pair<Boolean, Int>> = SupabaseClient.rpc(
        "toggle_post_like",
        JSONObject().put("target_post", postId),
    ).map { it.optBoolean("liked") to it.optInt("count") }

    suspend fun togglePostBookmark(postId: String): Result<Boolean> = SupabaseClient.rpc(
        "toggle_post_bookmark",
        JSONObject().put("target_post", postId),
    ).map { it.optString("value").toBooleanStrictOrNull() ?: false }

    suspend fun loadListings(): Result<List<MarketplaceListing>> = SupabaseClient.restGet(
        table = "listings",
        parameters = mapOf(
            "select" to "id,seller_id,title,description,price_cents,location,media_paths,status,created_at,seller:profiles!listings_seller_id_fkey(display_name)",
            "order" to "created_at.desc",
            "limit" to "50",
        ),
    ).map { rows -> List(rows.length()) { index -> parseListing(rows.getJSONObject(index)) } }

    suspend fun publishListing(
        userId: String,
        title: String,
        description: String,
        priceCents: Int,
        location: String,
    ): Result<MarketplaceListing> = SupabaseClient.restInsert(
        "listings",
        JSONObject()
            .put("seller_id", userId)
            .put("title", title.trim())
            .put("description", description.trim())
            .put("price_cents", priceCents)
            .put("category", "其他")
            .put("condition", "良好")
            .put("location", location.trim())
            .put("status", "active"),
    ).map(::parseListing)

    suspend fun toggleFavorite(listingId: String): Result<Boolean> = SupabaseClient.rpc(
        "toggle_favorite",
        JSONObject().put("target_listing", listingId),
    ).map { it.optString("value").toBooleanStrictOrNull() ?: false }

    private fun parsePost(item: JSONObject): CommunityPost {
        val media = item.optJSONArray("media_paths") ?: JSONArray()
        val author = item.optJSONObject("author")?.optString("display_name").orEmpty()
        val anonymous = item.optBoolean("is_anonymous")
        return CommunityPost(
            id = item.optString("id"),
            authorId = item.optString("author_id"),
            author = if (anonymous) "匿名同学" else author.ifBlank { "CampusAI 用户" },
            body = item.optString("body"),
            topic = item.optString("topic"),
            mediaUrl = media.optString(0).takeIf { it.isNotBlank() }?.let { SupabaseClient.publicMediaUrl("post-media", it) }.orEmpty(),
            anonymous = anonymous,
            likes = item.optInt("like_count"),
            comments = item.optInt("comment_count"),
            createdAt = item.optString("created_at"),
        )
    }

    private fun parseListing(item: JSONObject): MarketplaceListing {
        val media = item.optJSONArray("media_paths") ?: JSONArray()
        return MarketplaceListing(
            id = item.optString("id"),
            sellerId = item.optString("seller_id"),
            seller = item.optJSONObject("seller")?.optString("display_name").orEmpty().ifBlank { "CampusAI 用户" },
            title = item.optString("title"),
            description = item.optString("description"),
            priceCents = item.optInt("price_cents"),
            location = item.optString("location"),
            mediaUrl = media.optString(0).takeIf { it.isNotBlank() }?.let { SupabaseClient.publicMediaUrl("listing-media", it) }.orEmpty(),
            status = item.optString("status"),
            createdAt = item.optString("created_at"),
        )
    }
}
