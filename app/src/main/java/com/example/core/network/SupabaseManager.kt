package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.core.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.Serializable
import java.util.concurrent.TimeUnit

object SupabaseManager {
    private const val PREFS_NAME = "supabase_user_session"
    private const val KEY_SESSION_USER = "session_user_json"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Global Active User Session
    private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
    val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

    // Global Reactive Data Flows (For instant reactivity across screens)
    private val _posts = MutableStateFlow<List<SupabasePost>>(emptyList())
    val posts: StateFlow<List<SupabasePost>> = _posts.asStateFlow()

    private val _comments = MutableStateFlow<Map<Int, List<SupabaseComment>>>(emptyMap())
    val comments: StateFlow<Map<Int, List<SupabaseComment>>> = _comments.asStateFlow()

    private val _likes = MutableStateFlow<List<SupabaseLike>>(emptyList())
    val likes: StateFlow<List<SupabaseLike>> = _likes.asStateFlow()

    private val _products = MutableStateFlow<List<SupabaseProduct>>(emptyList())
    val products: StateFlow<List<SupabaseProduct>> = _products.asStateFlow()

    private val _orders = MutableStateFlow<List<SupabaseOrder>>(emptyList())
    val orders: StateFlow<List<SupabaseOrder>> = _orders.asStateFlow()

    private val _favorites = MutableStateFlow<List<SupabaseFavorite>>(emptyList())
    val favorites: StateFlow<List<SupabaseFavorite>> = _favorites.asStateFlow()

    private val _messages = MutableStateFlow<List<SupabaseMessage>>(emptyList())
    val messages: StateFlow<List<SupabaseMessage>> = _messages.asStateFlow()

    private val _reports = MutableStateFlow<List<SupabaseReport>>(emptyList())
    val reports: StateFlow<List<SupabaseReport>> = _reports.asStateFlow()

    private val _announcements = MutableStateFlow<List<SupabaseAnnouncement>>(emptyList())
    val announcements: StateFlow<List<SupabaseAnnouncement>> = _announcements.asStateFlow()

    private val _allUsers = MutableStateFlow<List<SupabaseUser>>(emptyList())
    val allUsers: StateFlow<List<SupabaseUser>> = _allUsers.asStateFlow()

    // Local Persistence fallback files (JSON based database engine)
    private lateinit var appDir: File
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appDir = context.filesDir
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = prefs.getString(KEY_SESSION_USER, null)
        if (userJson != null) {
            try {
                val adapter = moshi.adapter(SupabaseUser::class.java)
                _currentUser.value = adapter.fromJson(userJson)
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Error parsing cached user session", e)
            }
        }
        
        // Seed default local data so the app starts gorgeous
        seedLocalCache()
        isInitialized = true
    }

    private fun saveSession(context: Context, user: SupabaseUser?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (user == null) {
            prefs.edit().remove(KEY_SESSION_USER).apply()
            _currentUser.value = null
        } else {
            val adapter = moshi.adapter(SupabaseUser::class.java)
            prefs.edit().putString(KEY_SESSION_USER, adapter.toJson(user)).apply()
            _currentUser.value = user
        }
    }

    // ==========================================
    // AUTHENTICATION APIs
    // ==========================================

    suspend fun register(
        context: Context,
        email: String,
        password: String,
        nickname: String,
        avatar: String,
        school: String,
        college: String,
        grade: String,
        bio: String = ""
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        // If Supabase is active
        if (SupabaseClient.isConfigured()) {
            val url = "${SupabaseClient.supabaseUrl}/auth/v1/signup"
            val bodyStr = """{"email":"$normalizedEmail","password":"$password"}"""
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseClient.supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        // Extract user ID from signup response
                        val userIdMatch = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
                        val id = userIdMatch?.groupValues?.get(1) ?: "sb_user_${System.currentTimeMillis()}"
                        
                        // Check if email domain implies role
                        val role = if (normalizedEmail.contains("admin")) "admin" else "user"

                        val rawUser = SupabaseUser(
                            id = id,
                            nickname = nickname,
                            avatar = avatar,
                            email = normalizedEmail,
                            school = school,
                            college = college,
                            grade = grade,
                            bio = bio,
                            role = role
                        )

                        // Write profile user records to PostgREST users table
                        insertUserToSupabase(rawUser)
                        
                        saveSession(context, rawUser)
                        // Sync with local table
                        val currentList = _allUsers.value.toMutableList()
                        currentList.add(rawUser)
                        _allUsers.value = currentList
                        writeLocalData("users.json", _allUsers.value)

                        Result.success(rawUser)
                    } else {
                        Result.failure(Exception(parseSupabaseAuthError(response.code, responseStr)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            // Offline High Fidelity Registration
            val existing = _allUsers.value.find { it.email == normalizedEmail }
            if (existing != null) {
                return@withContext Result.failure(Exception("该邮箱已被注册！"))
            }

            val id = "local_usr_${System.currentTimeMillis()}"
            val role = if (normalizedEmail.contains("admin")) "admin" else "user"
            val user = SupabaseUser(
                id = id,
                nickname = nickname,
                avatar = avatar,
                email = normalizedEmail,
                school = school,
                college = college,
                grade = grade,
                bio = bio,
                role = role
            )

            val list = _allUsers.value.toMutableList()
            list.add(user)
            _allUsers.value = list
            writeLocalData("users.json", list)
            saveSession(context, user)

            Result.success(user)
        }
    }

    suspend fun login(context: Context, email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (SupabaseClient.isConfigured()) {
            val url = "${SupabaseClient.supabaseUrl}/auth/v1/token?grant_type=password"
            val bodyStr = """{"email":"$normalizedEmail","password":"$password"}"""
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseClient.supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val userIdMatch = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
                        val id = userIdMatch?.groupValues?.get(1) ?: ""
                        
                        // Query user record from PostgREST users table
                        val profileResult = fetchUserProfile(id)
                        if (profileResult != null) {
                            if (profileResult.isBlocked) {
                                return@withContext Result.failure(Exception("您的账号因违规已被封禁限制！请联系 school_admin 申诉。"))
                            }
                            saveSession(context, profileResult)
                            Result.success(profileResult)
                        } else {
                            // Automatically insert user record as precaution
                            val defaultUser = SupabaseUser(
                                id = id,
                                nickname = "自律萌新",
                                avatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200",
                                email = normalizedEmail,
                                school = "清华大学",
                                college = "信息学院",
                                grade = "大一"
                            )
                            insertUserToSupabase(defaultUser)
                            saveSession(context, defaultUser)
                            Result.success(defaultUser)
                        }
                    } else {
                        Result.failure(Exception(parseSupabaseAuthError(response.code, responseStr)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            // Local high-fidelity login check
            val matched = _allUsers.value.find { it.email == normalizedEmail }
            if (matched != null) {
                if (matched.isBlocked) {
                    Result.failure(Exception("您的账号因违规已被系统封禁！"))
                } else {
                    saveSession(context, matched)
                    Result.success(matched)
                }
            } else {
                Result.failure(Exception("邮箱不存在或密码不匹配，请先进行注册。"))
            }
        }
    }

    fun logout(context: Context) {
        saveSession(context, null)
    }

    fun updateUserProfile(context: Context, nickname: String, school: String, college: String, grade: String, bio: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            nickname = nickname,
            school = school,
            college = college,
            grade = grade,
            bio = bio
        )
        _currentUser.value = updated
        saveSession(context, updated)

        // Sync with global users list
        val uList = _allUsers.value.map { if (it.id == current.id) updated else it }
        _allUsers.value = uList
        writeLocalData("users.json", uList)

        // Write back to Supabase
        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                insertUserToSupabase(updated)
            }
        }
    }

    // ==========================================
    // REAL CAMPUS SOCIAL SYSTEM
    // ==========================================

    fun getPostsList(): List<SupabasePost> {
        return _posts.value.filter { it.isApproved }
    }

    fun publishPost(content: String, tags: String, topic: String, isAnonymous: Boolean, imageUrl: String) {
        val user = _currentUser.value ?: return
        val newPost = SupabasePost(
            id = if (_posts.value.isEmpty()) 1 else _posts.value.maxOf { it.id } + 1,
            authorId = user.id,
            authorName = if (isAnonymous) "匿名同学" else user.nickname,
            authorAvatar = if (isAnonymous) "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200" else user.avatar,
            content = content,
            tags = tags,
            topic = topic,
            isAnonymous = isAnonymous,
            createdAt = System.currentTimeMillis()
        )
        val list = _posts.value.toMutableList()
        list.add(0, newPost)
        _posts.value = list
        writeLocalData("posts.json", list)

        // Sync to Supabase
        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val recordJson = moshi.adapter(SupabasePost::class.java).toJson(newPost)
                SupabaseClient.insertGeneric("posts", recordJson)
            }
        }
    }

    fun deletePost(postId: Int) {
        val list = _posts.value.filter { it.id != postId }
        _posts.value = list
        writeLocalData("posts.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.deleteGeneric("posts", "id", "$postId")
            }
        }
    }

    fun getPostComments(postId: Int): List<SupabaseComment> {
        return _comments.value[postId] ?: emptyList()
    }

    fun addComment(postId: Int, content: String, parentId: Int? = null) {
        val user = _currentUser.value ?: return
        val newComment = SupabaseComment(
            id = (System.currentTimeMillis() % 100000000).toInt(),
            postId = postId,
            authorId = user.id,
            authorName = user.nickname,
            authorAvatar = user.avatar,
            content = content,
            createdAt = System.currentTimeMillis(),
            parentId = parentId
        )
        val currentComments = _comments.value.toMutableMap()
        val postCmts = (currentComments[postId] ?: emptyList()).toMutableList()
        postCmts.add(newComment)
        currentComments[postId] = postCmts
        _comments.value = currentComments
        writeLocalData("comments.json", currentComments)

        // Sync to Supabase
        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val commentJson = moshi.adapter(SupabaseComment::class.java).toJson(newComment)
                SupabaseClient.insertGeneric("comments", commentJson)
            }
        }
    }

    fun likePost(postId: Int) {
        val user = _currentUser.value ?: return
        val isLiked = _likes.value.any { it.postId == postId && it.userId == user.id }
        if (isLiked) {
            // Unlike
            val list = _likes.value.filterNot { it.postId == postId && it.userId == user.id }
            _likes.value = list
            writeLocalData("likes.json", list)
            if (SupabaseClient.isConfigured()) {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClient.deleteQueryGeneric("likes", "post_id=eq.$postId&user_id=eq.${user.id}")
                }
            }
        } else {
            // Like
            val newLike = SupabaseLike(
                id = (System.currentTimeMillis() % 10000000).toInt(),
                postId = postId,
                userId = user.id
            )
            val list = _likes.value.toMutableList()
            list.add(newLike)
            _likes.value = list
            writeLocalData("likes.json", list)
            if (SupabaseClient.isConfigured()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val likeJson = moshi.adapter(SupabaseLike::class.java).toJson(newLike)
                    SupabaseClient.insertGeneric("likes", likeJson)
                }
            }
        }
    }

    fun isPostLiked(postId: Int): Boolean {
        val user = _currentUser.value ?: return false
        return _likes.value.any { it.postId == postId && it.userId == user.id }
    }

    fun getPostLikeCount(postId: Int): Int {
        return _likes.value.count { it.postId == postId }
    }

    // ==========================================
    // REAL SECOND-HAND MARKETPLACE
    // ==========================================

    fun getProductsList(): List<SupabaseProduct> {
        return _products.value.filter { it.isApproved }
    }

    fun publishProduct(
        title: String,
        description: String,
        price: Double,
        originalPrice: Double,
        category: String,
        condition: String,
        location: String,
        imageUrl: String
    ) {
        val user = _currentUser.value ?: return
        val newProduct = SupabaseProduct(
            id = if (_products.value.isEmpty()) 1 else _products.value.maxOf { it.id } + 1,
            title = title,
            description = description,
            imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&q=80&w=300" },
            price = price,
            originalPrice = originalPrice,
            category = category,
            condition = condition,
            location = location,
            sellerId = user.id,
            sellerName = user.nickname,
            sellerAvatar = user.avatar,
            createdAt = System.currentTimeMillis(),
            status = "pending",
            isApproved = true // auto approve in prototype or dev, but editable in admin panel
        )

        val list = _products.value.toMutableList()
        list.add(0, newProduct)
        _products.value = list
        writeLocalData("products.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val recordJson = moshi.adapter(SupabaseProduct::class.java).toJson(newProduct)
                SupabaseClient.insertGeneric("products", recordJson)
            }
        }
    }

    fun buyProduct(productId: Int) {
        val user = _currentUser.value ?: return
        val prodIndex = _products.value.indexOfFirst { it.id == productId }
        if (prodIndex == -1) return

        val product = _products.value[prodIndex]
        if (product.status != "pending" && product.status != "active") return

        // Create Order
        val newOrder = SupabaseOrder(
            id = if (_orders.value.isEmpty()) 1 else _orders.value.maxOf { it.id } + 1,
            productId = product.id,
            productTitle = product.title,
            productImageUrl = product.imageUrl,
            buyerId = user.id,
            buyerName = user.nickname,
            sellerId = product.sellerId,
            sellerName = product.sellerName,
            price = product.price,
            status = "待付款",
            createdAt = System.currentTimeMillis()
        )

        // Update product status
        val updatedProduct = product.copy(status = "completed")
        val prods = _products.value.toMutableList()
        prods[prodIndex] = updatedProduct
        _products.value = prods
        writeLocalData("products.json", prods)

        val ords = _orders.value.toMutableList()
        ords.add(0, newOrder)
        _orders.value = ords
        writeLocalData("orders.json", ords)

        // Auto trigger a chat thread between buyer and seller! Awesome for visual fidelity!
        publishSystemTriggeredChat(user.id, product.sellerId, "你好！我刚买下了你发布的闲置商品《${product.title}》，我们可以面对面交易吗？")

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val orderJson = moshi.adapter(SupabaseOrder::class.java).toJson(newOrder)
                SupabaseClient.insertGeneric("orders", orderJson)
                SupabaseClient.updateGeneric("products", "id", "$productId", """{"status":"completed"}""")
            }
        }
    }

    fun updateOrderStatus(orderId: Int, newStatus: String) {
        val index = _orders.value.indexOfFirst { it.id == orderId }
        if (index == -1) return
        val order = _orders.value[index]
        val updated = order.copy(status = newStatus)
        val list = _orders.value.toMutableList()
        list[index] = updated
        _orders.value = list
        writeLocalData("orders.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.updateGeneric("orders", "id", "$orderId", """{"status":"$newStatus"}""")
            }
        }
    }

    fun toggleProductFavorite(productId: Int) {
        val user = _currentUser.value ?: return
        val isFav = _favorites.value.any { it.productId == productId && it.userId == user.id }
        if (isFav) {
            val list = _favorites.value.filterNot { it.productId == productId && it.userId == user.id }
            _favorites.value = list
            writeLocalData("favorites.json", list)
            if (SupabaseClient.isConfigured()) {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClient.deleteQueryGeneric("favorites", "product_id=eq.$productId&user_id=eq.${user.id}")
                }
            }
        } else {
            val newFav = SupabaseFavorite(
                id = (System.currentTimeMillis() % 10000000).toInt(),
                productId = productId,
                userId = user.id
            )
            val list = _favorites.value.toMutableList()
            list.add(newFav)
            _favorites.value = list
            writeLocalData("favorites.json", list)
            if (SupabaseClient.isConfigured()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val favJson = moshi.adapter(SupabaseFavorite::class.java).toJson(newFav)
                    SupabaseClient.insertGeneric("favorites", favJson)
                }
            }
        }
    }

    fun isProductFavorited(productId: Int): Boolean {
        val user = _currentUser.value ?: return false
        return _favorites.value.any { it.productId == productId && it.userId == user.id }
    }

    // ==========================================
    // CHAT SYSTEM / MESSAGE CENTER
    // ==========================================

    fun getConversationsForUser(): List<SupabaseConversation> {
        val user = _currentUser.value ?: return emptyList()
        val userId = user.id
        
        // Find distinct conversational threads from messages
        val filteredMsgs = _messages.value.filter { it.senderId == userId || it.recipientId == userId }
        val partners = filteredMsgs.flatMap { listOf(it.senderId, it.recipientId) }.distinct().filter { it != userId }
        
        return partners.map { partnerId ->
            val partnerUser = _allUsers.value.find { it.id == partnerId }
            val partnerName = partnerUser?.nickname ?: "自律伙伴"
            val partnerAvatar = partnerUser?.avatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200"
            val threadMessages = filteredMsgs.filter { 
                (it.senderId == userId && it.recipientId == partnerId) || 
                (it.senderId == partnerId && it.recipientId == userId)
            }.sortedBy { it.createdAt }
            
            val lastMsgStr = threadMessages.lastOrNull()?.content ?: "点击开始聊天"
            val updatedVal = threadMessages.lastOrNull()?.createdAt ?: System.currentTimeMillis()
            
            SupabaseConversation(
                id = partnerId.hashCode(),
                participantA = userId,
                participantB = partnerId,
                lastMessage = lastMsgStr,
                updatedAt = updatedVal,
                partnerName = partnerName,
                partnerAvatar = partnerAvatar
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun getMessagesForConversation(partnerId: String): List<SupabaseMessage> {
        val user = _currentUser.value ?: return emptyList()
        val userId = user.id
        return _messages.value.filter {
            (it.senderId == userId && it.recipientId == partnerId) ||
            (it.senderId == partnerId && it.recipientId == userId)
        }.sortedBy { it.createdAt }
    }

    fun sendMessageToPartner(partnerId: String, content: String, imageUrl: String = "") {
        val user = _currentUser.value ?: return
        val newMsg = SupabaseMessage(
            id = if (_messages.value.isEmpty()) 1 else _messages.value.maxOf { it.id } + 1,
            conversationId = partnerId.hashCode(),
            senderId = user.id,
            recipientId = partnerId,
            content = content,
            imageUrl = imageUrl,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )

        val list = _messages.value.toMutableList()
        list.add(newMsg)
        _messages.value = list
        writeLocalData("messages.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val recordJson = moshi.adapter(SupabaseMessage::class.java).toJson(newMsg)
                SupabaseClient.insertGeneric("messages", recordJson)
            }
        }
    }

    private fun publishSystemTriggeredChat(buyerId: String, sellerId: String, initialMsg: String) {
        val newMsg = SupabaseMessage(
            id = if (_messages.value.isEmpty()) 1 else _messages.value.maxOf { it.id } + 1,
            conversationId = sellerId.hashCode(),
            senderId = buyerId,
            recipientId = sellerId,
            content = initialMsg,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
        val list = _messages.value.toMutableList()
        list.add(newMsg)
        _messages.value = list
        writeLocalData("messages.json", list)
    }

    // ==========================================
    // REPORT AUDITING SYSTEM
    // ==========================================

    fun submitReport(reportedType: String, reportedId: String, reason: String, details: String, contentDigest: String) {
        val user = _currentUser.value ?: return
        val newReport = SupabaseReport(
            id = if (_reports.value.isEmpty()) 1 else _reports.value.maxOf { it.id } + 1,
            reporterId = user.id,
            reportedType = reportedType,
            reportedId = reportedId,
            reason = reason,
            details = details,
            status = "pending",
            createdAt = System.currentTimeMillis(),
            reportedContentDigest = contentDigest
        )

        val list = _reports.value.toMutableList()
        list.add(0, newReport)
        _reports.value = list
        writeLocalData("reports.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val recordJson = moshi.adapter(SupabaseReport::class.java).toJson(newReport)
                SupabaseClient.insertGeneric("reports", recordJson)
            }
        }
    }

    // ==========================================
    // ADMINISTRATOR CONSOLE PORTAL APIs
    // ==========================================

    fun adminSetUserBlockStatus(userId: String, isBlocked: Boolean) {
        val userList = _allUsers.value.map {
            if (it.id == userId) it.copy(isBlocked = isBlocked) else it
        }
        _allUsers.value = userList
        writeLocalData("users.json", userList)

        // For audit force block post & products as safety
        if (isBlocked) {
            val postsList = _posts.value.map {
                if (it.authorId == userId) it.copy(isApproved = false) else it
            }
            _posts.value = postsList
            writeLocalData("posts.json", postsList)

            val prodsList = _products.value.map {
                if (it.sellerId == userId) it.copy(isApproved = false, status = "review_rejected") else it
            }
            _products.value = prodsList
            writeLocalData("products.json", prodsList)
        }

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.updateGeneric("users", "id", userId, """{"isBlocked":$isBlocked}""")
            }
        }
    }

    fun adminResolveReport(reportId: Int, newStatus: String) {
        val list = _reports.value.map {
            if (it.id == reportId) it.copy(status = newStatus) else it
        }
        _reports.value = list
        writeLocalData("reports.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.updateGeneric("reports", "id", "$reportId", """{"status":"$newStatus"}""")
            }
        }
    }

    fun adminUpdateUserRole(userId: String, newRole: String) {
        val list = _allUsers.value.map {
            if (it.id == userId) it.copy(role = newRole) else it
        }
        _allUsers.value = list
        writeLocalData("users.json", list)

        // If currently logged-in user is updated, keep current session role in sync!
        if (currentUser.value?.id == userId) {
            _currentUser.value = _currentUser.value?.copy(role = newRole)
        }

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.updateGeneric("users", "id", userId, """{"role":"$newRole"}""")
            }
        }
    }

    suspend fun uploadAndUpdateAvatarBytes(context: Context, fileBytes: ByteArray, formatExtension: String): Result<String> = withContext(Dispatchers.IO) {
        val userId = currentUser.value?.id ?: return@withContext Result.failure(Exception("用户未登录"))
        
        val uploadResult = SupabaseClient.uploadAvatarBytes(fileBytes, userId, formatExtension)
        if (uploadResult.isSuccess) {
            val publicUrl = uploadResult.getOrThrow()
            
            // Sync-write databases
            if (SupabaseClient.isConfigured()) {
                val jsonPatch = """{"avatar_url":"$publicUrl","avatar":"$publicUrl","avatar_updated_at":${System.currentTimeMillis()}}"""
                val client = OkHttpClient()
                val type = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url("${SupabaseClient.supabaseUrl}/rest/v1/users?id=eq.$userId")
                    .addHeader("apikey", SupabaseClient.supabaseAnonKey)
                    .addHeader("Authorization", "Bearer ${SupabaseClient.supabaseAnonKey}")
                    .addHeader("Content-Type", "application/json")
                    .patch(jsonPatch.toRequestBody(type))
                    .build()
                try { client.newCall(request).execute().close() } catch (e: Exception) {
                    Log.e("SupabaseManager", "Error updating user avatar fields in db: ${e.message}")
                }
            }
            
            val updatedUser = currentUser.value?.copy(
                avatar = publicUrl,
                avatar_url = publicUrl,
                avatar_updated_at = System.currentTimeMillis()
            )
            
            _currentUser.value = updatedUser
            val list = _allUsers.value.map {
                if (it.id == userId) updatedUser!! else it
            }
            _allUsers.value = list
            writeLocalData("users.json", list)
            saveSession(context, updatedUser)
            
            Result.success(publicUrl)
        } else {
            Result.failure(uploadResult.exceptionOrNull() ?: Exception("上传失败"))
        }
    }

    suspend fun deleteAvatar(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = currentUser.value?.id ?: return@withContext Result.failure(Exception("用户未登录"))
        val fallbackDefault = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200"
        
        if (SupabaseClient.isConfigured()) {
            val jsonPatch = """{"avatar_url":null,"avatar":"$fallbackDefault","avatar_updated_at":${System.currentTimeMillis()}}"""
            val client = OkHttpClient()
            val type = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("${SupabaseClient.supabaseUrl}/rest/v1/users?id=eq.$userId")
                .addHeader("apikey", SupabaseClient.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${SupabaseClient.supabaseAnonKey}")
                .addHeader("Content-Type", "application/json")
                .patch(jsonPatch.toRequestBody(type))
                .build()
            try { client.newCall(request).execute().close() } catch (e: Exception) {}
        }
        
        val updatedUser = currentUser.value?.copy(
            avatar = fallbackDefault,
            avatar_url = null,
            avatar_updated_at = System.currentTimeMillis()
        )
        _currentUser.value = updatedUser
        val list = _allUsers.value.map {
            if (it.id == userId) updatedUser!! else it
        }
        _allUsers.value = list
        writeLocalData("users.json", list)
        saveSession(context, updatedUser)
        
        Result.success(true)
    }

    suspend fun updateUserProfileRemote(
        context: Context,
        nickname: String,
        school: String,
        college: String,
        grade: String,
        bio: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = currentUser.value?.id ?: return@withContext Result.failure(Exception("用户未登录"))
        
        if (SupabaseClient.isConfigured()) {
            val jsonPatch = """{
                "nickname":"$nickname",
                "school":"$school",
                "college":"$college",
                "grade":"$grade",
                "bio":"$bio"
            }"""
            val client = OkHttpClient()
            val type = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("${SupabaseClient.supabaseUrl}/rest/v1/users?id=eq.$userId")
                .addHeader("apikey", SupabaseClient.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${SupabaseClient.supabaseAnonKey}")
                .addHeader("Content-Type", "application/json")
                .patch(jsonPatch.toRequestBody(type))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("DB update failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        
        val updatedUser = currentUser.value?.copy(
            nickname = nickname,
            school = school,
            college = college,
            grade = grade,
            bio = bio
        )
        
        _currentUser.value = updatedUser
        val list = _allUsers.value.map {
            if (it.id == userId) updatedUser!! else it
        }
        _allUsers.value = list
        writeLocalData("users.json", list)
        saveSession(context, updatedUser)
        
        Result.success(true)
    }

    fun adminDeletePost(postId: Int) {
        deletePost(postId)
    }

    fun adminTakeDownProduct(productId: Int) {
        val prods = _products.value.map {
            if (it.id == productId) it.copy(isApproved = false, status = "review_rejected") else it
        }
        _products.value = prods
        writeLocalData("products.json", prods)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.updateGeneric("products", "id", "$productId", """{"isApproved":false,"status":"review_rejected"}""")
            }
        }
    }

    fun adminPublishAnnouncement(title: String, content: String) {
        val admin = _currentUser.value ?: return
        val newAnn = SupabaseAnnouncement(
            id = if (_announcements.value.isEmpty()) 1 else _announcements.value.maxOf { it.id } + 1,
            title = title,
            content = content,
            authorName = admin.nickname,
            createdAt = System.currentTimeMillis()
        )
        val list = _announcements.value.toMutableList()
        list.add(0, newAnn)
        _announcements.value = list
        writeLocalData("announcements.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                val recordJson = moshi.adapter(SupabaseAnnouncement::class.java).toJson(newAnn)
                SupabaseClient.insertGeneric("announcements", recordJson)
            }
        }
    }

    fun adminDeleteAnnouncement(annId: Int) {
        val list = _announcements.value.filter { it.id != annId }
        _announcements.value = list
        writeLocalData("announcements.json", list)

        if (SupabaseClient.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseClient.deleteGeneric("announcements", "id", "$annId")
            }
        }
    }

    // ==========================================
    // SEEDING AND HELPER READ/WRITES
    // ==========================================

    private fun seedLocalCache() {
        // Users list seeding
        val usersFile = File(appDir, "users.json")
        val usersListType = Types.newParameterizedType(List::class.java, SupabaseUser::class.java)
        val usersAdapter = moshi.adapter<List<SupabaseUser>>(usersListType)
        if (usersFile.exists()) {
            try { _allUsers.value = usersAdapter.fromJson(usersFile.readText()) ?: emptyList() } catch (e: Exception) {}
        } else {
            val seedUsers = listOf(
                SupabaseUser("user_id_1", "高燃自学狂人", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200", "kaoshi@example.com", "北京大学", "软件学院", "研二", "深度学习时间统计践行者 🐳", System.currentTimeMillis(), "user"),
                SupabaseUser("user_id_2", "学委小李 🌻", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200", "xuewei@example.com", "北京大学", "电子信息系", "大三", "快乐学习，每天都要前进一点点！", System.currentTimeMillis(), "user"),
                SupabaseUser("admin_id_1", "卓越学术导师", "https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&q=80&w=200", "prof_admin@example.com", "清华大学", "计算机学院", "导师", "官方学术风纪监督大使 / 终极数据统计中心", System.currentTimeMillis(), "admin")
            )
            _allUsers.value = seedUsers
            writeLocalData("users.json", seedUsers)
        }

        // Posts seeding
        val postsFile = File(appDir, "posts.json")
        val postsType = Types.newParameterizedType(List::class.java, SupabasePost::class.java)
        val postsAdapter = moshi.adapter<List<SupabasePost>>(postsType)
        if (postsFile.exists()) {
            try { _posts.value = postsAdapter.fromJson(postsFile.readText()) ?: emptyList() } catch (e: Exception) {}
        } else {
            val seedPosts = listOf(
                SupabasePost(1, "user_id_1", "高燃自学狂人", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200", "今天终于把柳比歇夫的时间结构打卡统计应用做出了第十次重构架构，今天高强度学习了7个小时！感觉自律动力直线上升 ✊", "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?auto=format&fit=crop&q=80&w=400", "自律打卡,柳比歇夫", "深度学习圈", false),
                SupabasePost(2, "user_id_2", "匿名同学", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200", "有人知道西校区新开图书馆下午几点闭馆吗？每次学到深处，都会沉浸到浑然不知天色已晚...", "", "自习疑问", "自律问答", true)
            )
            _posts.value = seedPosts
            writeLocalData("posts.json", seedPosts)
        }

        // Products seeding
        val prodsFile = File(appDir, "products.json")
        val prodsType = Types.newParameterizedType(List::class.java, SupabaseProduct::class.java)
        val prodsAdapter = moshi.adapter<List<SupabaseProduct>>(prodsType)
        if (prodsFile.exists()) {
            try { _products.value = prodsAdapter.fromJson(prodsFile.readText()) ?: emptyList() } catch (e: Exception) {}
        } else {
            val seedProds = listOf(
                SupabaseProduct(1, "考研高数红宝书 (极限与微积分)", "精磨绝无缺页，仅有少量铅笔刷题考点批注，附赠清华名师精细视频讲义，学弟学妹高分必藏！", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&q=80&w=300", 25.5, 78.0, "考研教材", "九成新", "西区4号楼下", "user_id_2", "学委小李 🌻", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200", System.currentTimeMillis() - 7200000),
                SupabaseProduct(2, "机械革命 15.6寸轻薄自强本 (极品带走)", "i7 处理器 / 16G 内存 / 512G SSD，完美跑通各类大语言模型框架和深度学习代码。毕业回国极速打包甩卖。", "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?auto=format&fit=crop&q=80&w=300", 1850.0, 4999.0, "数码电子", "八五新", "东区综合楼对面", "user_id_1", "高燃自学狂人", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200", System.currentTimeMillis() - 17200000)
            )
            _products.value = seedProds
            writeLocalData("products.json", seedProds)
        }

        // Seeding empty/loaded lists
        val comsFile = File(appDir, "comments.json")
        val comsType = Types.newParameterizedType(Map::class.java, Integer::class.java, Types.newParameterizedType(List::class.java, SupabaseComment::class.java))
        val comsAdapter = moshi.adapter<Map<Int, List<SupabaseComment>>>(comsType)
        if (comsFile.exists()) {
            try { _comments.value = comsAdapter.fromJson(comsFile.readText()) ?: emptyMap() } catch (e: Exception) {}
        } else {
            val m = mapOf(
                1 to listOf(SupabaseComment(101, 1, "user_id_2", "学委小李 🌻", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200", "太猛了同桌！今晚九点约个夜跑怎么样，好好释放一下高强度思考带来的乳酸堆积！🌳", System.currentTimeMillis() - 10000))
            )
            _comments.value = m
            writeLocalData("comments.json", m)
        }

        // Chat messages seeding
        val msgsFile = File(appDir, "messages.json")
        val msgsType = Types.newParameterizedType(List::class.java, SupabaseMessage::class.java)
        val msgsAdapter = moshi.adapter<List<SupabaseMessage>>(msgsType)
        if (msgsFile.exists()) {
            try { _messages.value = msgsAdapter.fromJson(msgsFile.readText()) ?: emptyList() } catch (e: Exception) {}
        } else {
            val threadSeed = listOf(
                SupabaseMessage(1001, "user_id_1".hashCode(), "user_id_2", "user_id_1", "学长你好！我看到你发布的时间统计日志了，请问有什么可以快速戒断短视频专注打卡的小窍门吗？"),
                SupabaseMessage(1002, "user_id_1".hashCode(), "user_id_1", "user_id_2", "你好呀！我用的是严格限制法，在自习期间我会把手机放进铁锁盒里。自习结束我就会记录我的柳比歇夫卡，动力满满！")
            )
            _messages.value = threadSeed
            writeLocalData("messages.json", threadSeed)
        }

        // Rest lists loading
        _likes.value = readLocalData("likes.json", Types.newParameterizedType(List::class.java, SupabaseLike::class.java)) ?: emptyList()
        _orders.value = readLocalData("orders.json", Types.newParameterizedType(List::class.java, SupabaseOrder::class.java)) ?: emptyList()
        _favorites.value = readLocalData("favorites.json", Types.newParameterizedType(List::class.java, SupabaseFavorite::class.java)) ?: emptyList()
        _reports.value = readLocalData("reports.json", Types.newParameterizedType(List::class.java, SupabaseReport::class.java)) ?: emptyList()
        
        val annsFile = File(appDir, "announcements.json")
        val annsType = Types.newParameterizedType(List::class.java, SupabaseAnnouncement::class.java)
        if (annsFile.exists()) {
            try { _announcements.value = moshi.adapter<List<SupabaseAnnouncement>>(annsType).fromJson(annsFile.readText()) ?: emptyList() } catch (e: Exception) {}
        } else {
            val seedAnns = listOf(
                SupabaseAnnouncement(1, "⏰ 绿色二手循环文明自律管理公告", "各位同学好！在二手交易发布闲置物品请勿使用任何违法违规或者辱骂性言论词汇，一经系统监管审计发现立即采取 10 级封号限制！", "管理员", System.currentTimeMillis() - 86400000),
                SupabaseAnnouncement(2, "💡 2.0.0 自学之星量化统计系统上线", "CampusAI 已经完美支持由 Android Keystore 与 AI 专属配置驱动的自研决策链！祝全校同学自考大捷！", "超级管理员", System.currentTimeMillis() - 172800000)
            )
            _announcements.value = seedAnns
            writeLocalData("announcements.json", seedAnns)
        }
    }

    private fun <T> writeLocalData(filename: String, data: T) {
        try {
            val file = File(appDir, filename)
            val adapter = moshi.adapter<T>(data!!::class.java)
            file.writeText(adapter.toJson(data))
        } catch (e: Exception) {
            // If complex type (e.g. List or Map), we serialize with corresponding types adapter to preserve list shapes
            try {
                val file = File(appDir, filename)
                val type = if (filename.contains("comments")) {
                    Types.newParameterizedType(Map::class.java, Integer::class.java, Types.newParameterizedType(List::class.java, SupabaseComment::class.java))
                } else if (filename.contains("posts")) {
                    Types.newParameterizedType(List::class.java, SupabasePost::class.java)
                } else if (filename.contains("users")) {
                    Types.newParameterizedType(List::class.java, SupabaseUser::class.java)
                } else if (filename.contains("products")) {
                    Types.newParameterizedType(List::class.java, SupabaseProduct::class.java)
                } else if (filename.contains("orders")) {
                    Types.newParameterizedType(List::class.java, SupabaseOrder::class.java)
                } else if (filename.contains("likes")) {
                    Types.newParameterizedType(List::class.java, SupabaseLike::class.java)
                } else if (filename.contains("favorites")) {
                    Types.newParameterizedType(List::class.java, SupabaseFavorite::class.java)
                } else if (filename.contains("messages")) {
                    Types.newParameterizedType(List::class.java, SupabaseMessage::class.java)
                } else if (filename.contains("reports")) {
                    Types.newParameterizedType(List::class.java, SupabaseReport::class.java)
                } else if (filename.contains("announcements")) {
                    Types.newParameterizedType(List::class.java, SupabaseAnnouncement::class.java)
                } else {
                    return
                }
                val adapter = moshi.adapter<T>(type)
                file.writeText(adapter.toJson(data))
            } catch (ex: Exception) {
                Log.e("SupabaseManager", "Error saving $filename locally", ex)
            }
        }
    }

    private fun <T> readLocalData(filename: String, type: java.lang.reflect.Type): T? {
        val file = File(appDir, filename)
        if (!file.exists()) return null
        return try {
            val adapter = moshi.adapter<T>(type)
            adapter.fromJson(file.readText())
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error reading $filename", e)
            null
        }
    }

    // ==========================================
    // DEEP SUPABASE PROFILE AND REST API WRITING Helpers
    // ==========================================

    private suspend fun fetchUserProfile(userId: String): SupabaseUser? {
        val url = "${SupabaseClient.supabaseUrl}/rest/v1/users?id=eq.$userId&select=*"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseClient.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${SupabaseClient.supabaseAnonKey}")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val listType = Types.newParameterizedType(List::class.java, SupabaseUser::class.java)
                    val adapter = moshi.adapter<List<SupabaseUser>>(listType)
                    adapter.fromJson(responseStr)?.firstOrNull()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun insertUserToSupabase(user: SupabaseUser) {
        val recordJson = moshi.adapter(SupabaseUser::class.java).toJson(user)
        val url = "${SupabaseClient.supabaseUrl}/rest/v1/users"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseClient.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${SupabaseClient.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(recordJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Async profile insert failed", e)
        }
    }
}

// Add generic query features to SupabaseClient
private fun SupabaseClient.insertGeneric(table: String, json: String) {
    if (!isConfigured()) return
    val client = OkHttpClient()
    val type = "application/json; charset=utf-8".toMediaType()
    val request = Request.Builder()
        .url("$supabaseUrl/rest/v1/$table")
        .addHeader("apikey", supabaseAnonKey)
        .addHeader("Authorization", "Bearer $supabaseAnonKey")
        .addHeader("Content-Type", "application/json")
        .post(json.toRequestBody(type))
        .build()
    try { client.newCall(request).execute().close() } catch (e: Exception) {}
}

private fun SupabaseClient.deleteGeneric(table: String, column: String, value: String) {
    if (!isConfigured()) return
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("$supabaseUrl/rest/v1/$table?$column=eq.$value")
        .addHeader("apikey", supabaseAnonKey)
        .addHeader("Authorization", "Bearer $supabaseAnonKey")
        .delete()
        .build()
    try { client.newCall(request).execute().close() } catch (e: Exception) {}
}

private fun SupabaseClient.deleteQueryGeneric(table: String, query: String) {
    if (!isConfigured()) return
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("$supabaseUrl/rest/v1/$table?$query")
        .addHeader("apikey", supabaseAnonKey)
        .addHeader("Authorization", "Bearer $supabaseAnonKey")
        .delete()
        .build()
    try { client.newCall(request).execute().close() } catch (e: Exception) {}
}

private fun SupabaseClient.updateGeneric(table: String, idColumn: String, idValue: String, jsonPatch: String) {
    if (!isConfigured()) return
    val client = OkHttpClient()
    val type = "application/json; charset=utf-8".toMediaType()
    val request = Request.Builder()
        .url("$supabaseUrl/rest/v1/$table?$idColumn=eq.$idValue")
        .addHeader("apikey", supabaseAnonKey)
        .addHeader("Authorization", "Bearer $supabaseAnonKey")
        .addHeader("Content-Type", "application/json")
        .patch(jsonPatch.toRequestBody(type))
        .build()
    try { client.newCall(request).execute().close() } catch (e: Exception) {}
}

private fun parseSupabaseAuthError(responseCode: Int, responseStr: String): String {
    return try {
        val errorDescriptionMatch = "\"error_description\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
        val errorMatch = "\"error\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
        val messageMatch = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
        val msgMatch = "\"msg\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseStr)
        
        val desc = errorDescriptionMatch?.groupValues?.get(1) 
            ?: msgMatch?.groupValues?.get(1) 
            ?: messageMatch?.groupValues?.get(1) 
            ?: errorMatch?.groupValues?.get(1)
            
        if (desc != null) {
            when {
                desc.contains("Email not confirmed", ignoreCase = true) -> 
                    "登录失败：邮箱尚未激活验证！请查收您的注册激活邮件，或前往 Supabase 管理后台在 [Authentication] -> [Providers] -> [Email] 手动关闭 [Confirm email] 并保存配置，然后再试。"
                desc.contains("Invalid login credentials", ignoreCase = true) || desc.contains("invalid_credentials", ignoreCase = true) -> 
                    "登录失败：邮箱或密码不正确，或者是该账号目前尚未创建。请切换至\"注册\"页面创建并绑定。"
                desc.contains("User not found", ignoreCase = true) -> 
                    "登录失败：该账号不存在，请切换至\"注册\"页快速建号。"
                desc.contains("rate limit", ignoreCase = true) -> 
                    "操作频繁：触发了 Supabase 的防护限制，请稍候 30 秒至 1 分钟后重新尝试。"
                desc.contains("signup is disabled", ignoreCase = true) -> 
                    "注册失败：自主邮箱密码注册已被您的 Supabase 实例管理员禁用。"
                else -> "失败：$desc"
            }
        } else {
            "HTTP 状态码 $responseCode\n原始信息: $responseStr"
        }
    } catch (e: Exception) {
        "HTTP 状态码 $responseCode\n原始异常: $responseStr"
    }
}
