package io.github.hnoni777.newdatemapdiary

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
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

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    private lateinit var addressText: TextView
    private var photoUri: Uri? = null

    private val REQ_LOCATION = 100
    private val KAKAO_REST_KEY = "83aa83329de094b2cf52a2e8a34206fa"

    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var isFromDeepLink: Boolean = false
    private var deepLinkAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KakaoMapSdk.init(this, "6cc7070982d3684fcac142f3f8f4a691")
        setContentView(R.layout.activity_main)

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
            startMap()
            addressText.text = deepLinkAddress
            showDeepLinkInvitationCard()
        } else {
            requestLocationPermission()
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
            
            if (latStr != null && lngStr != null) {
                currentLat = latStr.toDoubleOrNull() ?: 0.0
                currentLng = lngStr.toDoubleOrNull() ?: 0.0
                deepLinkAddress = addrStr ?: ""
                isFromDeepLink = true
            }
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_retry).setOnClickListener {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.btn_save_photo).setOnClickListener {
            photoUri?.let {
                savePhotoToGallery(it)
            } ?: Toast.makeText(this, "저장할 사진이 없습니다", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_screenshot).setOnClickListener {
            takeScreenshot(false)
        }

        findViewById<View>(R.id.btn_share_photo).setOnClickListener {
            takeScreenshot(true)
        }

        findViewById<View>(R.id.btn_gallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<View>(R.id.btn_create_card).setOnClickListener {
            if (photoUri == null) {
                Toast.makeText(this, "사진을 먼저 촬영해주세요", Toast.LENGTH_SHORT).show()
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

        val savedUri = saveBitmapToGallery(bitmap)
        if (shareAfter && savedUri != null) {
            shareImage(savedUri)
        } else if (savedUri != null) {
            Toast.makeText(this, "스샷 저장 완료", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        try {
            val filename = "DateMapDiary_Screenshot_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NewDateMapDiary")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("MediaStore insert failed")
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return uri
        } catch (e: Exception) {
            Log.e("SCREENSHOT", e.toString())
            return null
        }
    }

    private fun shareImage(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "HereWithYou 추억 공유하기"))
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
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NewDateMapDiary")
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
        container.removeAllViews()

        val cardView = layoutInflater.inflate(
            R.layout.item_memory_card_04,
            container,
            false
        )

        val imgView = cardView.findViewById<ImageView>(R.id.card_image)
        if(photoUri != null) {
            imgView.setImageURI(photoUri)
        }

        cardView.findViewById<TextView>(R.id.card_message).text = "오늘의 로맨틱한 순간"
        cardView.findViewById<TextView>(R.id.card_address).text = addressText.text
        cardView.findViewById<TextView>(R.id.card_date).text =
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

        container.addView(cardView)
    }

    private fun showDeepLinkInvitationCard() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        container.removeAllViews()

        val cardView = layoutInflater.inflate(
            R.layout.item_memory_card_04,
            container,
            false
        )

        val imgView = cardView.findViewById<ImageView>(R.id.card_image)
        imgView.setImageResource(R.drawable.vd_lovers)
        imgView.setBackgroundColor(Color.parseColor("#FFD9EC"))

        cardView.findViewById<TextView>(R.id.card_message).text = "우리의 추억이 도착했어요💌"
        cardView.findViewById<TextView>(R.id.card_address).text = deepLinkAddress
        cardView.findViewById<TextView>(R.id.card_date).text = "함께 확인해볼까요?"

        findViewById<View>(R.id.btn_create_card).visibility = View.GONE
        container.addView(cardView)
    }

    // ===============================
    // 📍 위치 / 지도
    // ===============================
    private fun requestLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_LOCATION
            )
        } else {
            startMap()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startMap()
        }
    }

    private fun startMap() {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {
                Log.e("MAP_ERROR", error.toString())
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
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    val pos = LatLng.from(currentLat, currentLng)
                    showLocationOnMap(pos)
                    fetchAddressFromKakao(pos.latitude, pos.longitude)
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
            } catch (e: Exception) {
                Log.e("KAKAO_ADDRESS", e.toString())
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
}