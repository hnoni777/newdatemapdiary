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
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.BitmapFactory

class GalleryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryDetailBinding
    private lateinit var dbHelper: MemoryDatabaseHelper
    private var pendingDeleteUri: Uri? = null
    private var pendingDeleteUriString: String? = null
    private var currentMemory: Memory? = null

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
            var memory = dbHelper.getMemoryByUri(imageUriString.trim())
            
            // DB에 정보가 없을 경우 Exif에서 JSON 메타데이터 파싱 시도 (백업 플랜)
            if (memory == null) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val exif = ExifInterface(input)
                        val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                        if (description != null) {
                            val json = JSONObject(description)
                            val lat = json.optDouble("lat", 0.0)
                            val lng = json.optDouble("lng", 0.0)
                            val profile = json.optString("profile", "")
                            val addrEncoded = json.optString("addr", "")
                            val addr = try { java.net.URLDecoder.decode(addrEncoded, "UTF-8") } catch (e: Exception) { addrEncoded }
                            
                            memory = Memory(
                                photoUri = imageUriString,
                                address = addr,
                                lat = lat,
                                lng = lng,
                                date = System.currentTimeMillis(),
                                profileSticker = if (profile.isNotEmpty()) profile else null
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EXIF_PARSE", "Failed to parse location from Exif", e)
                }
            }
            
            currentMemory = memory
            val lat = memory?.lat ?: 0.0
            val lng = memory?.lng ?: 0.0

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
                showShareSelectionDialog()
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

        // 🛡️ [비공개 테스트 모드] 고급 기능(길찾기, 로드뷰) 차단
        if (AppConfig.IS_TEST_MODE) {
            binding.btnDirections.visibility = View.GONE
            binding.btnRoadview.visibility = View.GONE
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

    private fun showShareSelectionDialog() {
        val memory = currentMemory ?: return
        val uri = Uri.parse(memory.photoUri)
        
        val shareDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val shareView = layoutInflater.inflate(R.layout.dialog_share_selection, null)
        
        // 1️⃣ 추억 카드 바로 공유 (이미지 전송)
        shareView.findViewById<View>(R.id.btn_share_card_direct).setOnClickListener {
            shareImageDirectly(uri)
            shareDialog.dismiss()
        }
        
        // 2️⃣ 상대방 지도에 흔적 남기기 (카카오 공유)
        shareView.findViewById<View>(R.id.btn_share_pin_direct).setOnClickListener {
            shareToLoverViaKakao(memory)
            shareDialog.dismiss()
        }
        
        shareDialog.setContentView(shareView)
        shareDialog.show()
    }

    private fun shareImageDirectly(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "HereWithYou 추억 공유하기"))
        } catch (e: Exception) {
            Toast.makeText(this, "공유를 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareToLoverViaKakao(target: Memory) {
        val shareDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val shareView = layoutInflater.inflate(R.layout.dialog_share_names, null)
        val etSender = shareView.findViewById<android.widget.EditText>(R.id.et_share_sender)
        val etReceiver = shareView.findViewById<android.widget.EditText>(R.id.et_share_receiver)
        val btnConfirm = shareView.findViewById<android.widget.Button>(R.id.btn_confirm_share)
        
        btnConfirm.setOnClickListener {
            val senderName = etSender.text.toString().trim()
            val receiverName = etReceiver.text.toString().trim()
            if (senderName.isNotEmpty() && receiverName.isNotEmpty()) {
                ProfileStickerManager.setMyName(this, senderName)
                ProfileStickerManager.setPartnerName(this, receiverName)
                shareDialog.dismiss()
                proceedToShare(target, senderName, receiverName)
            }
        }
        shareDialog.setContentView(shareView)
        shareDialog.show()
    }

    private fun proceedToShare(target: Memory, senderName: String, receiverName: String) {
        Toast.makeText(this, "우리만의 소중한 장소 공유를 준비합니다 ✨", Toast.LENGTH_SHORT).show()
        kotlin.concurrent.thread {
            try {
                val uri = Uri.parse(target.photoUri)
                val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                
                val myProfileBitmap = if (!target.profileSticker.isNullOrEmpty()) {
                    ProfileStickerManager.getProfileBitmap(this, target.profileSticker!!)
                } else {
                    ProfileStickerManager.getSelectedProfileBitmap(this)
                }
                
                val coverBitmap = createShareCoverGraphic(senderName, receiverName)
                
                val coverFile = java.io.File(cacheDir, "share_cover.jpg")
                coverFile.outputStream().use { coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                val originalFile = java.io.File(cacheDir, "share_original.jpg")
                originalFile.outputStream().use { originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                val profileFile = if (myProfileBitmap != null) {
                    val f = java.io.File(cacheDir, "sh_prof.png")
                    f.outputStream().use { myProfileBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    f
                } else null
                
                runOnUiThread { uploadAndShareTriple(coverFile, originalFile, profileFile, target) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun createShareCoverGraphic(senderName: String, receiverName: String): Bitmap {
        val size = 1000
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val bgPaint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, size.toFloat(),
                intArrayOf(Color.parseColor("#1A1A1A"), Color.parseColor("#000000")),
                null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
        
        val logoPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BBFFFFFF")
            textSize = 34f
            letterSpacing = 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
            try { typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD) } catch (e: Exception) {}
        }
        canvas.drawText("H E R E   W I T H   Y O U", size / 2f, 100f, logoPaint)
        
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            try { typeface = androidx.core.content.res.ResourcesCompat.getFont(this@GalleryDetailActivity, R.font.kyobo_hand_family) } catch (e: Exception) {}
        }
        textPaint.textSize = 62f
        canvas.drawText("${senderName}님이", size / 2f, size / 2f - 60f, textPaint)
        textPaint.apply { textSize = 68f; color = Color.parseColor("#FFD700") }
        canvas.drawText("${receiverName}님에게", size / 2f, size / 2f + 20f, textPaint)
        textPaint.apply { textSize = 54f; color = Color.WHITE }
        canvas.drawText("소중한 추억의 장소를 공유합니다 📍", size / 2f, size / 2f + 110f, textPaint)
        return result
    }

    private fun uploadAndShareTriple(coverFile: java.io.File, originalFile: java.io.File, profileFile: java.io.File?, target: Memory) {
        com.kakao.sdk.share.ShareClient.instance.uploadImage(originalFile) { oResult, oError ->
            if (oError != null || oResult == null) {
                runOnUiThread { Toast.makeText(this, "이미지 업로드 실패 😢", Toast.LENGTH_SHORT).show() }
                return@uploadImage
            }
            val oUrl = oResult.infos.original.url
            
            if (profileFile != null) {
                com.kakao.sdk.share.ShareClient.instance.uploadImage(profileFile) { pResult, pError ->
                    val pUrl = pResult?.infos?.original?.url ?: ""
                    com.kakao.sdk.share.ShareClient.instance.uploadImage(coverFile) { cResult, cError ->
                        val cUrl = cResult?.infos?.original?.url ?: ""
                        sendKakaoLinkWithProfile(cUrl, oUrl, pUrl, target)
                    }
                }
            } else {
                com.kakao.sdk.share.ShareClient.instance.uploadImage(coverFile) { cResult, cError ->
                    val cUrl = cResult?.infos?.original?.url ?: ""
                    sendKakaoLinkWithProfile(cUrl, oUrl, "", target)
                }
            }
        }
    }

    private fun sendKakaoLinkWithProfile(coverUrl: String, originalUrl: String, profileUrl: String, target: Memory) {
        val executionParams = mutableMapOf(
            "lat" to target.lat.toString(),
            "lng" to target.lng.toString(),
            "addr" to (target.address ?: ""),
            "img" to originalUrl
        )
        if (profileUrl.isNotEmpty()) executionParams["profile"] = profileUrl
        
        val feedTemplate = com.kakao.sdk.template.model.FeedTemplate(
            content = com.kakao.sdk.template.model.Content(
                title = "소중한 추억 ✨", description = "지도로 우리만의 비밀 장소를 확인해보세요!",
                imageUrl = coverUrl, link = com.kakao.sdk.template.model.Link(androidExecutionParams = executionParams)
            ),
            buttons = listOf(com.kakao.sdk.template.model.Button("추억 확인하기", com.kakao.sdk.template.model.Link(androidExecutionParams = executionParams)))
        )
        com.kakao.sdk.share.ShareClient.instance.shareDefault(this, feedTemplate) { result, error ->
            if (error == null && result != null) startActivity(result.intent)
        }
    }
}
