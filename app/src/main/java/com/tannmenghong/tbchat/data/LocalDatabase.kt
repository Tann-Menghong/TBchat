package com.tannmenghong.tbchat.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(@PrimaryKey val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "messages", foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)], indices = [Index("conversationId")])
data class MessageEntity(@PrimaryKey val id: String, val conversationId: String, val role: String, val body: String, val createdAt: Long)

@Entity(tableName = "images")
data class ImageEntity(@PrimaryKey val id: String, val prompt: String, val negativePrompt: String, val seed: Long, val localPath: String?, val createdAt: Long)

@Dao interface LocalDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun conversations(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt") fun messages(id: String): Flow<List<MessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putConversation(item: ConversationEntity)
    @Insert suspend fun putMessage(item: MessageEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putImage(item: ImageEntity)
    @Query("DELETE FROM conversations") suspend fun clearConversations()
    @Query("DELETE FROM images") suspend fun clearImages()
}

@Database(entities = [ConversationEntity::class, MessageEntity::class, ImageEntity::class], version = 1)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao
    companion object { fun open(context: Context) = Room.databaseBuilder(context, LocalDatabase::class.java, "tbchat.db").build() }
}
