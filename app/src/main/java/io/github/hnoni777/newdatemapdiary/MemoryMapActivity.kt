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
import com.kakao.vectormap.camera.CameraUpdateFactory
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

        val markerBitmap = vectorToBitmap(R.drawable.ic_red_heart_marker)
        val styles = LabelStyles.from(LabelStyle.from(markerBitmap).setAnchorPoint(0.5f, 1.0f))

        // 🏠 [대표님 지시] 주소 텍스트가 사실상 같으면 무조건 하나로 합침
        // normalizeAddress를 통해 공백/특수문자를 무시하고 글자만 같으면 그룹화합니다.
        val groups = memories.groupBy { normalizeAddress(it.address) }

        groups.forEach { (normAddr, group) ->
            // 그룹 중 가장 최근 데이터의 좌표에 핀 하나만 꽂음
            val rep = group.first()
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

    private fun showMemoryCardDialog(groupItems: List<Memory>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_memory_card, null)
        val pager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.dialog_card_pager)
        val indicator = view.findViewById<TextView>(R.id.text_page_indicator)
        val btnDelete = view.findViewById<TextView>(R.id.btn_delete_memory)
        val btnGetDirections = view.findViewById<View>(R.id.btn_get_directions)
        var currentPosition = 0

        // 🛠️ 가변 리스트로 관리하여 삭제 시 즉각 반영되도록 함
        val mutableGroup = groupItems.toMutableList()
        val adapter = MemoryPagerAdapter(mutableGroup)
        pager.adapter = adapter

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
        // 🔥 [대통함] 화면 복귀 시 무조건 DB에서 최신 데이터 로드
        // 다른 화면(메인/편집기)에서 추가된 데이터가 즉각 반영됨
        memories = dbHelper.getAllMemories()
        if (kakaoMap != null) showMemoriesOnMap()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.finish()
    }
}
