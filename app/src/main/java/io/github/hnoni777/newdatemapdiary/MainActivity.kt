package io.github.hnoni777.newdatemapdiary

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import androidx.cardview.widget.CardView
import androidx.exifinterface.media.ExifInterface

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    private lateinit var addressText: TextView
    private var photoUri: Uri? = null

    private val REQ_LOCATION = 100
    private val REQ_CAMERA = 200
    private val KAKAO_REST_KEY = "83aa83329de094b2cf52a2e8a34206fa"

    private lateinit var cameraUri: Uri
    
    // 📍 [NEW] 프로필 스티커용 이미지 선택기
    private val pickProfileImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processProfileSticker(it) }
    }

    private val cameraLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri = cameraUri
                // 📸 [이중 안전 장치] 촬영 직후, 가장 최신의 위치로 한 번 더 갱신 요청
                fetchAndShowMyLocation()
                showCardPreview()
                Toast.makeText(this, "사진 촬영 완료!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "사진 촬영 취소", Toast.LENGTH_SHORT).show()
            }
        }

    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var isFromDeepLink: Boolean = false
    private var deepLinkAddress: String = ""

    // 📩 초대장 수신용 프리미엄 다이얼로그 관리
    private var invitationDialog: android.app.Dialog? = null
    private var statusDotsTimer: java.util.Timer? = null
    
    // 🔄 [최적화] 열려있는 프로필 관리 다이얼로그 참조
    private var activeProfileDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var activeProfileAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>? = null
    private var activeProfileList: MutableList<java.io.File>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🗺️ 카카오 지도 인증 실패 해결을 위한 실제 해시 키 출력 (Logcat에서 KAKAO_HASH_KEY 로 검색)
        printHashKey()

        addressText = findViewById(R.id.text_address)

        mapView = MapView(this)
        findViewById<FrameLayout>(R.id.map_container).addView(mapView)

        handleDeepLink(intent)

        intent.getStringExtra("photoUri")?.let {
            photoUri = Uri.parse(it)
            if (!isFromDeepLink) showCardPreview()
        }

        setupButtons()
        
        if (isFromDeepLink) {
            // 🛡️ [비공개 테스트 모드] 딥링크(흔적 남기기) 수신 차단
            if (AppConfig.IS_TEST_MODE) {
                addressText.text = "새로운 추억을 기록해보세요!"
                requestLocationPermission()
            } else {
                startMap()
                addressText.text = deepLinkAddress
                showDeepLinkInvitationCard()
            }
        } else {
            requestLocationPermission()
        }

        // ✨ [NEW] 대시보드 초기화 및 자동 갱신
        refreshDashboard()

        // 🛡️ [비공개 테스트 모드] 메인 진입로 은닉
        if (AppConfig.IS_TEST_MODE) {
            findViewById<View>(R.id.btn_memory_map)?.visibility = View.GONE
            findViewById<View>(R.id.btn_profile_settings)?.visibility = View.GONE
        }
    }

    private fun printHashKey() {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val key = Base64.encodeToString(md.digest(), Base64.DEFAULT)
                Log.d("KAKAO_HASH_KEY", "Key: $key")
            }
        } catch (e: Exception) {
            Log.e("KAKAO_HASH_KEY", "Error getting hash key: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 💡 Update the activity's intent
        handleDeepLink(intent)
        
        if (isFromDeepLink) {
            // Re-initialise map or move camera if needed
            if (kakaoMap != null) {
                showLocationOnMap(LatLng.from(currentLat, currentLng))
            } else {
                startMap()
            }
            addressText.text = deepLinkAddress
            showDeepLinkInvitationCard()
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val action: String? = intent?.action
        val data: Uri? = intent?.data
        if (Intent.ACTION_VIEW == action && data != null) {
            val latStr = data.getQueryParameter("lat")
            val lngStr = data.getQueryParameter("lng")
            val addrStr = data.getQueryParameter("addr")
            val imgUrl = data.getQueryParameter("img")
            val profUrl = data.getQueryParameter("profile")
            
            if (latStr != null && lngStr != null) {
                currentLat = latStr.toDoubleOrNull() ?: 0.0
                currentLng = lngStr.toDoubleOrNull() ?: 0.0
                deepLinkAddress = addrStr ?: ""
                isFromDeepLink = true
                
                // 🌉 [추가] 이미지가 있다면 다운로드 시도
                if (!imgUrl.isNullOrEmpty()) {
                    downloadDeepLinkImage(imgUrl, profUrl)
                }
            }
        }
    }

    private fun downloadDeepLinkImage(url: String, profUrl: String?) {
        // 1️⃣ [초기화] 이전 다이얼로그나 타이머 정리
        statusDotsTimer?.cancel()
        runOnUiThread { invitationDialog?.dismiss() }

        // 2️⃣ [다이얼로그 준비] 프리미엄 초대장 먼저 띄움
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val v = layoutInflater.inflate(R.layout.dialog_invitation_card, null)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.setCancelable(false) // 다운로드 중 실수로 닫기 방지

        val ivPreview = v.findViewById<ImageView>(R.id.iv_invitation_preview)
        val tvStatus = v.findViewById<TextView>(R.id.tv_invitation_status)
        val tvDots = v.findViewById<TextView>(R.id.tv_invitation_dots)
        val pbLoading = v.findViewById<ProgressBar>(R.id.pb_invitation_loading)
        val btnAccept = v.findViewById<Button>(R.id.btn_invitation_accept)
        val btnCancel = v.findViewById<View>(R.id.btn_invitation_cancel)

        // 🔘 취소 버튼 (나중에)
        btnCancel.setOnClickListener {
            statusDotsTimer?.cancel()
            dialog.dismiss()
        }

        // ⏳ 점 애니메이션 시작
        var dotCount = 0
        statusDotsTimer = java.util.Timer().apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    runOnUiThread {
                        dotCount = (dotCount + 1) % 4
                        tvDots.text = ".".repeat(dotCount)
                    }
                }
            }, 0, 500)
        }

        invitationDialog = dialog
        dialog.show()

        // 3️⃣ [네트워크 작업] 백그라운드 스레드 시작
        thread {
            try {
                if (url.isEmpty()) {
                    runOnUiThread { 
                        Toast.makeText(this@MainActivity, "공유 정보가 비어있습니다. 😢", Toast.LENGTH_SHORT).show() 
                        dialog.dismiss()
                    }
                    return@thread
                }

                // 📸 1. 메인 이미지 다운로드
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                
                val file = java.io.File(cacheDir, "deeplink_shared_photo.jpg")
                java.io.FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
                photoUri = Uri.fromFile(file)

                // 메인 사진 로딩 완료 시 미리보기 갱신
                runOnUiThread {
                    ivPreview.setImageBitmap(bitmap)
                    tvStatus.text = "핀 정보를 확인하는 중"
                }

                // 👤 2. 프로필 핀 다운로드 (있을 경우)
                var senderProfileFilename: String? = null
                if (!profUrl.isNullOrEmpty()) {
                    try {
                        val pConn = java.net.URL(profUrl).openConnection() as java.net.HttpURLConnection
                        pConn.doInput = true
                        pConn.connect()
                        val pBitmap = BitmapFactory.decodeStream(pConn.inputStream)
                        if (pBitmap != null) {
                            senderProfileFilename = ProfileStickerManager.saveProfileSticker(this@MainActivity, pBitmap)
                        }
                    } catch (pe: Exception) {
                        Log.e("DEEPLINK_PROF", "Profile download failed", pe)
                    }
                }

                // 🏁 3. 최종 완료 처리
                runOnUiThread {
                    statusDotsTimer?.cancel()
                    tvDots.text = "!"
                    tvStatus.text = "수신 완료 ✨"
                    tvStatus.setTextColor(Color.WHITE)
                    pbLoading.visibility = View.GONE
                    
                    // 수락 버튼 활성화
                    btnAccept.alpha = 1.0f
                    btnAccept.isEnabled = true
                    btnAccept.setOnClickListener {
                        saveDeepLinkMemoryImmediately(bitmap, senderProfileFilename)
                        dialog.dismiss()
                    }
                    
                    // 메인 화면 미리보기도 갱신
                    showCardPreview()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusDotsTimer?.cancel()
                    Toast.makeText(this@MainActivity, "추억을 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun saveDeepLinkMemoryImmediately(bitmap: Bitmap, profileFilename: String?) {
        val savedUri = saveBitmapToGallery(bitmap, currentLat, currentLng, deepLinkAddress.trim())
        if (savedUri != null) {
            try {
                val dbHelper = MemoryDatabaseHelper(this)
                val memory = Memory(
                    photoUri = savedUri.toString(),
                    address = deepLinkAddress.trim(),
                    lat = currentLat,
                    lng = currentLng,
                    date = System.currentTimeMillis(),
                    profileSticker = profileFilename // 보낸 사람의 프로필이 박힘!
                )
                dbHelper.insertMemory(memory)
                runOnUiThread {
                    dismissInvitation()
                    showSaveSuccessDialog()
                }
                    
            } catch (e: Exception) {
                Log.e("DB_INSERT", "딥링크 저장 실패", e)
            }
        }
    }

    private fun showSaveSuccessDialog() {
        val dialog = android.app.Dialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_success, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btn_save_go_map).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MemoryMapActivity::class.java))
        }

        dialogView.findViewById<Button>(R.id.btn_save_close).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun dismissInvitation() {
        runOnUiThread {
            statusDotsTimer?.cancel()
            invitationDialog?.dismiss()
            invitationDialog = null
        }
    }


    private fun setupButtons() {
        findViewById<View>(R.id.btn_camera).setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        findViewById<View>(R.id.btn_save_photo).setOnClickListener {
            photoUri?.let {
                savePhotoToGallery(it)
            } ?: Toast.makeText(this, "저장할 사진이 없습니다", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_screenshot).setOnClickListener {
            if (photoUri == null) {
                Toast.makeText(this, "먼저 사진을 촬영해주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            takeScreenshot(false)
        }

        findViewById<View>(R.id.btn_share_photo).setOnClickListener {
            if (photoUri == null) {
                Toast.makeText(this, "먼저 사진을 촬영해주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            takeScreenshot(true)
        }

        findViewById<View>(R.id.btn_gallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<View>(R.id.btn_memory_map).setOnClickListener {
            startActivity(Intent(this, MemoryMapActivity::class.java))
        }

        findViewById<View>(R.id.btn_create_card).setOnClickListener {
            if (photoUri == null) {
                Toast.makeText(this, "위의 📸 카메라 버튼을 눌러 사진을 먼저 촬영해주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, CardEditorActivity::class.java).apply {
                putExtra("photoUri", photoUri.toString())
                putExtra("address", addressText.text.toString())
                putExtra("lat", currentLat)
                putExtra("lng", currentLng)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_profile_settings).setOnClickListener {
            showProfileManagerDialog()
        }

    }

    // 📸 스샷로직
    private fun takeScreenshot(shareAfter: Boolean) {
        val floatingBar = findViewById<View>(R.id.floating_action_bar)
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        val createCardBtn = findViewById<View>(R.id.btn_create_card)
        val addressTextBtn = findViewById<View>(R.id.text_address)
        
        val innerCard = if (container.childCount > 0) {
            val cardView = container.getChildAt(0) as? android.view.ViewGroup
            cardView?.getChildAt(0) ?: container
        } else container

        floatingBar.visibility = View.GONE
        createCardBtn.visibility = View.GONE
        addressTextBtn.visibility = View.GONE

        val bitmap = Bitmap.createBitmap(innerCard.width, innerCard.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val photoView = innerCard.findViewById<View>(R.id.card_image)
        val photoWrapper = photoView?.parent as? View ?: photoView
        
        innerCard.draw(canvas)
        
        try {
            if (photoView != null && photoWrapper != null) {
                val radiusPx = 12 * resources.displayMetrics.density
                
                var rx = 0f
                var ry = 0f
                var current: View? = photoWrapper
                while (current != null && current != innerCard) {
                    rx += current.x
                    ry += current.y
                    current = current.parent as? View
                }
                
                val left = rx
                val top = ry
                val right = left + photoWrapper.width
                val bottom = top + photoWrapper.height
                
                val paint = android.graphics.Paint().apply {
                    color = (innerCard.background as? android.graphics.drawable.ColorDrawable)?.color 
                            ?: (innerCard as? androidx.cardview.widget.CardView)?.cardBackgroundColor?.defaultColor
                            ?: android.graphics.Color.parseColor("#F0F7FF")
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRect(left, top, right, bottom, paint)
                
                val path = android.graphics.Path().apply {
                    addRoundRect(
                        android.graphics.RectF(left, top, right, bottom),
                        radiusPx, radiusPx,
                        android.graphics.Path.Direction.CW
                    )
                }
                
                canvas.save()
                canvas.clipPath(path)
                canvas.translate(left, top)
                photoWrapper.draw(canvas)
                canvas.restore()

                val stickerLayer = innerCard.findViewById<View>(R.id.sticker_container)
                if (stickerLayer != null) {
                    var sdx = 0f
                    var sdy = 0f
                    var sCurrent: View? = stickerLayer
                    while (sCurrent != null && sCurrent != innerCard) {
                        sdx += sCurrent.x
                        sdy += sCurrent.y
                        sCurrent = sCurrent.parent as? View
                    }
                    canvas.save()
                    canvas.translate(sdx, sdy)
                    stickerLayer.draw(canvas)
                    canvas.restore()
                }
            }
        } catch (e: Exception) {
            Log.e("SCREENSHOT_ROUNDING", "Precision drawing failed", e)
        }
        
        floatingBar.visibility = View.VISIBLE
        createCardBtn.visibility = View.VISIBLE
        addressTextBtn.visibility = View.VISIBLE

        // 💡 주의: 여기서 DB에 자동 저장하면, 나중에 '카드 꾸미기' 후 저장할 때 중복이 생길 수 있음.
        // 하지만 대표님께서 '스샷' 버튼을 눌렀을 때의 기록도 남기고 싶어 하시므로 유지하되, 
        // 주소의 공백 등을 제거하여 추후 매칭이 잘 되게 함.
        val savedUri = saveBitmapToGallery(bitmap, currentLat, currentLng, addressText.text.toString().trim())
        if (savedUri != null) {
            try {
                val dbHelper = MemoryDatabaseHelper(this)
                // 📍 [정밀화] 저장 시점부터 주소를 트림하여 정규화 주소와의 괴리 방지
                val memory = Memory(
                    photoUri = savedUri.toString(),
                    address = addressText.text.toString().trim(),
                    lat = currentLat,
                    lng = currentLng,
                    date = System.currentTimeMillis(),
                    profileSticker = ProfileStickerManager.getSelectedProfileFilename(this)
                )
                dbHelper.insertMemory(memory)
                Log.d("DB_INSERT", "메인 스샷 저장 성공")
                // ✨ 저장 성공 시 대시보드 즉시 갱신
                runOnUiThread { refreshDashboard() }
            } catch (e: Exception) {
                Log.e("DB_INSERT", "내 추억지도 자동 저장 실패", e)
            }
        }

        if (shareAfter && savedUri != null) {
            shareImage(savedUri, currentLat, currentLng, addressText.text.toString())
        } else if (savedUri != null) {
            val msg = if (AppConfig.IS_TEST_MODE) "보관함에 저장 완료 ✨" else "스샷 저장 및 추억지도에 등록 완료"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveBitmapToGallery(bitmap: Bitmap, lat: Double, lng: Double, address: String): Uri? {
        try {
            val filename = "DateMapDiary_Screenshot_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HereWithYou")
            }

            // Exif Metadata Injector용 임시 파일 🕵️‍♂️
            val tempFile = java.io.File(cacheDir, "temp_screenshot_exif.jpg")
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val encodedAddr = java.net.URLEncoder.encode(address, "UTF-8")
                val sender = java.net.URLEncoder.encode(ProfileStickerManager.getMyName(this), "UTF-8")
                val receiver = java.net.URLEncoder.encode(ProfileStickerManager.getPartnerName(this), "UTF-8")
                val profile = ProfileStickerManager.getSelectedProfileFilename(this) ?: ""
                val jsonMeta = "{\"lat\":$lat, \"lng\":$lng, \"addr\":\"$encodedAddr\", \"sender\":\"$sender\", \"receiver\":\"$receiver\", \"profile\":\"$profile\"}"
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, jsonMeta)
                exif.saveAttributes()
            } catch (e: Exception) {
                Log.e("EXIF", "Metadata injection failed", e)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("MediaStore insert failed")
            contentResolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(tempFile).use { input ->
                    input.copyTo(out)
                }
            }
            tempFile.delete()
            return uri
        } catch (e: Exception) {
            Log.e("SCREENSHOT", e.toString())
            return null
        }
    }

    private fun shareImage(uri: Uri, lat: Double, lng: Double, address: String) {
        try {
            // 💡 깔끔하게 카드 이미지만 공유
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(null, uri)
            }
            startActivity(Intent.createChooser(shareIntent, "추억 카드 공유하기"))
        } catch (e: Exception) {
            Log.e("ShareError", "공유 중 에러 발생: ${e.message}")
        }
    }

    private fun savePhotoToGallery(uri: Uri) {
        try {
            val fileName = "NewDateMapDiary_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HereWithYou")
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("MediaStore insert failed")
            contentResolver.openOutputStream(imageUri).use { output ->
                contentResolver.openInputStream(uri).use { input ->
                    input?.copyTo(output!!)
                }
            }
            Toast.makeText(this, "사진 저장 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SAVE_PHOTO", e.toString())
            Toast.makeText(this, "사진 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // ===============================
    // 💌 카드 미리보기
    // ===============================
    private fun showCardPreview() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        
        // 💡 [코부장 최적화] 이미 카드가 있다면 새로 그리지 않고 내용만 바꿉니다. (성능 향상)
        val cardView = if (container.childCount > 0) {
            container.getChildAt(0)
        } else {
            val v = layoutInflater.inflate(R.layout.item_memory_card_04, container, false)
            container.addView(v)
            v
        }

        val imgView = cardView.findViewById<ImageView>(R.id.card_image)
        if(photoUri != null) {
            imgView.setImageURI(photoUri)
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER
            imgView.adjustViewBounds = true
            val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            lp.dimensionRatio = null
            imgView.layoutParams = lp

            cardView.findViewById<TextView>(R.id.card_message).text = "오늘의 로맨틱한 순간"
            cardView.findViewById<TextView>(R.id.card_address).text = addressText.text
            val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREA).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
            }
            cardView.findViewById<TextView>(R.id.card_date).text = sdf.format(Date())

            // 🛡️ [비공개 테스트 모드] 카드 레이아웃 단순화
            if (AppConfig.IS_TEST_MODE) {
                cardView.findViewById<View>(R.id.card_rating_container)?.visibility = View.GONE
                cardView.findViewById<View>(R.id.card_qr_code)?.visibility = View.GONE
                cardView.findViewById<View>(R.id.card_watermark)?.visibility = View.GONE
                cardView.findViewById<View>(R.id.card_premium_border)?.visibility = View.GONE
                cardView.findViewById<View>(R.id.card_premium_bg)?.visibility = View.GONE
            } else {
                updateCardQRCode(cardView, currentLat, currentLng, addressText.text.toString())
            }
        } else {
            // 빈 사진일 경우 다꾸 초대장 이미지를 플레이스홀더로 사용
            imgView.setImageResource(R.drawable.bg_invitation)
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER
            imgView.adjustViewBounds = true
            val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            imgView.layoutParams = lp
        }
        
        // 🚀 [웰컴 복구] 설치 후 최초 실행 시 1회만 자동 복구 수행
        checkAndPerformInitialRestore()
    }

    private fun checkAndPerformInitialRestore() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isRestoreDone = prefs.getBoolean("initial_restore_done", false)
        
        if (!isRestoreDone) {
            val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED) {
                performBackgroundRestore()
            }
        }
    }

    private fun performBackgroundRestore() {
        thread {
            try {
                // 1️⃣ 프로필 스티커 파일들부터 갤러리에서 앱 내부로 먼저 복사 (선행 필수!)
                ProfileStickerManager.restoreProfileFromGallery(this)

                val dbHelper = MemoryDatabaseHelper(this)
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%Pictures/HereWithYou%", "DateMapDiary_Card_%")
                
                val cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )

                var restoredCount = 0
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        
                        if (dbHelper.getMemoryByUri(uri.toString()) == null) {
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
                                    
                                    val memory = Memory(
                                        photoUri = uri.toString(),
                                        address = addr,
                                        lat = lat,
                                        lng = lng,
                                        date = System.currentTimeMillis(),
                                        profileSticker = if (profile.isNotEmpty()) profile else null
                                    )
                                    dbHelper.insertMemory(memory)
                                    restoredCount++
                                }
                            }
                        }
                    }
                }
                
                getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                    .putBoolean("initial_restore_done", true).apply()
                
                if (restoredCount > 0) {
                    runOnUiThread { Toast.makeText(this, "${restoredCount}개의 추억을 복구했습니다! ✨", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Log.e("AUTO_RESTORE", "Restore failed", e)
            }
        }
    }

    private fun showDeepLinkInvitationCard() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        container.removeAllViews()

        val cardView = layoutInflater.inflate(R.layout.item_memory_card_04, container, false)
        val imgView = cardView.findViewById<ImageView>(R.id.card_image)
        imgView.setImageResource(R.drawable.bg_invitation)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        imgView.adjustViewBounds = true
        
        val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        imgView.layoutParams = lp

        cardView.findViewById<TextView>(R.id.card_message).text = ""
        cardView.findViewById<TextView>(R.id.card_address).text = deepLinkAddress
        cardView.findViewById<TextView>(R.id.card_date).text = ""

        updateCardQRCode(cardView, currentLat, currentLng, deepLinkAddress)
        findViewById<View>(R.id.btn_create_card).visibility = View.GONE
        container.addView(cardView)
    }

    // ===============================
    // 📍 위치 / 지도
    // ===============================
    private fun requestLocationPermission() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_LOCATION)
        } else {
            startMap()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            when (requestCode) {
                REQ_LOCATION -> {
                    startMap()
                    checkAndPerformInitialRestore()
                }
                REQ_CAMERA -> openCamera()
            }
        }
    }

    // 🔐 카메라 권한 체크
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // 🚀 [1안 적용] 카메라 열기 직전에 GPS 신호를 미리 최신으로 갱신합니다.
            // 앱을 오랫동안 켜둔 상태에서 촬영 시 오래된 캐시 위치가 찍히는 현상 방지!
            fetchAndShowMyLocation()
            openCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    // 📸 카메라 실행
    private fun openCamera() {
        val photoFile = java.io.File.createTempFile("photo_", ".jpg", cacheDir)
        cameraUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraLauncher.launch(cameraUri)
    }

    private fun startMap() {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {
                Log.e("MAP_ERROR", error.toString())
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "📍 지도 오류: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                if (isFromDeepLink) {
                    showLocationOnMap(LatLng.from(currentLat, currentLng))
                } else {
                    fetchAndShowMyLocation()
                }
            }
        })
    }

    private fun fetchAndShowMyLocation() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        try {
            // 💡 [코부장 처방] 먼저 캐시된 위치(lastLocation)를 즉시 시도하여 초기 로딩 속도를 올립니다.
            fused.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    currentLat = lastLoc.latitude
                    currentLng = lastLoc.longitude
                    val pos = LatLng.from(currentLat, currentLng)
                    showLocationOnMap(pos)
                    fetchAddressFromKakao(pos.latitude, pos.longitude)
                }

                // 그와 동시에 가장 정확한 실시간 위치 수신을 백그라운드에서 실행합니다. (정밀도 보장)
                val cts = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            // 실시간 위치가 수신되면 의미 있는 차이가 있을 때만 갱신
                            if (currentLat != loc.latitude || currentLng != loc.longitude) {
                                currentLat = loc.latitude
                                currentLng = loc.longitude
                                val pos = LatLng.from(currentLat, currentLng)
                                showLocationOnMap(pos)
                                fetchAddressFromKakao(pos.latitude, pos.longitude)
                                Log.d("LOCATION", "실시간 위치로 정밀 갱신 완료")
                            }
                        }
                    }
            }
        } catch (e: SecurityException) {
            Log.e("LOCATION", "Permission missing", e)
        }
    }

    private fun showLocationOnMap(pos: LatLng) {
        val map = kakaoMap ?: return

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(pos, 17)
        )

        val layerId = "my_location"
        var layer = map.labelManager?.getLayer(layerId)
        if (layer == null) {
            layer = map.labelManager?.addLayer(LabelLayerOptions.from(layerId))
        }
        layer?.removeAll()

        val markerBitmap = vectorToBitmap(R.drawable.ic_red_heart_marker)
        val styles = LabelStyles.from(
            LabelStyle.from(markerBitmap)
                .setAnchorPoint(0.5f, 1.0f)
        )

        layer?.addLabel(
            LabelOptions.from(pos).setStyles(styles)
        )
    }

    private fun vectorToBitmap(resId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, resId) ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(
            Math.max(1, drawable.intrinsicWidth),
            Math.max(1, drawable.intrinsicHeight),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun fetchAddressFromKakao(lat: Double, lng: Double) {
        thread {
            try {
                val url = URL("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_KEY")

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(json)
                    val docs = root.getJSONArray("documents")

                if (docs.length() > 0) {
                    val obj = docs.getJSONObject(0)
                    val road = obj.optJSONObject("road_address")
                    val addr = road?.optString("address_name")
                        ?: obj.getJSONObject("address").optString("address_name")

                    runOnUiThread {
                        addressText.text = addr
                        showCardPreview() // 주소가 오면 카드 프리뷰 다시 갱신
                    }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "오류 정보 없음"
                    Log.e("KAKAO_ADDRESS", "HTTP $responseCode: $err")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "🏠 주소 오류($responseCode): $err", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("KAKAO_ADDRESS", e.toString())
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "🏠 주소 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.finish()
    }

    // ===============================
    // 🔍 QR Code Generation
    // ===============================
    private fun updateCardQRCode(cardView: View, lat: Double, lng: Double, addr: String) {
        val qrView = cardView.findViewById<ImageView>(R.id.card_qr_code) ?: return
        
        // 💡 [코부장 최적화] QR 생성은 무거운 루프가 포함되므로 백그라운드 스레드로 분리합니다.
        thread {
            try {
                // 💡 [코부장 배포 최적화] 새로운 블랙&골드 랜딩 페이지로 연결합니다.
                val link = "https://hnoni777.github.io/newdatemapdiary/"
                val qrBitmap = generateQRCode(link)
                
                runOnUiThread {
                    if (qrBitmap != null) {
                        qrView.setImageBitmap(qrBitmap)
                        qrView.visibility = View.VISIBLE
                    } else {
                        qrView.visibility = View.INVISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("QR_CODE", "Failed to add QR", e)
            }
        }
    }

    private fun generateQRCode(url: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
            ) 
            // 💡 [코부장 최적화] 256x256 크기로 줄이고, setPixels로 속도를 10배 이상 올립니다.
            val bitMatrix = writer.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                256,
                256,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.TRANSPARENT
                }
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            bmp
        } catch (e: Exception) {
            Log.e("QR_GEN", "Error", e)
            null
        }
    }

    private fun processProfileSticker(uri: Uri) {
        // 🔥 [최적화] 비트맵 디코딩 및 변환 작업을 백그라운드에서 실행
        kotlin.concurrent.thread {
            try {
                runOnUiThread {
                    Toast.makeText(this, "💑 프로필 스티커 생성 중...", Toast.LENGTH_SHORT).show()
                }

                val inputStream = contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // 🔄 [회전 보정] 사진 똑바로 세우기
                val rotationInputStream = contentResolver.openInputStream(uri)
                val exif = rotationInputStream?.let { androidx.exifinterface.media.ExifInterface(it) }
                val orientation = exif?.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                rotationInputStream?.close()

                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                val rotated = if (orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL) {
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                } else original

                // 🎨 스티커 생성 (가장 최신 튜닝 버전: 풍성한 머리카락 + 프로필 스타일)
                FaceStickerUtil.createFaceSticker(rotated) { sticker ->
                    runOnUiThread {
                        if (sticker != null) {
                            ProfileStickerManager.saveProfileSticker(this, sticker)
                            Toast.makeText(this, "새 프로필이 등록되었습니다! ✨", Toast.LENGTH_SHORT).show()
                            
                            // 🔄 [자동 새로고침] 다이얼로그가 열려있다면 즉시 반영
                            activeProfileList?.let { list ->
                                list.clear()
                                list.addAll(ProfileStickerManager.getProfileStickers(this))
                                activeProfileAdapter?.notifyDataSetChanged()
                            }
                        } else {
                            Toast.makeText(this, "얼굴을 찾을 수 없습니다. 정면 사진을 사용해 주세요.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PROFILE_ERROR", e.message ?: "")
            }
        }
    }

    private fun showProfileManagerDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        activeProfileDialog = dialog
        val view = layoutInflater.inflate(R.layout.dialog_profile_manager, null)
        
        val rvList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_profile_list)
        val btnAdd = view.findViewById<View>(R.id.btn_add_profile)
        val btnApply = view.findViewById<View>(R.id.btn_apply_profile)

        val profiles = ProfileStickerManager.getProfileStickers(this).toMutableList()
        activeProfileList = profiles
        
        rvList.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 3)
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class ProfileViewHolder(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
                val img = v.findViewById<ImageView>(R.id.img_profile_sticker)
                val indicator = v.findViewById<View>(R.id.bg_selected_indicator)
                val btnDelete = v.findViewById<View>(R.id.btn_delete_profile)
            }

            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                return ProfileViewHolder(layoutInflater.inflate(R.layout.item_profile_sticker, parent, false))
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val h = holder as ProfileViewHolder
                val file = profiles[position]
                val selected = ProfileStickerManager.getSelectedProfileFilename(this@MainActivity)
                
                com.bumptech.glide.Glide.with(this@MainActivity).load(file).into(h.img)
                h.indicator.visibility = if (file.name == selected) View.VISIBLE else View.INVISIBLE
                
                h.itemView.setOnClickListener {
                    ProfileStickerManager.setSelectedProfile(this@MainActivity, file.name)
                    notifyDataSetChanged()
                }
                
                h.btnDelete.setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("프로필 삭제")
                        .setMessage("이 프로필을 삭제할까요?")
                        .setPositiveButton("삭제") { _, _ ->
                            ProfileStickerManager.deleteProfileSticker(this@MainActivity, file.name)
                            profiles.clear()
                            profiles.addAll(ProfileStickerManager.getProfileStickers(this@MainActivity))
                            notifyDataSetChanged()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
            override fun getItemCount() = profiles.size
        }
        activeProfileAdapter = adapter
        rvList.adapter = adapter

        btnAdd.setOnClickListener {
            // 🔥 [개선] 다이얼로그를 닫지 않고 바로 선택기 실행!
            pickProfileImage.launch("image/*")
        }
        
        btnApply.setOnClickListener {
            val currentSelected = ProfileStickerManager.getSelectedProfileFilename(this)
            if (currentSelected.isNullOrEmpty()) {
                Toast.makeText(this, "먼저 적용할 프로필을 선택해 주세요!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "닉네임과 프로필 핀이 적용되었습니다! ✨", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setContentView(view)
        dialog.setOnDismissListener {
            activeProfileDialog = null
            activeProfileAdapter = null
            activeProfileList = null
        }
        dialog.show()
    }
    
    // ===============================
    // 👣 Footprint Analytics Dashboard Logic
    // ===============================
    private fun refreshDashboard() {
        try {
            val dbHelper = MemoryDatabaseHelper(this)
            val memories = dbHelper.getAllMemories()
            
            // 1. 총 발자국 개수
            findViewById<TextView>(R.id.text_memory_count).text = "${memories.size}개"

            if (memories.isEmpty()) {
                findViewById<TextView>(R.id.text_hot_spot).text = "기록 없음"
                findViewById<TextView>(R.id.text_latest_date).text = "-"
                return
            }

            // 2. 자주 가는 곳 (Hot Spot) 분석: '동/읍/면' 단위를 우선하여 더 상세하게!
            val areaMap = mutableMapOf<String, Int>()
            memories.forEach { m ->
                val addr = m.address
                val parts = addr.split(" ")
                parts.forEach { part ->
                    // 대한민국 주소 체계상 가장 체감도가 높은 '동/읍/면'과 '구' 단위를 수집
                    if (part.endsWith("동") || part.endsWith("읍") || part.endsWith("면") || part.endsWith("구")) {
                        areaMap[part] = areaMap.getOrDefault(part, 0) + 1
                    }
                }
            }
            // 가장 빈도가 높은 지역 추출 (없으면 '시' 단위라도 검색)
            var hotSpot = areaMap.maxByOrNull { it.value }?.key
            
            if (hotSpot == null) {
                val cityMap = mutableMapOf<String, Int>()
                memories.forEach { m ->
                    m.address.split(" ").forEach { part ->
                        if (part.endsWith("시") || part.endsWith("군")) {
                            cityMap[part] = cityMap.getOrDefault(part, 0) + 1
                        }
                    }
                }
                hotSpot = cityMap.maxByOrNull { it.value }?.key ?: "탐색 중"
            }
            findViewById<TextView>(R.id.text_hot_spot).text = hotSpot

            // 3. 마지막 기록 (Latest Activity)
            val latestMemory = memories.maxByOrNull { it.date }
            latestMemory?.let {
                val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREA).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
                }
                val timeText = sdf.format(Date(it.date))
                findViewById<TextView>(R.id.text_latest_date).text = timeText
            }
            
        } catch (e: Exception) {
            Log.e("DASHBOARD", "Analytics failed", e)
        }
    }
}
