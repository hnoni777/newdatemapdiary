package io.github.hnoni777.newdatemapdiary

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ProfileStickerManager {

    private const val PROFILE_DIR = "profile_stickers"
    private const val PREF_SELECTED_PROFILE = "selected_profile_filename"
    private const val PREFS_NAME = "ProfilePrefs"
    private const val BACKUP_FILENAME = "profile_backup_do_not_delete.png" 

    /**
     * 📸 [전수 백업] 스티커가 생성될 때마다 개별 파일명으로 갤러리에 즉각 백업합니다.
     */
    fun saveProfileSticker(context: Context, bitmap: Bitmap): String? {
        val dir = File(context.filesDir, PROFILE_DIR)
        if (!dir.exists()) dir.mkdirs()

        val filename = "profile_${System.currentTimeMillis()}.png"
        val tempFile = File(dir, "$filename.tmp")
        val finalFile = File(dir, filename)

        return try {
            FileOutputStream(tempFile).use { fos ->
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    fos.flush()
                } else {
                    throw Exception("Compression failed")
                }
            }
            if (tempFile.renameTo(finalFile)) {
                // 🔥 [즉시 백업] 생성과 동시에 갤러리로 쏜다!
                backupSingleStickerToGallery(context, bitmap, filename)
                filename
            } else {
                null
            }
        } catch (e: Exception) {
            tempFile.delete()
            e.printStackTrace()
            null
        }
    }

    fun getProfileStickers(context: Context): List<File> {
        val dir = File(context.filesDir, PROFILE_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { _, name -> name.startsWith("profile_") && name.endsWith(".png") }
            ?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteProfileSticker(context: Context, filename: String) {
        val file = File(File(context.filesDir, PROFILE_DIR), filename)
        if (file.exists()) file.delete()
        
        // 🗑️ 갤러리 백업본도 함께 삭제 시도 (선택 사항이나 깔끔하게 처리)
        try {
            context.contentResolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(filename))
        } catch (e: Exception) { /* 무시 */ }

        if (getSelectedProfileFilename(context) == filename) {
            setSelectedProfile(context, null)
        }
    }

    fun setSelectedProfile(context: Context, filename: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SELECTED_PROFILE, filename).apply()
    }

    fun getSelectedProfileFilename(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_SELECTED_PROFILE, null)
    }

    fun getSelectedProfileBitmap(context: Context): Bitmap? {
        val filename = getSelectedProfileFilename(context) ?: return null
        return getProfileBitmap(context, filename)
    }

    fun getProfileBitmap(context: Context, filename: String): Bitmap? {
        val file = File(File(context.filesDir, PROFILE_DIR), filename)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            file.delete() // 깨진 파일은 과감히 삭제하여 재복구 유도
            null
        }
    }

    fun getSelectedProfileFile(context: Context): File? {
        val filename = getSelectedProfileFilename(context) ?: return null
        val file = File(File(context.filesDir, PROFILE_DIR), filename)
        return if (file.exists()) file else null
    }

    private const val PREF_MY_NAME = "my_nickname"
    private const val PREF_PARTNER_NAME = "partner_nickname"

    fun setMyName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_MY_NAME, name).apply()
    }

    fun getMyName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_MY_NAME, "나") ?: "나"
    }

    fun setPartnerName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_PARTNER_NAME, name).apply()
    }

    fun getPartnerName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_PARTNER_NAME, "연인") ?: "연인"
    }
    private fun backupSingleStickerToGallery(context: Context, bitmap: Bitmap, filename: String) {
        // 🔥 [최적화] 갤러리 I/O 및 스캔 작업을 백그라운드로 던짐
        kotlin.concurrent.thread {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/HereWithYou")
                }
                
                // 기존에 동일한 이름이 있으면 삭제 (덮어쓰기 효과)
                resolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(filename))

                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    
                    // ⚡ [핵심] 갤러리 앱이 즉시 파일을 인식하도록 강제 스캐닝 명령!
                    val path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).absolutePath + "/HereWithYou/" + filename
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { p, u ->
                        Log.d("PROFIL_BACKUP", "Media scanned: $p")
                    }
                }
            } catch (e: Exception) {
                Log.e("PROFIL_BACKUP", "Failed to backup $filename", e)
            }
        }
    }

    /**
     * 🛡️ [불사조 프로필 전수 복구]
     * 개량형: 원자적 복구 및 무결성 검증을 통해 반쪽짜리 이미지 방지
     */
    fun restoreProfileFromGallery(context: Context, onComplete: (() -> Unit)? = null) {
        kotlin.concurrent.thread {
            try {
                val resolver = context.contentResolver
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME
                )
                val selection = "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("profile_%", "%HereWithYou%")
                
                val dir = File(context.filesDir, PROFILE_DIR)
                if (!dir.exists()) dir.mkdirs()

                resolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val filename = cursor.getString(nameCol)
                        val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        
                        val targetFile = File(dir, filename)
                        
                        // ✨ [개량] 이미 파일이 있다면 이미지 무결성 검사
                        if (targetFile.exists()) {
                            val testBitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
                            if (testBitmap != null) {
                                // 정상적인 이미지면 패스
                                continue
                            } else {
                                // 깨진 이미지면 삭제하고 다시 받기
                                targetFile.delete()
                            }
                        }

                        val tempFile = File(dir, "$filename.restore.tmp")
                        resolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                FileOutputStream(tempFile).use { out ->
                                    if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                        out.flush()
                                    }
                                }
                                tempFile.renameTo(targetFile)
                            }
                        }
                        tempFile.delete() // 혹시 남아있을 임시파일 청소
                    }
                }
                Log.d("PROFIL_RESTORE", "Atomic restoration with integrity check completed.")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e("PROFIL_RESTORE", "Restoration failed", e)
            }
        }
    }
}
