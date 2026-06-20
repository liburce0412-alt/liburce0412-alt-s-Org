package com.example.core.database

import android.content.Context
import androidx.room.*
import com.example.core.model.TimeRecord
import com.example.core.model.Goods
import com.example.core.model.Friend
import com.example.core.model.ChatMessage
import com.example.core.model.UserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ==========================================
// Room Entities
// ==========================================

@Entity(tableName = "time_records")
data class TimeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Long,
    val remark: String,
    val userId: String
) {
    fun toDomain() = TimeRecord(id, title, category, startTime, endTime, durationMinutes, remark, userId)
    
    companion object {
        fun fromDomain(d: TimeRecord) = TimeRecordEntity(
            id = d.id,
            title = d.title,
            category = d.category,
            startTime = d.startTime,
            endTime = d.endTime,
            durationMinutes = d.durationMinutes,
            remark = d.remark,
            userId = d.userId
        )
    }
}

@Entity(tableName = "goods")
data class GoodsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val imageUrl: String,
    val price: Double,
    val sellerName: String,
    val sellerId: String,
    val createdAt: Long,
    val isFavorite: Boolean
) {
    fun toDomain() = Goods(id, title, description, imageUrl, price, sellerName, sellerId, createdAt, isFavorite)
    
    companion object {
        fun fromDomain(d: Goods) = GoodsEntity(
            id = d.id,
            title = d.title,
            description = d.description,
            imageUrl = d.imageUrl,
            price = d.price,
            sellerName = d.sellerName,
            sellerId = d.sellerId,
            createdAt = d.createdAt,
            isFavorite = d.isFavorite
        )
    }
}

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val avatarUrl: String,
    val bio: String,
    val status: String
) {
    fun toDomain() = Friend(id, nickname, avatarUrl, bio, status)
    
    companion object {
        fun fromDomain(d: Friend) = FriendEntity(
            id = d.id,
            nickname = d.nickname,
            avatarUrl = d.avatarUrl,
            bio = d.bio,
            status = d.status
        )
    }
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean
) {
    fun toDomain() = ChatMessage(id, friendId, senderId, content, timestamp, isRead)
    
    companion object {
        fun fromDomain(d: ChatMessage) = ChatMessageEntity(
            id = d.id,
            friendId = d.friendId,
            senderId = d.senderId,
            content = d.content,
            timestamp = d.timestamp,
            isRead = d.isRead
        )
    }
}

@Entity(tableName = "user_messages")
data class UserMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileUserId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val timestamp: Long,
    val isApproved: Boolean
) {
    fun toDomain() = UserMessage(id, profileUserId, authorName, authorAvatar, content, timestamp, isApproved)
    
    companion object {
        fun fromDomain(d: UserMessage) = UserMessageEntity(
            id = d.id,
            profileUserId = d.profileUserId,
            authorName = d.authorName,
            authorAvatar = d.authorAvatar,
            content = d.content,
            timestamp = d.timestamp,
            isApproved = d.isApproved
        )
    }
}

// ==========================================
// Room DAOs
// ==========================================

@Dao
interface CampusDao {
    // Time Records
    @Query("SELECT * FROM time_records ORDER BY startTime DESC")
    fun getAllTimeRecordsFlow(): Flow<List<TimeRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeRecord(entity: TimeRecordEntity): Long

    @Query("DELETE FROM time_records WHERE id = :id")
    suspend fun deleteTimeRecordById(id: Int)

    // Goods Market
    @Query("SELECT * FROM goods ORDER BY createdAt DESC")
    fun getAllGoodsFlow(): Flow<List<GoodsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoods(entity: GoodsEntity): Long

    @Query("DELETE FROM goods WHERE id = :id")
    suspend fun deleteGoodsById(id: Int)

    @Query("UPDATE goods SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateGoodsFavorite(id: Int, isFavorite: Boolean)

    // Friends List
    @Query("SELECT * FROM friends")
    fun getAllFriendsFlow(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(entity: FriendEntity)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriendById(id: String)

    // Chats Messages
    @Query("SELECT * FROM chat_messages WHERE friendId = :friendId ORDER BY timestamp ASC")
    fun getMessagesForFriendFlow(friendId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(entity: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET isRead = 1 WHERE friendId = :friendId AND senderId = :friendId")
    suspend fun markMessagesAsRead(friendId: String)

    // Message Board Walls
    @Query("SELECT * FROM user_messages WHERE profileUserId = :profileUserId ORDER BY timestamp DESC")
    fun getMessagesForProfileFlow(profileUserId: String): Flow<List<UserMessageEntity>>

    @Query("SELECT * FROM user_messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<UserMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserMessage(entity: UserMessageEntity): Long

    @Query("DELETE FROM user_messages WHERE id = :id")
    suspend fun deleteUserMessageById(id: Int)

    @Query("UPDATE user_messages SET isApproved = :isApproved WHERE id = :id")
    suspend fun updateUserMessageApproval(id: Int, isApproved: Boolean)
}

// ==========================================
// Database Setup
// ==========================================

@Database(
    entities = [
        TimeRecordEntity::class,
        GoodsEntity::class,
        FriendEntity::class,
        ChatMessageEntity::class,
        UserMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun campusDao(): CampusDao

    companion object {
        @Volatile
        private var INSTANCE: CampusDatabase? = null

        fun getDatabase(context: Context): CampusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CampusDatabase::class.java,
                    "campus_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
