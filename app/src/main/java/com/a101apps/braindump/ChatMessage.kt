package com.a101apps.braindump

import android.content.Context
import androidx.room.*

@Entity
data class ChatMessage(
    @PrimaryKey val id: String,
    val text: String,
    val senderId: String,
    val timestamp: Long,
    val showDateHeader: Boolean = false
)

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insertMessage(chatMessage: ChatMessage)

    @Update
    suspend fun updateMessage(chatMessage: ChatMessage)

    @Delete
    suspend fun deleteMessage(chatMessage: ChatMessage)

    @Query("SELECT * FROM ChatMessage ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>
}

@Database(entities = [ChatMessage::class], version = 1)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
