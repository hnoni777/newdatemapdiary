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
                    val memoryId = label.tag as? Long
                    val clickedMemory = memories.find { it.id == memoryId }
                    if (clickedMemory != null) {
                        val key = clickedMemory.address
                        val group = memories.filter { 
                            it.address == key 
                        }
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

        val groups = memories.groupBy { it.address }

        groups.values.forEach { group ->
            val rep = group.first()
            val pos = LatLng.from(rep.lat, rep.lng)
            boundsBuilder.include(pos)
            
            layer?.addLabel(
                LabelOptions.from(pos)
                    .setStyles(styles)
                    .setTag(rep.id)
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
        inner class MemoryViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val imgView: ImageView = view.findViewById(R.id.card_image)
            val qrView: ImageView = view.findViewById(R.id.card_qr_code)
            val msgText: TextView = view.findViewById(R.id.card_message)
            val addrText: TextView = view.findViewById(R.id.card_address)
            val dateText: TextView = view.findViewById(R.id.card_date)
            
            fun bind(memory: Memory) {
                try {
                    imgView.setImageURI(Uri.parse(memory.photoUri))
                } catch (e: Exception) {
                    imgView.setImageResource(R.drawable.bg_invitation)
                }
                
                imgView.scaleType = ImageView.ScaleType.FIT_CENTER
                imgView.adjustViewBounds = true
                val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
                lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
                lp.dimensionRatio = null
                imgView.layoutParams = lp

                msgText.text = "우리의 소중한 추억 💌"
                addrText.text = memory.address
                dateText.text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(memory.date))
                
                // Add QR Code dynamically
                try {
                    val shortLat = String.format(Locale.US, "%.6f", memory.lat)
                    val shortLng = String.format(Locale.US, "%.6f", memory.lng)
                    val shortAddr = if (memory.address.length > 20) memory.address.substring(0, 20) else memory.address
                    val addrEncoded = java.net.URLEncoder.encode(shortAddr, "UTF-8")
                    val link = "https://hnoni777.github.io/newdatemapdiary/share?lat=$shortLat&lng=$shortLng&addr=$addrEncoded"
                    val qrBitmap = generateQRCodeLocally(link)
                    if (qrBitmap != null) {
                        qrView.setImageBitmap(qrBitmap)
                        qrView.visibility = View.VISIBLE
                    } else {
                        qrView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    qrView.visibility = View.GONE
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MemoryViewHolder {
            val view = layoutInflater.inflate(R.layout.item_memory_card_04, parent, false)
            val scrollView = androidx.core.widget.NestedScrollView(this@MemoryMapActivity).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(view)
            }
            return MemoryViewHolder(scrollView)
        }

        override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }

    private fun generateQRCodeLocally(url: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
            ) 
            val bitMatrix = writer.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                256,
                256,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.TRANSPARENT)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
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
