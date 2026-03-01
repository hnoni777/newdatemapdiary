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
    val date: Long
)

class MemoryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "memories.db"
        const val DATABASE_VERSION = 1
        const val TABLE_MEMORIES = "memories"
        
        const val COLUMN_ID = "id"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_LAT = "lat"
        const val COLUMN_LNG = "lng"
        const val COLUMN_DATE = "date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_MEMORIES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PHOTO_URI TEXT,
                $COLUMN_ADDRESS TEXT,
                $COLUMN_LAT REAL,
                $COLUMN_LNG REAL,
                $COLUMN_DATE INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORIES")
        onCreate(db)
    }

    fun insertMemory(memory: Memory): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_URI, memory.photoUri)
            put(COLUMN_ADDRESS, memory.address)
            put(COLUMN_LAT, memory.lat)
            put(COLUMN_LNG, memory.lng)
            put(COLUMN_DATE, memory.date)
        }
        return db.insert(TABLE_MEMORIES, null, values)
    }

    fun getAllMemories(): List<Memory> {
        val memories = mutableListOf<Memory>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_MEMORIES, null, null, null, null, null, "$COLUMN_DATE DESC")
        
        if (cursor.moveToFirst()) {
            do {
                val memory = Memory(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHOTO_URI)),
                    address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)),
                    lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT)),
                    lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LNG)),
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE))
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
}
