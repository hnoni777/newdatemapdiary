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
    private val cameraLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri = cameraUri
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
                    date = System.currentTimeMillis()
                )
                dbHelper.insertMemory(memory)
                Log.d("DB_INSERT", "메인 스샷 저장 성공")
            } catch (e: Exception) {
                Log.e("DB_INSERT", "내 추억지도 자동 저장 실패", e)
            }
        }

        if (shareAfter && savedUri != null) {
            shareImage(savedUri, currentLat, currentLng, addressText.text.toString())
        } else if (savedUri != null) {
            Toast.makeText(this, "스샷 저장 및 추억지도에 등록 완료", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, lat: Double, lng: Double, address: String): Uri? {
        try {
            val filename = "DateMapDiary_Screenshot_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NewDateMapDiary")
            }

            // Exif Metadata Injector용 임시 파일 🕵️‍♂️
            val tempFile = java.io.File(cacheDir, "temp_screenshot_exif.jpg")
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val encodedAddr = java.net.URLEncoder.encode(address, "UTF-8")
                val jsonMeta = "{\"lat\":$lat, \"lng\":$lng, \"addr\":\"$encodedAddr\"}"
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
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER
            imgView.adjustViewBounds = true
            val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            lp.dimensionRatio = null
            imgView.layoutParams = lp

            cardView.findViewById<TextView>(R.id.card_message).text = "오늘의 로맨틱한 순간"
            cardView.findViewById<TextView>(R.id.card_address).text = addressText.text
            cardView.findViewById<TextView>(R.id.card_date).text =
                SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

            updateCardQRCode(cardView, currentLat, currentLng, addressText.text.toString())
        } else {
            // 빈 사진일 경우 다꾸 초대장 이미지를 플레이스홀더로 사용
            imgView.setImageResource(R.drawable.bg_invitation)
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER
            imgView.adjustViewBounds = true
            val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            lp.dimensionRatio = null
            imgView.layoutParams = lp

            cardView.findViewById<TextView>(R.id.card_message).text = ""
            cardView.findViewById<TextView>(R.id.card_address).text = addressText.text
            cardView.findViewById<TextView>(R.id.card_date).text = ""
        }

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
        imgView.setImageResource(R.drawable.bg_invitation)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        imgView.adjustViewBounds = true
        val lp = imgView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        lp.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        lp.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        lp.dimensionRatio = null
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
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQ_LOCATION -> startMap()
                REQ_CAMERA -> openCamera()
            }
        } else {
            if (requestCode == REQ_CAMERA) {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔐 카메라 권한 체크
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
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
        try {
            val shortLat = String.format("%.6f", lat)
            val shortLng = String.format("%.6f", lng)
            val shortAddr = if (addr.length > 20) addr.substring(0, 20) else addr
            val addrEncoded = java.net.URLEncoder.encode(shortAddr, "UTF-8")
            
            val link = "https://hnoni777.github.io/newdatemapdiary/share/map.html?lat=$shortLat&lng=$shortLng&addr=$addrEncoded"
            val qrBitmap = generateQRCode(link)
            
            val qrView = cardView.findViewById<ImageView>(R.id.card_qr_code)
            if (qrBitmap != null && qrView != null) {
                qrView.setImageBitmap(qrBitmap)
                qrView.visibility = View.VISIBLE
            } else if (qrView != null) {
                qrView.visibility = View.INVISIBLE
            }
        } catch (e: Exception) {
            Log.e("QR_CODE", "Failed to add QR", e)
        }
    }

    private fun generateQRCode(url: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
            ) 
            val bitMatrix = writer.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512,
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
            Log.e("QR_GEN", "Error", e)
            null
        }
    }
}