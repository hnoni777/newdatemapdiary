package io.github.hnoni777.newdatemapdiary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.github.hnoni777.newdatemapdiary.databinding.ActivityGalleryDetailBinding

class GalleryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryDetailBinding
    private lateinit var dbHelper: MemoryDatabaseHelper
    private var pendingDeleteUri: Uri? = null
    private var pendingDeleteUriString: String? = null

    // Android 10+ 시스템 삭제 확인 다이얼로그 결과 처리
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = MemoryDatabaseHelper(this)

        // 🔑 Android 10+: 시스템 삭제 권한 요청 결과 콜백
        deleteRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 사용자가 시스템 삭제 다이얼로그에서 "삭제" 승인
                pendingDeleteUriString?.let { dbHelper.deleteMemoryByUri(it) }
                android.widget.Toast.makeText(this, "추억이 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            } else {
                android.widget.Toast.makeText(this, "삭제가 취소되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val imageUriString = intent.getStringExtra("image_uri")

        if (imageUriString != null) {
            val uri = Uri.parse(imageUriString)

            // 📸 Glide 이미지 로드
            Glide.with(this)
                .load(uri)
                .into(binding.ivDetailImage)

            binding.btnCopyAddress.setOnClickListener {
                copyToClipboard("전설의 사랑 장소 ❤️")
            }

            binding.btnShareImage.setOnClickListener {
                shareImage(uri)
            }

            findViewById<android.widget.ImageButton>(R.id.btn_delete_gallery).setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("추억 삭제")
                    .setMessage("이 추억 카드(사진과 지도 핀)를 정말 지우시겠습니까?\n한 번 삭제하면 되돌릴 수 없습니다.")
                    .setPositiveButton("삭제") { _, _ ->
                        deleteImage(uri, imageUriString)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        binding.btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * 📸 이미지 삭제 메인 로직
     * - Android 10 미만: contentResolver.delete() 직접 호출
     * - Android 10 이상: MediaStore.createDeleteRequest() 시스템 다이얼로그 사용
     *   (재설치 후 불러온 카드, 다른 앱이 만든 파일도 삭제 가능)
     */
    private fun deleteImage(uri: Uri, uriString: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : createDeleteRequest로 시스템 삭제 요청
            try {
                pendingDeleteUri = uri
                pendingDeleteUriString = uriString
                val intentSender = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(uri)
                ).intentSender
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (e: Exception) {
                android.util.Log.e("GalleryDetail", "createDeleteRequest 실패: ${e.message}")
                android.widget.Toast.makeText(this, "삭제 요청 중 오류가 발생했습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 이하: 직접 삭제
            try {
                val deletedRows = contentResolver.delete(uri, null, null)
                dbHelper.deleteMemoryByUri(uriString)
                if (deletedRows > 0) {
                    android.widget.Toast.makeText(this, "추억이 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    android.widget.Toast.makeText(this, "사진 삭제에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("GalleryDetail", "삭제 실패: ${e.message}")
                android.widget.Toast.makeText(this, "사진 삭제 권한이 없거나 오류가 발생했습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("HereWithYou_Location", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, "소중한 장소의 주소가 복사되었습니다! 📋", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareImage(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "HereWithYou 추억 공유하기"))
        } catch (e: Exception) {
            android.util.Log.e("ShareError", "공유 중 에러 발생: ${e.message}")
            android.widget.Toast.makeText(this, "공유를 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
