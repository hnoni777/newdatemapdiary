package io.github.hnoni777.newdatemapdiary

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.*
import com.kakao.vectormap.label.*
import java.text.SimpleDateFormat
import java.util.*
import android.provider.MediaStore
import android.content.ContentUris
import android.os.Build
import android.app.RecoverableSecurityException
import android.content.IntentSender
import org.json.JSONObject
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.route.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MemoryMapActivity : AppCompatActivity() {

    private val KAKAO_REST_KEY = "83aa83329de094b2cf52a2e8a34206fa"

    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private lateinit var dbHelper: MemoryDatabaseHelper
    private var memories = listOf<Memory>()
    private var sortedMemoriesForPath = listOf<Memory>()
    private var memoryStopIndexes = mutableListOf<Int>() // fullJourneyPoints 내의 정지 지점 인덱스
    private var isPathPlaying = false
    private var isMovingToPoint = false
    private var currentPathIndex = 0
    private var fullJourneyPoints = listOf<LatLng>()
    private var flightAnimator: android.animation.Animator? = null
    private var airplaneLabel: Label? = null
    private var currentRouteLine: RouteLine? = null
    private var isRouteReady = false
    private var cachedAirplaneBitmap: Bitmap? = null
    private val markerBitmapCache = mutableMapOf<String, Bitmap>() // 📸 [최적화] 핀 비트맵 캐시

    // 💎 [프리미엄 설정 키]
    private val PREFS_NAME = "MapPremiumPrefs"
    private val KEY_VEHICLE = "map_vehicle"
    private val KEY_COLOR = "map_color"
    private val KEY_PHOTO_PINS = "map_photo_pins"

    // 🕊️ 안드로이드 10/11+ 갤러리 삭제 승인을 위한 런처
    private val deleteLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "갤러리 파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            // 지도가 갱신되어야 한다면 호출
            showMemoriesOnMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory_map)

        dbHelper = MemoryDatabaseHelper(this)
        memories = dbHelper.getAllMemories()

        mapView = MapView(this)
        findViewById<FrameLayout>(R.id.memory_map_container).addView(mapView)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_sync).setOnClickListener {
            checkPermissionAndSyncMemories()
        }

        findViewById<View>(R.id.btn_play_path).setOnClickListener {
            startPathAnimation()
        }

        findViewById<View>(R.id.btn_map_settings).setOnClickListener {
            showMapSettingsDialog()
        }

        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {
                Log.e("MEMORY_MAP", error.toString())
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                showMemoriesOnMap()

                map.setOnLabelClickListener { _, _, label ->
                    val tagAddr = label.tag as? String ?: ""
                    
                    // 태그(정규화주소)와 일치하는 모든 카드 수집
                    val group = memories.filter { normalizeAddress(it.address) == tagAddr }
                    if (group.isNotEmpty()) {
                        showMemoryCardDialog(group)
                    }
                    true
                }

                // 🎬 [애니메이션 리스너]
                map.setOnCameraMoveEndListener { _, _, gestureType ->
                    if (isPathPlaying && (gestureType == GestureType.Unknown || gestureType == GestureType.OneFingerDoubleTap)) {
                        // fitMapPoints 등이 끝났을 때 첫 시작
                        if (currentPathIndex == 0 && !isMovingToPoint) {
                            mapView.postDelayed({
                                playNextFlight()
                            }, 1000)
                        }
                    }
                }
            }
        })
    }

    // 📍 [대표님 지시] 주소 텍스트가 사실상 같으면 무조건 하나로 합침
    // 공백, 특수문자 등을 모두 제거하고 순수 글자만 비교하여 동일 장소 판단
    private fun normalizeAddress(addr: String): String {
        return addr.replace("[^ㄱ-ㅎㅏ-ㅣ가-힣a-zA-Z0-9]".toRegex(), "")
    }

    private fun showMemoriesOnMap() {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layerId = "memories_layer"
        
        // 1️⃣ 기존 핀 완전 박멸 (유령 핀 방지)
        // 레이어를 가져와서 모든 라벨을 지웁니다.
        val layer = labelManager.getLayer(layerId) ?: labelManager.addLayer(LabelLayerOptions.from(layerId))
        layer?.removeAll() 

        if (memories.isEmpty()) {
            Toast.makeText(this, "저장된 추억이 없습니다.", Toast.LENGTH_SHORT).show()
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 10))
            return
        }

        // 🏠 [대표님 지시] 주소 텍스트가 사실상 같으면 무조건 하나로 합침
        // normalizeAddress를 통해 공백/특수문자를 무시하고 글자만 같으면 그룹화합니다.
        val groups = memories.groupBy { normalizeAddress(it.address) }

        // 💎 프리미엄 설정 로드
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val showPhotoPins = prefs.getBoolean(KEY_PHOTO_PINS, false)

        groups.forEach { (normAddr, group) ->
            // 그룹 중 가장 최근 데이터의 좌표에 핀 하나만 꽂음
            val rep = group.first()
            val latestRating = rep.rating
            
            val finalMarkerBitmap = if (showPhotoPins) {
                // 📸 [프리미엄] 실제 사진 핀 생성 (기본 핀보다 우선순위 높음)
                val cacheKey = "photo_${rep.photoUri}_${latestRating}"
                markerBitmapCache[cacheKey] ?: run {
                    val miniPhoto = createMiniPhotoMarker(rep.photoUri)
                    val result = if (miniPhoto != null) {
                        if (latestRating > 0) drawRatingStarsToBitmap(miniPhoto, latestRating) else miniPhoto
                    } else {
                        val baseMarkerRes = R.drawable.ic_red_heart_marker
                        if (latestRating > 0) drawRatingStarsToBitmap(vectorToBitmap(baseMarkerRes), latestRating) else vectorToBitmap(baseMarkerRes)
                    }
                    markerBitmapCache[cacheKey] = result
                    result
                }
            } else {
                // 기본 핀
                val cacheKey = "default_${latestRating}"
                markerBitmapCache[cacheKey] ?: run {
                    val baseMarkerRes = R.drawable.ic_red_heart_marker 
                    val result = if (latestRating > 0) drawRatingStarsToBitmap(vectorToBitmap(baseMarkerRes), latestRating) else vectorToBitmap(baseMarkerRes)
                    markerBitmapCache[cacheKey] = result
                    result
                }
            }
            
            val styles = LabelStyles.from(LabelStyle.from(finalMarkerBitmap).setAnchorPoint(0.5f, 1.0f))
            val pos = LatLng.from(rep.lat, rep.lng)

            layer?.addLabel(
                LabelOptions.from(pos)
                    .setStyles(styles)
                    .setTag(normAddr) 
            )
        }
        
        // 카메라 이동 (가장 최근 촬영지)
        if (memories.isNotEmpty()) {
            val lastPos = LatLng.from(memories[0].lat, memories[0].lng)
            map.moveCamera(CameraUpdateFactory.newCenterPosition(lastPos, 12))
        }
    }

    // 🎨 [코부장 전용 도구] 핀 위에 앙증맞은 골드 별 배지를 그립니다.
    private fun drawRatingStarsToBitmap(baseBitmap: Bitmap, rating: Int): Bitmap {
        val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val starSize = 18f 
        val starPadding = 2f
        
        // 별 아이콘을 핀 상단 중앙에 배치
        val totalWidth = (starSize * rating) + (starPadding * (rating - 1))
        val startX = (mutableBitmap.width - totalWidth) / 2f
        val startY = 5f 
        
        // 🌑 [영자 감성 필살기] 입체 그림자 페인트 (약간 번지는 검은색/다크브라운)
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000") // 반투명 블랙 그림자
            style = android.graphics.Paint.Style.FILL
            // 쉐도우 레이어 효과 (고급 입체감)
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#88000000"))
        }
        
        // ⚪ [가독성 보조] 화이트 외곽선 (그림자와 별 사이 분리)
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        
        // 🌟 [메인] 골드 채우기
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700") 
            style = android.graphics.Paint.Style.FILL
        }
        
        // 🌟 레이어별로 덧그리기 (그림자 -> 외곽선 -> 채우기)
        for (i in 0 until rating) {
            val cx = startX + (starSize + starPadding) * i + starSize / 2f
            val cy = startY + starSize / 2f
            
            // 1. 입체 그림자 (약간 아래쪽으로 오프셋)
            drawSmallStar(canvas, cx + 1f, cy + 1f, starSize / 2f, shadowPaint)
            // 2. 화이트 테두리
            drawSmallStar(canvas, cx, cy, starSize / 2f, strokePaint)
            // 3. 골드 별 채우기
            drawSmallStar(canvas, cx, cy, starSize / 2f, fillPaint)
        }
        
        return mutableBitmap
    }

    private fun drawSmallStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: android.graphics.Paint) {
        val path = android.graphics.Path()
        val innerRadius = radius * 0.4f
        for (i in 0 until 10) {
            val angle = Math.toRadians((i * 36 - 90).toDouble())
            val r = if (i % 2 == 0) radius else innerRadius
            val x = cx + r * Math.cos(angle).toFloat()
            val y = cy + r * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun showMemoryCardDialog(groupItems: List<Memory>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_memory_card, null)
        val pager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.dialog_card_pager)
        val indicator = view.findViewById<TextView>(R.id.text_page_indicator)
        val btnDelete = view.findViewById<TextView>(R.id.btn_delete_memory)
        val btnShare = view.findViewById<View>(R.id.btn_share_image)
        val btnGetDirections = view.findViewById<View>(R.id.btn_get_directions)
        val btnOpenRoadview = view.findViewById<View>(R.id.btn_open_roadview)
        var currentPosition = 0

        // 🛠️ 가변 리스트로 관리하여 삭제 시 즉각 반영되도록 함
        val mutableGroup = groupItems.toMutableList()
        val adapter = MemoryPagerAdapter(mutableGroup)
        pager.adapter = adapter

        btnShare.setOnClickListener {
            if (currentPosition < 0 || currentPosition >= mutableGroup.size) return@setOnClickListener
            val target = mutableGroup[currentPosition]
            shareImage(Uri.parse(target.photoUri))
        }

        btnGetDirections.setOnClickListener {
            if (currentPosition < 0 || currentPosition >= mutableGroup.size) return@setOnClickListener
            val target = mutableGroup[currentPosition]
            
            // 🛣️ [새로운 페이지] 길찾기 전용 액티비티 실행
            if (target.lat != 0.0 && target.lng != 0.0) {
                Log.d("MAP_NAV", "Starting navigation to: ${target.lat}, ${target.lng}")
                val intent = Intent(this, DirectionsActivity::class.java).apply {
                    putExtra("lat", target.lat)
                    putExtra("lng", target.lng)
                    putExtra("address", target.address)
                }
                startActivity(intent)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "좌표 정보가 없는 추억입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenRoadview?.setOnClickListener {
            if (currentPosition < 0 || currentPosition >= mutableGroup.size) return@setOnClickListener
            val target = mutableGroup[currentPosition]
            
            if (target.lat != 0.0 && target.lng != 0.0) {
                // 🏙️ [정밀 교정] 좌표 소수점 자릿수를 6자리로 제한하여 카카오맵 앱 호환성 극대화
                val formattedLat = String.format(java.util.Locale.US, "%.6f", target.lat)
                val formattedLng = String.format(java.util.Locale.US, "%.6f", target.lng)
                
                val appUrl = "kakaomap://roadview?p=$formattedLat,$formattedLng"
                val marketUrl = "market://details?id=net.daum.android.map"
                val webMarketUrl = "https://map.kakao.com/link/roadview/$formattedLat,$formattedLng"

                try {
                    // 🚀 1순위: 카카오맵 앱으로 로드뷰 실행
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    // 📥 2순위: 앱 미설치 시 안내 문구 후 마켓으로 이동
                    Toast.makeText(this, "카카오맵 앱이 설치되어 있지 않아 설치 페이지로 이동합니다.", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl)))
                    } catch (e2: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webMarketUrl)))
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "좌표 정보가 없는 추억입니다.", Toast.LENGTH_SHORT).show()
            }
        }
        
        fun updateIndicator() {
            if (mutableGroup.size > 1) {
                indicator.visibility = View.VISIBLE
                indicator.text = "${pager.currentItem + 1} / ${mutableGroup.size} 장의 추억"
            } else {
                indicator.visibility = View.GONE
            }
        }
        
        updateIndicator()
        
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateIndicator()
            }
        })
        
        btnDelete.setOnClickListener {
            if (currentPosition < 0 || currentPosition >= mutableGroup.size) return@setOnClickListener
            
            val memoryToDelete = mutableGroup[currentPosition]
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("추억 삭제")
                .setMessage("이 추억을 정말 지우시겠습니까?\n한 번 삭제하면 되돌릴 수 없습니다.")
                .setPositiveButton("삭제") { _, _ ->
                    // 1️⃣ DB에서 삭제
                    val success = dbHelper.deleteMemory(memoryToDelete.id)
                    if (success) {
                        // 2️⃣ MediaStore에서도 삭제 시도 (갤러리 이미지 삭제)
                        try {
                            val uri = android.net.Uri.parse(memoryToDelete.photoUri)
                            if (uri.scheme == "content") {
                                // 📱 안드로이드 11(R) 이상: createDeleteRequest 사용 권장
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                                    deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } else {
                                    // 안드로이드 10 이하 또는 하위 호환
                                    try {
                                        contentResolver.delete(uri, null, null)
                                    } catch (securityException: Exception) {
                                        // 안드로이드 10에서 RecoverableSecurityException 발생 시 처리
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                                            val intentSender = securityException.userAction.actionIntent.intentSender
                                            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                                        } else {
                                            throw securityException
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DELETE", "MediaStore 삭제 실패: ${e.message}")
                            Toast.makeText(this, "갤러리 삭제 중 문제가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                        Toast.makeText(this, "추억이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        // 3️⃣ 데이터 리스트 갱신
                        mutableGroup.removeAt(currentPosition)
                        memories = dbHelper.getAllMemories() // 전체 리스트 갱신

                        if (mutableGroup.isEmpty()) {
                            dialog.dismiss()
                            showMemoriesOnMap()
                        } else {
                            // 🔥 어댑터에 데이터 변경 알림 (전체 교체보다 안전)
                            adapter.notifyItemRemoved(currentPosition)
                            
                            // 삭제 후 위치 조정 및 인디케이터 갱신
                            // notifyItemRemoved 호출 후 딜레이를 주어 안정적으로 갱신
                            pager.post {
                                updateIndicator()
                                showMemoriesOnMap()
                            }
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
        
        dialog.setContentView(view)
        
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        
        dialog.show()
    }

    inner class MemoryPagerAdapter(private val items: List<Memory>) : androidx.recyclerview.widget.RecyclerView.Adapter<MemoryPagerAdapter.MemoryViewHolder>() {
        
        inner class MemoryViewHolder(view: View, val imgView: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            fun bind(memory: Memory) {
                try {
                    // Use Glide for safe mapping image loading if possible, or setImageURI
                    imgView.setImageURI(Uri.parse(memory.photoUri))
                } catch (e: Exception) {
                    imgView.setImageResource(R.drawable.bg_invitation)
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MemoryViewHolder {
            val context = parent.context
            
            val cardView = androidx.cardview.widget.CardView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                radius = 16f * resources.displayMetrics.density
                cardElevation = 8f * resources.displayMetrics.density
                setCardBackgroundColor(Color.WHITE)
                useCompatPadding = true
            }
            
            val imageView = ImageView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            
            cardView.addView(imageView)
            
            val scrollView = androidx.core.widget.NestedScrollView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(cardView)
            }
            
            return MemoryViewHolder(scrollView, imageView)
        }

        override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
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

    private fun checkPermissionAndSyncMemories() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            syncMemoriesFromGallery()
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 2001)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                syncMemoriesFromGallery()
            } else {
                Toast.makeText(this, "추억을 복원하려면 로컬 저장소 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncMemoriesFromGallery() {
        // 1️⃣ 현재 DB 상태 파악
        val currentDbList = dbHelper.getAllMemories()
        
        // 🛡️ [지문 1] 고유 URI 리스트
        val existingUris = currentDbList.map { it.photoUri }.toMutableSet()

        var restoredCount = 0
        val projection = arrayOf(
            MediaStore.Images.Media._ID, 
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%NewDateMapDiary%")

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAddedSecs = cursor.getLong(dateColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = contentUri.toString()

                    // [체크 A] URI가 이미 있으면 패스
                    if (existingUris.contains(uriString)) continue

                    try {
                        contentResolver.openInputStream(contentUri)?.use { input ->
                            val exif = androidx.exifinterface.media.ExifInterface(input)
                            val jsonMeta = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION)

                            if (!jsonMeta.isNullOrEmpty()) {
                                val jsonObj = JSONObject(jsonMeta)
                                val lat = jsonObj.optDouble("lat", 0.0)
                                val lng = jsonObj.optDouble("lng", 0.0)
                                val rawAddr = jsonObj.optString("addr", "")
                                val addr = try { java.net.URLDecoder.decode(rawAddr, "UTF-8") } catch (e: Exception) { rawAddr }
                                val dateMillis = dateAddedSecs * 1000L
                                
                                val newMemory = Memory(
                                    photoUri = uriString,
                                    address = addr.trim(),
                                    lat = lat,
                                    lng = lng,
                                    date = dateMillis
                                )
                                // 📍 [핵심] DB에 즉시 영구 저장. 중복 핀 로직은 지도 표출단에서 주소(normalizeAddress) 기준으로만 하나로 묶임
                                dbHelper.insertMemory(newMemory)
                                
                                existingUris.add(uriString)
                                restoredCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SYNC", "EXIF read error: $uriString")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC", "Query error", e)
        }

        // 3️⃣ 갱신 결과 반영
        if (restoredCount > 0) {
            Toast.makeText(this, "${restoredCount}개의 추억이 종갓집 DB에 합병되었습니다!", Toast.LENGTH_LONG).show()
            // 무조건 최신 DB에서 다시 불러오기
            memories = dbHelper.getAllMemories()
            showMemoriesOnMap()
        } else {
            Toast.makeText(this, "모든 추억이 이미 안전하게 보관 중입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
        
        // 🧹 [고스트 메모리 청소 로직] 외부 갤러리 앱에서 삭제된 사진 감지 및 DB 동기화
        var ghostsRemoved = false
        val currentDbList = dbHelper.getAllMemories()
        currentDbList.forEach { dbMemory ->
            var fileExists = false
            try {
                contentResolver.openAssetFileDescriptor(Uri.parse(dbMemory.photoUri), "r")?.use {
                    fileExists = true
                }
            } catch (e: Exception) {
                fileExists = false
            }
            if (!fileExists) {
                dbHelper.deleteMemory(dbMemory.id)
                ghostsRemoved = true
                Log.d("GHOST_CLEANUP", "Deleted ghost memory from DB: ${dbMemory.photoUri}")
            }
        }
        
        // 🔥 [대통함] 화면 복귀 시 무조건 DB에서 최신 데이터 로드
        // 다른 화면(메인/편집기)에서 추가된 데이터나 삭제된 고스트 파일이 즉각 반영됨
        memories = dbHelper.getAllMemories()
        if (kakaoMap != null) showMemoriesOnMap()
    }

    private fun updateAirplanePosition(pos: LatLng, rotation: Float = 0f, scaleMultiplier: Float = 1.0f) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = labelManager.getLayer("airplane_layer") ?: labelManager.addLayer(LabelLayerOptions.from("airplane_layer"))
        
        // 🚀 [메모리 세이프] 미리 스케일링된 캐시 비트맵 사용
        val baseBitmap = cachedAirplaneBitmap ?: return

        if (airplaneLabel == null) {
            val style = LabelStyle.from(baseBitmap).setAnchorPoint(0.5f, 0.5f)
            airplaneLabel = layer?.addLabel(LabelOptions.from(pos).setStyles(LabelStyles.from(style)))
        }

        airplaneLabel?.moveTo(pos)
        
        // ✨ [스페이스 점프 스케일링] 크기 조절과 회전을 동시에 적용
        val matrix = android.graphics.Matrix().apply {
            postScale(scaleMultiplier, scaleMultiplier)
            postRotate(rotation)
        }
        
        // 스케일 변화 시 원본보다 작아지면 안되므로 최소 1픽셀 방어 로직
        val targetWidth = Math.max(1, (baseBitmap.width * scaleMultiplier).toInt())
        val targetHeight = Math.max(1, (baseBitmap.height * scaleMultiplier).toInt())
        
        // 크기가 달라지므로 createBitmap에 원본 사이즈를 넘기고 matrix로 변환해야 함
        val transformedBitmap = Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
        
        val style = LabelStyle.from(transformedBitmap).setAnchorPoint(0.5f, 0.5f)
        airplaneLabel?.changeStyles(LabelStyles.from(style))
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    private fun startPathAnimation() {
        val map = kakaoMap ?: return
        val routeLineManager = map.routeLineManager ?: return
        val labelManager = map.labelManager ?: return
        
        // 1️⃣ 시간 순 정렬
        val originalSorted = memories.sortedBy { it.date }
        if (originalSorted.size < 2) {
            Toast.makeText(this, "경로를 그리려면 최소 2개 이상의 추억이 필요합니다!", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚀 [코다리 부장의 테스트 기믹] 타지역 비행 테스트를 위해 상위 최대 4개 카드의 좌표를 강제 변환
        val tempTestList = originalSorted.toMutableList()
        val testOffsets = listOf(
            Pair(0.0, 0.0),      // 원본 (예: 광명)
            Pair(-0.4, -0.4),    // 약 40km (충남 당진 쯤)
            Pair(-0.8, -0.1),    // 약 80km (천안/대전 쯤)
            Pair(-2.0, +1.0)     // 약 250km (경남 부산 쯤)
        )
        for (i in 0 until Math.min(tempTestList.size, 4)) {
            val offset = testOffsets[i]
            val m = tempTestList[i]
            tempTestList[i] = m.copy(
                lat = m.lat + offset.first,
                lng = m.lng + offset.second,
                address = "[테스트 비행 목적지 ${i+1}]"
            )
        }
        
        // 데이터 준비
        sortedMemoriesForPath = tempTestList

        // 초기화
        isPathPlaying = true
        isMovingToPoint = false
        currentPathIndex = 0
        fullJourneyPoints = listOf()
        memoryStopIndexes.clear()
        flightAnimator?.cancel()
        
        routeLineManager.layer.removeAll()
        labelManager.getLayer("popup_layer")?.removeAll()
        labelManager.getLayer("airplane_layer")?.removeAll()
        airplaneLabel = null
        currentRouteLine = null
        isRouteReady = false

        // 💎 프리미엄 설정 로드
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val vehicleType = prefs.getString(KEY_VEHICLE, "jet")
        val vehicleRes = when(vehicleType) {
            "balloon" -> R.drawable.img_premium_balloon
            "rocket" -> R.drawable.img_premium_rocket
            "ufo" -> R.drawable.img_premium_ufo
            "henry" -> R.drawable.img_premium_henry
            "paper_airplane" -> R.drawable.img_premium_paperairplane
            "paper_hak" -> R.drawable.img_premium_paperhak
            else -> R.drawable.img_premium_jet
        }

        // 🎨 [탈것 아이콘 준비] 사장님이 준비해주신 프리미엄 이미지(img_premium_*)는 이미 완벽한 누끼 상태이므로, 비율을 유지하며 고해상도로 로드합니다.
        val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val original = android.graphics.BitmapFactory.decodeResource(resources, vehicleRes, options) ?: vectorToBitmap(vehicleRes)
        
        // 📏 [비율 유지 리사이징] 뚱뚱해지지 않도록 원본 비율을 철저히 사수합니다.
        val maxSize = 110
        val ratio = original.width.toFloat() / original.height.toFloat()
        val (finalW, finalH) = if (ratio > 1f) {
            maxSize to (maxSize / ratio).toInt()
        } else {
            (maxSize * ratio).toInt() to maxSize
        }
        cachedAirplaneBitmap = Bitmap.createScaledBitmap(original, Math.max(1, finalW), Math.max(1, finalH), true)

        // 🚀 [추억 여행의 시작] 첫 번째 사진을 먼저 보여줍니다. (클래식 모드 복구)
        showPhotoPopup() 
        
        Toast.makeText(this, "우리의 소중한 추억 여행을 준비 중입니다... ✈️", Toast.LENGTH_SHORT).show()
    }



    private fun fetchFullRoute() {
        if (sortedMemoriesForPath.size < 2) return
        
        if (sortedMemoriesForPath.size > 17) {
            startDirectFlight()
            return
        }

        thread {
            try {
                val origin = sortedMemoriesForPath.first()
                val destination = sortedMemoriesForPath.last()
                
                val waypointsStr = StringBuilder()
                for (i in 1 until sortedMemoriesForPath.size - 1) {
                    val m = sortedMemoriesForPath[i]
                    if (waypointsStr.isNotEmpty()) waypointsStr.append("|")
                    waypointsStr.append("${m.lng},${m.lat}")
                }
                
                var urlString = "https://apis-navi.kakaomobility.com/v1/directions?origin=${origin.lng},${origin.lat}&destination=${destination.lng},${destination.lat}&priority=RECOMMEND"
                if (waypointsStr.isNotEmpty()) {
                    urlString += "&waypoints=$waypointsStr"
                }
                
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_KEY")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(json)
                    val routes = root.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        if (route.getInt("result_code") == 0) {
                            val sections = route.getJSONArray("sections")
                            val allPoints = mutableListOf<LatLng>()
                            val stops = mutableListOf<Int>()
                            
                            stops.add(0)
                            for (i in 0 until sections.length()) {
                                val roads = sections.getJSONObject(i).getJSONArray("roads")
                                for (j in 0 until roads.length()) {
                                    val vArray = roads.getJSONObject(j).getJSONArray("vertexes")
                                    for (k in 0 until vArray.length() step 2) {
                                        val point = LatLng.from(vArray.getDouble(k + 1), vArray.getDouble(k))
                                        if (allPoints.isEmpty() || allPoints.last() != point) allPoints.add(point)
                                    }
                                }
                                stops.add(allPoints.size - 1)
                            }
                            
                            runOnUiThread {
                                val map = kakaoMap ?: return@runOnUiThread
                                val routeLineManager = map.routeLineManager ?: return@runOnUiThread
                                
                                fullJourneyPoints = allPoints
                                memoryStopIndexes = stops
                                
                                // 🛣️ [프리미엄 세팅] 전체 경로를 연회색으로 전경 처리 (선을 6f로 얇게 조정)
                                val baseStyle = RouteLineStyle.from(6f, Color.parseColor("#E0E0E0"), 1.5f, Color.WHITE)
                                val baseSegment = RouteLineSegment.from(allPoints, RouteLineStyles.from(baseStyle))
                                routeLineManager.layer.addRouteLine(RouteLineOptions.from(baseSegment))
                                
                                isRouteReady = true
                                Log.d("NAV_PATH", "Route is ready for takeoff!")
                            }
                            return@thread
                        }
                    }
                }
                // API 응답 실패 시 또는 결과 코드가 정상이 아닐 시
                Log.e("NAV_PATH", "API Failure: ${conn.responseCode}")
                runOnUiThread { startDirectFlight() }
            } catch (e: Exception) {
                Log.e("NAV_PATH", "Error: ${e.message}")
                runOnUiThread { startDirectFlight() }
            }
        }
    }

    private fun startDirectFlight() {
        // 실제 도로를 찾기 힘들 경우 (너무 멀거나, 좌표가 없거나), 장소들을 직선으로 연결하여 애니메이션 수행
        
        fullJourneyPoints = sortedMemoriesForPath.map { LatLng.from(it.lat, it.lng) }
        
        // 정지 인덱스는 각 메모리의 인덱스 그대로 사용 (0, 1, 2...)
        val stops = mutableListOf<Int>()
        for (i in 0 until fullJourneyPoints.size) {
            stops.add(i)
        }
        memoryStopIndexes = stops
        
        // 🛣️ [프리미엄 세팅] 직선 경로라도 연회색으로 가이드라인 제공 (선을 6f로 얇게 조정)
        val map = kakaoMap
        val routeLineManager = map?.routeLineManager
        if (routeLineManager != null) {
            val baseStyle = RouteLineStyle.from(6f, Color.parseColor("#E0E0E0"), 1.5f, Color.WHITE)
            val baseSegment = RouteLineSegment.from(fullJourneyPoints, RouteLineStyles.from(baseStyle))
            routeLineManager.layer.addRouteLine(RouteLineOptions.from(baseSegment))
        }

        isRouteReady = true // 🚀 비행 준비 완료 신호!
    }

    private fun playNextFlight() {
        val map = kakaoMap ?: return
        val routeLineManager = map.routeLineManager ?: return
        
        // 비행 준비가 안 됐거나 이미 종료된 경우 중단
        if (!isPathPlaying || !isRouteReady) {
            Log.d("FLY", "Not ready: playing=$isPathPlaying, ready=$isRouteReady")
            return
        }

        // 🌟 [피날레 체크]
        if (currentPathIndex >= sortedMemoriesForPath.size - 1) {
            isPathPlaying = false
            airplaneLabel?.remove()
            airplaneLabel = null
            Toast.makeText(this, "우리의 모든 추억 조각을 찾아보았습니다! ✨", Toast.LENGTH_LONG).show()
            return
        }
        // 🎨 [프리미엄 세팅] 저장된 선 색상 테마 로드
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pathColorHex = prefs.getString(KEY_COLOR, "#4D7CFF") ?: "#4D7CFF"
        val pathColor = Color.parseColor(pathColorHex)

        val startIndex = if (currentPathIndex < memoryStopIndexes.size) memoryStopIndexes[currentPathIndex] else 0
        val endIndex = if (currentPathIndex + 1 < memoryStopIndexes.size) memoryStopIndexes[currentPathIndex + 1] else fullJourneyPoints.size - 1
        
        val segmentVertexes = fullJourneyPoints.subList(startIndex, endIndex + 1)
        
        if (segmentVertexes.size < 2) {
            currentPathIndex++
            showPhotoPopup()
            return
        }
        
        isMovingToPoint = true
        map.labelManager?.getLayer("popup_layer")?.removeAll()

        flightAnimator?.cancel()
        
        // 거리 맵 대폭 최적화
        val distanceMap = DoubleArray(segmentVertexes.size)
        var accDist = 0.0
        distanceMap[0] = 0.0
        for (i in 0 until segmentVertexes.size - 1) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                segmentVertexes[i].latitude, segmentVertexes[i].longitude,
                segmentVertexes[i+1].latitude, segmentVertexes[i+1].longitude, results
            )
            accDist += results[0]
            distanceMap[i+1] = accDist
        }

        // 🔥 [코다리 다이내믹 줌] 대표님 극약처방: "타지역 이동이면 속력 구분 없이 무조건 광속 터보!!" 🚀
        var durationMultiplier = 0.15 // 근거리(동네)용 속도
        if (accDist > 10000) {
            // 10km 이상(타지역 이동)이면 기존 "3번째 지역(초장거리)" 갈 때 쓰던 극단적 최고속도(0.02)를 일괄 적용!
            durationMultiplier = 0.02 
        }

        // 최소 0.4초 ~ 최대 2초 사이로 비행 시간 제한 하향 (거의 0.4초 ~ 1초 대에 타지역 주파!)
        val durationMs = Math.max(400L, (accDist * durationMultiplier).toLong()).coerceAtMost(2000L)

        // 🛫 [1단계: 제자리 이륙]
        val baseZoom = 14.0
        val zoomOutIntensity = Math.min(7.0, (accDist / 40000.0) * 7.0) 
        val targetZoom = baseZoom - zoomOutIntensity
        
        val scaleIntensity = Math.min(0.8f, (accDist / 40000f).toFloat() * 0.8f) 
        val targetScale = 1.0f + scaleIntensity
        
        val firstPos = segmentVertexes.first()
        val lastPos = segmentVertexes.last()
        val initialBearing = if (segmentVertexes.size > 1) calculateBearing(firstPos, segmentVertexes[1]) else 0f
        val finalBearing = if (segmentVertexes.size > 1) calculateBearing(segmentVertexes[segmentVertexes.size - 2], lastPos) else 0f

        val takeoffAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L // 0.8초 -> 0.4초 초고속 이륙
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val currentZoom = baseZoom - (zoomOutIntensity * fraction)
                val currentScale = 1.0f + (scaleIntensity * fraction)
                
                updateAirplanePosition(firstPos, initialBearing, currentScale)
                map.moveCamera(CameraUpdateFactory.newCenterPosition(firstPos, currentZoom.toInt()))
            }
        }

        // 🚀 [2단계: 고도 유지 순항]
        val cruiseAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = android.view.animation.LinearInterpolator()
            var lastDrawTime = 0L

            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val targetDist = accDist * fraction
                
                var idx = 0
                while (idx < distanceMap.size - 1 && distanceMap[idx + 1] < targetDist) idx++
                
                val p1 = segmentVertexes[idx]
                val p2 = segmentVertexes[Math.min(idx + 1, segmentVertexes.size - 1)]
                val segDist = distanceMap[Math.min(idx + 1, distanceMap.size - 1)] - distanceMap[idx]
                val segFraction = if (segDist > 0) (targetDist - distanceMap[idx]) / segDist else 0.0
                
                val currentPos = LatLng.from(
                    p1.latitude + (p2.latitude - p1.latitude) * segFraction,
                    p1.longitude + (p2.longitude - p1.longitude) * segFraction
                )
                
                val currentBearing = calculateBearing(p1, p2)
                updateAirplanePosition(currentPos, currentBearing, targetScale)
                
                val now = System.currentTimeMillis()
                if (now - lastDrawTime > 33 || fraction >= 1f) {
                    val tailPoints = segmentVertexes.subList(0, idx + 1).toMutableList()
                    tailPoints.add(currentPos)
                    
                    val pathStyle = RouteLineStyle.from(7f, pathColor, 2f, Color.WHITE)
                    val newLine = routeLineManager.layer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(tailPoints, RouteLineStyles.from(pathStyle))))
                    
                    currentRouteLine?.remove()
                    currentRouteLine = newLine
                    lastDrawTime = now
                }
                
                map.moveCamera(CameraUpdateFactory.newCenterPosition(currentPos, targetZoom.toInt()))
            }
        }

        // 🛬 [3단계: 제자리 착륙]
        val landingAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L // 0.8초 -> 0.4초 초고속 착륙
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val currentZoom = targetZoom + (zoomOutIntensity * fraction)
                val currentScale = targetScale - (scaleIntensity * fraction)
                
                updateAirplanePosition(lastPos, finalBearing, currentScale)
                map.moveCamera(CameraUpdateFactory.newCenterPosition(lastPos, currentZoom.toInt()))
            }
        }

        // 🌟 코다리의 3단 애니메이션 합체
        flightAnimator = android.animation.AnimatorSet().apply {
            playSequentially(takeoffAnimator, cruiseAnimator, landingAnimator)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isPathPlaying) {
                        isMovingToPoint = false
                        
                        // 현재 비행 구간을 고정 레이어에 확정 (사용자 테마 색상 적용)
                        val pathStyle = RouteLineStyle.from(7f, pathColor, 2f, Color.WHITE)
                        routeLineManager.layer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(segmentVertexes, RouteLineStyles.from(pathStyle))))
                        currentRouteLine?.remove()
                        currentRouteLine = null

                        currentPathIndex++ 
                        showPhotoPopup()
                    }
                }
            })
            start()
        }
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        if (start == end) return 0f
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)
        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }


    private fun showPhotoPopup() {
        val map = kakaoMap ?: return
        if (currentPathIndex >= sortedMemoriesForPath.size) return
        
        val memory = sortedMemoriesForPath[currentPathIndex]
        val pos = LatLng.from(memory.lat, memory.lng)
        
        if (currentPathIndex == 0) fetchFullRoute()

        thread {
            try {
                // [프리미엄 딜레이] 사용자가 준비될 수 있게 사진 로딩 전 약간의 여유를 둡니다.
                val photoBitmap = createPhotoMarkerBitmap(memory.photoUri) ?: vectorToBitmap(R.drawable.bg_invitation)
                
                // 경로 데이터가 올 때까지 최대 5초간 대기 (Race Condition 방지)
                if (currentPathIndex == 0) {
                    var waitCount = 0
                    while (!isRouteReady && waitCount < 50) { 
                        Thread.sleep(100)
                        waitCount++
                    }
                }

                runOnUiThread {
                    val labelManager = map.labelManager ?: return@runOnUiThread
                    val layer = labelManager.getLayer("popup_layer") ?: labelManager.addLayer(LabelLayerOptions.from("popup_layer"))
                    
                    val styles = LabelStyles.from(LabelStyle.from(photoBitmap).setAnchorPoint(0.5f, 1.1f))
                    layer?.addLabel(LabelOptions.from(pos).setStyles(styles))
                    
                    // 🕒 [추억 감상 타임] 사진을 충분히 보실 수 있도록 2.5초간 머무릅니다. (요청에 따른 연장)
                    mapView.postDelayed({
                        if (currentPathIndex < sortedMemoriesForPath.size - 1) {
                            playNextFlight()
                        } else {
                            // 피날레
                            isPathPlaying = false
                            airplaneLabel?.remove()
                            airplaneLabel = null
                            Toast.makeText(this, "모든 추억을 완벽하게 감상해 보았습니다! ✨", Toast.LENGTH_LONG).show()
                        }
                    }, 2500) 
                }
            } catch (e: Exception) {
                Log.e("POPUP", e.message ?: "")
                // 에러 발생 시에도 흐름이 끊기지 않게 조치
                runOnUiThread { 
                    if (currentPathIndex < sortedMemoriesForPath.size - 1) playNextFlight()
                }
            }
        }
    }

    private fun createPhotoMarkerBitmap(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = contentResolver.openInputStream(uri)
            val original = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close() ?: return null

            // 📏 원본 비율 유지 리사이징
            val maxSide = 400
            val (targetW, targetH) = if (original.width > original.height) {
                val ratio = original.height.toFloat() / original.width.toFloat()
                Pair(maxSide, (maxSide * ratio).toInt())
            } else {
                val ratio = original.width.toFloat() / original.height.toFloat()
                Pair((maxSide * ratio).toInt(), maxSide)
            }
            
            val scaled = Bitmap.createScaledBitmap(original, Math.max(1, targetW), Math.max(1, targetH), true)
            
            // 프리미엄 화이트 액자 효과
            val borderSize = 16
            val output = Bitmap.createBitmap(scaled.width + borderSize * 2, scaled.height + borderSize * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            
            // 깔끔한 둥근 모서리 배경
            paint.color = Color.WHITE
            canvas.drawRoundRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), 20f, 20f, paint)
            
            // 사진 그리기
            canvas.drawBitmap(scaled, borderSize.toFloat(), borderSize.toFloat(), null)
            
            output
        } catch (e: Exception) {
            null
        }
    }

    private fun shareImage(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // 🖼️ [프리미엄 한 마디] 갤러리 상세 화면과 공유 문구 및 경험 통일
            startActivity(Intent.createChooser(shareIntent, "HereWithYou 추억 공유하기"))
        } catch (e: Exception) {
            android.util.Log.e("ShareError", "공유 중 에러 발생: ${e.message}")
            Toast.makeText(this, "공유를 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMapSettingsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_map_premium_settings, null)
        
        // 🛠️ [코부장 정밀 점검] 라디오 버튼들을 수동 관리하여 단일 선택 보장
        val rbJet = view.findViewById<android.widget.RadioButton>(R.id.rb_jet)
        val rbBalloon = view.findViewById<android.widget.RadioButton>(R.id.rb_balloon)
        val rbRocket = view.findViewById<android.widget.RadioButton>(R.id.rb_rocket)
        val rbUfo = view.findViewById<android.widget.RadioButton>(R.id.rb_ufo)
        val rbHenry = view.findViewById<android.widget.RadioButton>(R.id.rb_henry)
        val rbPaperAirplane = view.findViewById<android.widget.RadioButton>(R.id.rb_paper_airplane)
        val rbPaperHak = view.findViewById<android.widget.RadioButton>(R.id.rb_paper_hak)
        val vehicles = listOf(rbJet, rbBalloon, rbRocket, rbUfo, rbHenry, rbPaperAirplane, rbPaperHak)

        val rbGray = view.findViewById<android.widget.RadioButton>(R.id.rb_color_gray)
        val rbGold = view.findViewById<android.widget.RadioButton>(R.id.rb_color_gold)
        val rbPink = view.findViewById<android.widget.RadioButton>(R.id.rb_color_pink)
        val rbBlue = view.findViewById<android.widget.RadioButton>(R.id.rb_color_blue)
        val colors = listOf(rbGray, rbGold, rbPink, rbBlue)

        // 차량 선택 리스너 (이미지 영역 포함 클릭)
        view.findViewById<View>(R.id.container_jet).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbJet) } }
        view.findViewById<View>(R.id.container_balloon).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbBalloon) } }
        view.findViewById<View>(R.id.container_rocket).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbRocket) } }
        view.findViewById<View>(R.id.container_ufo).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbUfo) } }
        view.findViewById<View>(R.id.container_henry).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbHenry) } }
        view.findViewById<View>(R.id.container_paper_airplane).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbPaperAirplane) } }
        view.findViewById<View>(R.id.container_paper_hak).setOnClickListener { vehicles.forEach { it.isChecked = (it == rbPaperHak) } }

        // 색상 선택 리스너 (순수 라디오 처리)
        colors.forEach { targetRb ->
            targetRb.setOnClickListener {
                colors.forEach { it.isChecked = (it == targetRb) }
            }
        }

        val swPhotoPins = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_photo_markers)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save_settings)
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // 기존 값 세팅
        when(prefs.getString(KEY_VEHICLE, "jet")) {
            "balloon" -> rbBalloon.isChecked = true
            "rocket" -> rbRocket.isChecked = true
            "ufo" -> rbUfo.isChecked = true
            "henry" -> rbHenry.isChecked = true
            "paper_airplane" -> rbPaperAirplane.isChecked = true
            "paper_hak" -> rbPaperHak.isChecked = true
            else -> rbJet.isChecked = true
        }
        
        when(prefs.getString(KEY_COLOR, "#E0E0E0")) {
            "#FFD700" -> rbGold.isChecked = true
            "#FF9999" -> rbPink.isChecked = true
            "#87CEEB" -> rbBlue.isChecked = true
            else -> rbGray.isChecked = true
        }
        
        swPhotoPins.isChecked = prefs.getBoolean(KEY_PHOTO_PINS, false)
        
        btnSave.setOnClickListener {
            val editor = prefs.edit()
            
            val vehicle = when {
                rbBalloon.isChecked -> "balloon"
                rbRocket.isChecked -> "rocket"
                rbUfo.isChecked -> "ufo"
                rbHenry.isChecked -> "henry"
                rbPaperAirplane.isChecked -> "paper_airplane"
                rbPaperHak.isChecked -> "paper_hak"
                else -> "jet"
            }
            editor.putString(KEY_VEHICLE, vehicle)
            
            val color = when {
                rbGold.isChecked -> "#FFD700"
                rbPink.isChecked -> "#FF9999"
                rbBlue.isChecked -> "#87CEEB"
                else -> "#E0E0E0"
            }
            editor.putString(KEY_COLOR, color)
            editor.putBoolean(KEY_PHOTO_PINS, swPhotoPins.isChecked)
            
            editor.apply()
            Toast.makeText(this, "프리미엄 지도가 성공적으로 저장되었습니다! ✨", Toast.LENGTH_SHORT).show()
            
            markerBitmapCache.clear() // 🧹 설정 변경 시 캐시 초기화
            showMemoriesOnMap() // 즉시 핀 반영
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun createMiniPhotoMarker(uriString: String): Bitmap? {
        // ✨ [대표님 긴급 처방] 로딩 속도 개선을 위해 원본 전체가 아닌 사진 일부(중앙)를 원형으로 크게(85px) 잘라 가져옵니다.
        return try {
            val uri = Uri.parse(uriString)
            val options = android.graphics.BitmapFactory.Options()
            
            // 1. 메모리 절약을 위해 먼저 사이즈만 체크
            options.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { 
                android.graphics.BitmapFactory.decodeStream(it, null, options) 
            }
            
            // 2. 핀 크기에 맞춰 대폭 축소하여 디코딩 (전체 사진을 다 읽지 않음)
            val targetSize = 100
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false
            
            val original = contentResolver.openInputStream(uri)?.use { 
                android.graphics.BitmapFactory.decodeStream(it, null, options) 
            } ?: return null

            // 3. 중앙 영역을 정사각형으로 크롭
            val rawSize = Math.min(original.width, original.height)
            val x = (original.width - rawSize) / 2
            val y = (original.height - rawSize) / 2
            val cropped = Bitmap.createBitmap(original, x, y, rawSize, rawSize)
            val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)

            // 4. 원형으로 마스킹 (사장님 지시: 사각형이 아닌 둥근 형태)
            val circleBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvasCircle = Canvas(circleBitmap)
            val paintCircle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            canvasCircle.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, paintCircle)
            paintCircle.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvasCircle.drawBitmap(scaled, 0f, 0f, paintCircle)

            // 5. 프레임 비트맵 생성 (둥근 테두리 및 핀 꼬리)
            val output = Bitmap.createBitmap(targetSize + 20, targetSize + 40, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            // 원형 화이트 보더
            paint.color = Color.WHITE
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 7f
            canvas.drawCircle((targetSize + 20) / 2f, (targetSize + 20) / 2f, targetSize / 2f + 2f, paint)

            // 꼬리 삼각형 (원형 핀 끝부분 하단 중앙)
            paint.style = android.graphics.Paint.Style.FILL
            val path = android.graphics.Path()
            path.moveTo((targetSize + 20) / 2f - 14f, (targetSize + 20).toFloat() - 5f)
            path.lineTo((targetSize + 20) / 2f + 14f, (targetSize + 20).toFloat() - 5f)
            path.lineTo((targetSize + 20) / 2f, (targetSize + 36).toFloat())
            path.close()
            canvas.drawPath(path, paint)

            // 사진 그리기
            canvas.drawBitmap(circleBitmap, 10f, 10f, null)
            
            // 메모리 해제
            if (original != cropped) original.recycle()
            cropped.recycle()
            circleBitmap.recycle()
            
            output
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onDestroy() {
        super.onDestroy()
        flightAnimator?.cancel()
        cachedAirplaneBitmap?.recycle()
        cachedAirplaneBitmap = null
        mapView.finish()
    }
}
