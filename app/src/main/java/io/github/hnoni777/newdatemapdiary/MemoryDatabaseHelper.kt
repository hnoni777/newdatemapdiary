package io.github.hnoni777.newdatemapdiary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Memory(
    val id: Long = 0,
    val photoUri: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val date: Long,
    val rating: Int = 0,
    val profileSticker: String? = null // ✨ 신규: 저장 당시 사용된 프로필 스티커 파일명
)

class MemoryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "memories.db"
        const val DATABASE_VERSION = 3 // ✨ DB 버전 업 (프로필 스티커 필드 추가)
        const val TABLE_MEMORIES = "memories"
        
        const val COLUMN_ID = "id"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_LAT = "lat"
        const val COLUMN_LNG = "lng"
        const val COLUMN_DATE = "date"
        const val COLUMN_RATING = "rating"
        const val COLUMN_PROFILE_STICKER = "profile_sticker"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_MEMORIES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PHOTO_URI TEXT,
                $COLUMN_ADDRESS TEXT,
                $COLUMN_LAT REAL,
                $COLUMN_LNG REAL,
                $COLUMN_DATE INTEGER,
                $COLUMN_RATING INTEGER DEFAULT 0,
                $COLUMN_PROFILE_STICKER TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_MEMORIES ADD COLUMN $COLUMN_RATING INTEGER DEFAULT 0")
        }
        if (oldVersion < 3) {
            // 버전 2 -> 3 업그레이드 시 profile_sticker 컬럼 추가
            db.execSQL("ALTER TABLE $TABLE_MEMORIES ADD COLUMN $COLUMN_PROFILE_STICKER TEXT")
        }
    }

    fun insertMemory(memory: Memory): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_URI, memory.photoUri)
            put(COLUMN_ADDRESS, memory.address)
            put(COLUMN_LAT, memory.lat)
            put(COLUMN_LNG, memory.lng)
            put(COLUMN_DATE, memory.date)
            put(COLUMN_RATING, memory.rating)
            put(COLUMN_PROFILE_STICKER, memory.profileSticker) // 저장 시점의 프로필 정보 기록
        }
        return db.insert(TABLE_MEMORIES, null, values)
    }

    fun getAllMemories(): List<Memory> {
        val memories = mutableListOf<Memory>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_MEMORIES, null, null, null, null, null, "$COLUMN_DATE DESC")
        
        if (cursor.moveToFirst()) {
            do {
                val ratingIndex = cursor.getColumnIndex(COLUMN_RATING)
                val rating = if (ratingIndex != -1) cursor.getInt(ratingIndex) else 0

                val profileIndex = cursor.getColumnIndex(COLUMN_PROFILE_STICKER)
                val profileSticker = if (profileIndex != -1) cursor.getString(profileIndex) else null

                val memory = Memory(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHOTO_URI)),
                    address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)),
                    lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT)),
                    lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LNG)),
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    rating = rating,
                    profileSticker = profileSticker
                )
                memories.add(memory)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return memories
    }

    fun deleteMemory(id: Long): Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_MEMORIES, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    fun deleteMemoryByUri(uriStr: String): Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_MEMORIES, "$COLUMN_PHOTO_URI = ?", arrayOf(uriStr)) > 0
    }

    fun getMemoryByUri(uriStr: String): Memory? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_MEMORIES,
            null,
            "$COLUMN_PHOTO_URI = ?",
            arrayOf(uriStr),
            null,
            null,
            null
        )

        var memory: Memory? = null
        if (cursor.moveToFirst()) {
            val ratingIndex = cursor.getColumnIndex(COLUMN_RATING)
            val rating = if (ratingIndex != -1) cursor.getInt(ratingIndex) else 0
            
            val profileIndex = cursor.getColumnIndex(COLUMN_PROFILE_STICKER)
            val profileSticker = if (profileIndex != -1) cursor.getString(profileIndex) else null

            memory = Memory(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHOTO_URI)),
                address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)),
                lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT)),
                lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LNG)),
                date = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                rating = rating,
                profileSticker = profileSticker
            )
        }
        cursor.close()
        return memory
    }
}
