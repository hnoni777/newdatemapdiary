package io.github.hnoni777.newdatemapdiary

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
import org.json.JSONObject

class MemoryMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private lateinit var dbHelper: MemoryDatabaseHelper
    private var memories = listOf<Memory>()

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
                    val tagStr = label.tag as? String ?: ""
                    val group = memories.filter { 
                        val latStr = String.format("%.4f", it.lat)
                        val lngStr = String.format("%.4f", it.lng)
                        "$latStr,$lngStr" == tagStr
                    }
                    if (group.isNotEmpty()) {
                        showMemoryCardDialog(group)
                    }
                    true
                }
            }
        })
    }

    private fun showMemoriesOnMap() {
        val map = kakaoMap ?: return
        if (memories.isEmpty()) {
            Toast.makeText(this, "저장된 추억이 아직 없습니다. 카드를 저장해주세요!", Toast.LENGTH_SHORT).show()
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 10))
            return
        }

        val layerId = "memories_layer"
        var layer = map.labelManager?.getLayer(layerId)
        if (layer == null) {
            layer = map.labelManager?.addLayer(LabelLayerOptions.from(layerId))
        }
        layer?.removeAll()

        val markerBitmap = vectorToBitmap(R.drawable.ic_red_heart_marker)
        val styles = LabelStyles.from(
            LabelStyle.from(markerBitmap).setAnchorPoint(0.5f, 1.0f)
        )

        val boundsBuilder = com.kakao.vectormap.LatLngBounds.Builder()

        // 🎯 Group by rounded coordinates (4 decimal places = approx 11m) to merge jittery markers
        val groups = memories.groupBy { 
            val latStr = String.format("%.4f", it.lat)
            val lngStr = String.format("%.4f", it.lng)
            "$latStr,$lngStr"
        }

        groups.forEach { (coordKey, group) ->
            val rep = group.first()
            val pos = LatLng.from(rep.lat, rep.lng)
            boundsBuilder.include(pos)
            
            layer?.addLabel(
                LabelOptions.from(pos)
                    .setStyles(styles)
                    .setTag(coordKey) // Use the coordinate key as the tag
            )
        }

        if (memories.isNotEmpty()) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(memories[0].lat, memories[0].lng), 12))
        }
    }

    private fun showMemoryCardDialog(groupItems: List<Memory>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_memory_card, null)
        val pager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.dialog_card_pager)
        val indicator = view.findViewById<TextView>(R.id.text_page_indicator)
        val btnDelete = view.findViewById<TextView>(R.id.btn_delete_memory)
        var currentPosition = 0
        
        pager.adapter = MemoryPagerAdapter(groupItems)
        
        if (groupItems.size > 1) {
            indicator.visibility = View.VISIBLE
            indicator.text = "1 / ${groupItems.size} 장의 추억"
        } else {
            indicator.visibility = View.GONE
        }
        
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                if (groupItems.size > 1) {
                    indicator.text = "${position + 1} / ${groupItems.size} 장의 추억"
                }
            }
        })
        
        btnDelete.setOnClickListener {
            val memoryToDelete = groupItems[currentPosition]
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("추억 삭제")
                .setMessage("이 추억을 정말 지우시겠습니까?\n한 번 삭제하면 되돌릴 수 없습니다.")
                .setPositiveButton("삭제") { _, _ ->
                    val success = dbHelper.deleteMemory(memoryToDelete.id)
                    if (success) {
                        Toast.makeText(this, "추억이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        
                        // Refresh map
                        memories = dbHelper.getAllMemories()
                        showMemoriesOnMap()
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
        var restoredCount = 0
        val existingUris = memories.map { it.photoUri }.toSet()

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
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAddedSecs = cursor.getLong(dateColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = contentUri.toString()

                    if (!existingUris.contains(uriString)) {
                        // DB에 없는 갤러리 이미지 발견! EXIF 정보 스캔 시도
                        try {
                            contentResolver.openInputStream(contentUri)?.use { input ->
                                val exif = androidx.exifinterface.media.ExifInterface(input)
                                val jsonMeta = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION)

                                if (!jsonMeta.isNullOrEmpty()) {
                                    val jsonObj = JSONObject(jsonMeta)
                                    val lat = jsonObj.optDouble("lat", 0.0)
                                    val lng = jsonObj.optDouble("lng", 0.0)
                                    val addr = jsonObj.optString("addr", "알 수 없는 장소")

                                    if (lat != 0.0 && lng != 0.0) {
                                        val newMemory = Memory(
                                            photoUri = uriString,
                                            address = addr,
                                            lat = lat,
                                            lng = lng,
                                            date = dateAddedSecs * 1000L
                                        )
                                        dbHelper.insertMemory(newMemory)
                                        restoredCount++
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SYNC", "EXIF read error for $uriString", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC", "MediaStore query error", e)
        }

        if (restoredCount > 0) {
            Toast.makeText(this, "${restoredCount}개의 추억을 갤러리에서 복원했습니다! 🔄", Toast.LENGTH_LONG).show()
            memories = dbHelper.getAllMemories()
            showMemoriesOnMap()
        } else {
            Toast.makeText(this, "동기화 완료: 모든 추억이 이미 최신 상태입니다.", Toast.LENGTH_SHORT).show()
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
}
