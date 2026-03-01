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
                        showMemoryCardDialog(clickedMemory)
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

        memories.forEach { memory ->
            val pos = LatLng.from(memory.lat, memory.lng)
            boundsBuilder.include(pos)
            
            layer?.addLabel(
                LabelOptions.from(pos)
                    .setStyles(styles)
                    .setTag(memory.id)
            )
        }

        if (memories.isNotEmpty()) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(memories[0].lat, memories[0].lng), 12))
        }
    }

    private fun showMemoryCardDialog(memory: Memory) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_memory_card, null)
        val container = view.findViewById<FrameLayout>(R.id.dialog_card_container)
        
        val cardView = layoutInflater.inflate(R.layout.item_memory_card_04, container, false)
        
        val imgView = cardView.findViewById<ImageView>(R.id.card_image)
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

        cardView.findViewById<TextView>(R.id.card_message).text = "우리의 소중한 추억 💌"
        cardView.findViewById<TextView>(R.id.card_address).text = memory.address
        cardView.findViewById<TextView>(R.id.card_date).text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(memory.date))
        
        container.addView(cardView)
        dialog.setContentView(view)
        
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        
        dialog.show()
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
