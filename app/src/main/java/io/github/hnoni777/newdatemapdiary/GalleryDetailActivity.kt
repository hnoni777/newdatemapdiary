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
import org.json.JSONObject
import androidx.exifinterface.media.ExifInterface
import android.widget.Toast

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
                Toast.makeText(this, "추억이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "삭제가 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        val imageUriString = intent.getStringExtra("image_uri")

        if (imageUriString != null) {
            val uri = Uri.parse(imageUriString)
            
            // 📸 Glide 이미지 로드
            Glide.with(this)
                .load(uri)
                .into(binding.ivDetailImage)

            // 🛰️ DB 또는 Exif에서 위치 정보 가져오기
            val memory = dbHelper.getMemoryByUri(imageUriString.trim())
            var lat = memory?.lat ?: 0.0
            var lng = memory?.lng ?: 0.0

            // DB에 정보가 없을 경우 Exif에서 JSON 메타데이터 파싱 시도 (백업 플랜)
            if (lat == 0.0 || lng == 0.0) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val exif = ExifInterface(input)
                        val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                        if (description != null) {
                            val json = JSONObject(description)
                            lat = json.optDouble("lat", 0.0)
                            lng = json.optDouble("lng", 0.0)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EXIF_PARSE", "Failed to parse location from Exif", e)
                }
            }

            // 🚗 길찾기 버튼
            binding.btnDirections.setOnClickListener {
                if (lat != 0.0 && lng != 0.0) {
                    openKakaoMapDirections(lat, lng, memory?.address ?: "추억의 장소")
                } else {
                    Toast.makeText(this, "위치 정보가 없는 카드입니다.", Toast.LENGTH_SHORT).show()
                }
            }

            // 🏙️ 로드뷰 버튼
            binding.btnRoadview.setOnClickListener {
                if (lat != 0.0 && lng != 0.0) {
                    openKakaoMapRoadView(lat, lng)
                } else {
                    Toast.makeText(this, "위치 정보가 없는 카드입니다.", Toast.LENGTH_SHORT).show()
                }
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

    private fun openKakaoMapDirections(lat: Double, lng: Double, name: String) {
        // 🚗 [친절 가이드] 카카오맵 설치 여부를 미리 확인하여 사용자에게 안내
        if (!isKakaoMapInstalled()) {
            Toast.makeText(this, "카카오맵 앱이 설치되어 있지 않아 설치 페이지로 이동합니다.", Toast.LENGTH_LONG).show()
        }

        // 프리미엄 경험을 위해 전용 길찾기 액티비티(DirectionsActivity)로 연결
        val intent = Intent(this, DirectionsActivity::class.java).apply {
            putExtra("lat", lat)
            putExtra("lng", lng)
            putExtra("address", name)
        }
        startActivity(intent)
    }

    private fun isKakaoMapInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("net.daum.android.map", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openKakaoMapRoadView(lat: Double, lng: Double) {
        // 🏙️ [정밀 교정] 좌표 소수점 자릿수를 6자리로 제한하여 카카오맵 앱 호환성 극대화
        val formattedLat = String.format(java.util.Locale.US, "%.6f", lat)
        val formattedLng = String.format(java.util.Locale.US, "%.6f", lng)
        
        val url = "kakaomap://roadview?p=$formattedLat,$formattedLng"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 앱이 없을 경우 웹 또는 마켓으로 안내 (내추억지도 스타일)
            Toast.makeText(this, "카카오맵 앱이 설치되어 있지 않아 설치 페이지로 이동합니다.", Toast.LENGTH_LONG).show()
            val marketUrl = "market://details?id=net.daum.android.map"
            val webUrl = "https://map.kakao.com/link/roadview/$formattedLat,$formattedLng"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl)))
            } catch (e2: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            }
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
                Toast.makeText(this, "삭제 요청 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 이하: 직접 삭제
            try {
                val deletedRows = contentResolver.delete(uri, null, null)
                dbHelper.deleteMemoryByUri(uriString)
                if (deletedRows > 0) {
                    Toast.makeText(this, "추억이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "사진 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("GalleryDetail", "삭제 실패: ${e.message}")
                Toast.makeText(this, "사진 삭제 권한이 없거나 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
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
            Toast.makeText(this, "공유를 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
