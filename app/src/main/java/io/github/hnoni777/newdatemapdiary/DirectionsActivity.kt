package io.github.hnoni777.newdatemapdiary

import android.content.Intent
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
import com.google.android.gms.location.LocationServices
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.*
import com.kakao.vectormap.route.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class DirectionsActivity : AppCompatActivity() {

    private val KAKAO_REST_KEY = "83aa83329de094b2cf52a2e8a34206fa"
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var destAddr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // 💡 [Edge-to-Edge] 시스템 바 영역까지 화면 확장
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // 💡 SDK 중복 초기화 방지 및 안전성 확보
            KakaoMapSdk.init(this, "6cc7070982d3684fcac142f3f8f4a691")
            
            setContentView(R.layout.activity_directions)

            destLat = intent.getDoubleExtra("lat", 0.0)
            destLng = intent.getDoubleExtra("lng", 0.0)
            destAddr = intent.getStringExtra("address") ?: ""

            Log.d("DIRECTIONS_DEBUG", "Destination: $destLat, $destLng")

            if (destLat == 0.0 || destLng == 0.0) {
                Toast.makeText(this, "잘못된 목적지 좌표입니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            findViewById<TextView>(R.id.text_directions_address)?.text = destAddr
            findViewById<TextView>(R.id.text_start_address)?.text = "현재 위치" // 나중에 GPS 정보로 보완 가능
            findViewById<View>(R.id.btn_directions_back)?.setOnClickListener { finish() }

            // 🚗 [카카오맵 앱 연동] 내비게이션 실행 및 미설치 시 가이드
            findViewById<View>(R.id.btn_launch_kakao_map)?.setOnClickListener {
                try {
                    // 카카오맵 길찾기 스킴 (자동차 길찾기 모드)
                    val url = "kakaomap://route?ep=$destLat,$destLng&by=CAR"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    // 🚨 카카오맵이 없는 경우: 안내 메시지 출력 후 플레이스토어로 연결
                    Toast.makeText(this, "카카오맵이 설치되어 있지 않습니다. 더 나은 길안내를 위해 카카오맵을 설치해 주세요!", Toast.LENGTH_LONG).show()
                    
                    try {
                        // 플레이스토어 앱 실행 (카카오맵 패키지: net.daum.android.map)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.daum.android.map")))
                    } catch (playEx: Exception) {
                        // 플레이스토어 앱도 없는 경우 (브라우저로 이동)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.daum.android.map")))
                    }
                }
            }

            // 📱 [네비게이션 바 겹침 해결] 하단 카드에 시스템 인셋 적용
            val bottomCard = findViewById<View>(R.id.card_directions_bottom)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // 하단 바 높이만큼 카드를 올리고, 약간의 여백(16dp)을 더 줌
                val extraPadding = 16 * resources.displayMetrics.density
                bottomCard?.translationY = -(systemBars.bottom.toFloat() + extraPadding)
                insets
            }

            val container = findViewById<FrameLayout>(R.id.directions_map_container)
            if (container != null) {
                mapView = MapView(this)
                container.addView(mapView)
                startMap()
            } else {
                Log.e("DIRECTIONS_DEBUG", "Container view not found!")
                Toast.makeText(this, "지도를 표시할 공간을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("DIRECTIONS_ONCREATE", e.toString())
            Toast.makeText(this, "화면 실행 중 에러: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun startMap() {
        try {
            if (!::mapView.isInitialized) return
            
            mapView.start(object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.d("DIRECTIONS_DEBUG", "Map Destroyed")
                }
                override fun onMapError(error: Exception) {
                    Log.e("DIRECTIONS_MAP", error.toString())
                    runOnUiThread { Toast.makeText(this@DirectionsActivity, "지도 로딩 에러: ${error.message}", Toast.LENGTH_LONG).show() }
                }
            }, object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    Log.d("DIRECTIONS_DEBUG", "Map Ready")
                    kakaoMap = map
                    fetchMyLocationAndDirections()
                }
            })
        } catch (e: Exception) {
            Log.e("DIRECTIONS_STARTMAP", e.toString())
            Toast.makeText(this, "지도 초기화 중 에러: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchMyLocationAndDirections() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                runOnUiThread {
                    if (loc != null) {
                        val startLat = loc.latitude
                        val startLng = loc.longitude
                        Log.d("DIRECTIONS_DEBUG", "My Location: $startLat, $startLng")
                        
                        // 🌍 [주소 가져오기] 현재 위치의 주소를 텍스트로 표시
                        fetchCurrentAddress(startLat, startLng)

                        // 시작점과 끝점에 마커 표시
                        showMarkers(LatLng.from(startLat, startLng), LatLng.from(destLat, destLng))
                        
                        fetchRoute(startLat, startLng, destLat, destLng)
                    } else {
                        Toast.makeText(this, "현재 위치를 가져올 수 없습니다. GPS를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener { e ->
                runOnUiThread {
                    Log.e("DIRECTIONS_DEBUG", "Location failed: ${e.message}")
                    Toast.makeText(this, "위치 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Log.e("DIRECTIONS_DEBUG", "Permission missing: ${e.message}")
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DIRECTIONS_DEBUG", "Location error: ${e.message}")
        }
    }

    private fun showMarkers(start: LatLng, end: LatLng) {
        try {
            val map = kakaoMap ?: return
            val labelManager = map.labelManager ?: return
            
            val layer = labelManager.getLayer("directions_layer") 
                ?: labelManager.addLayer(LabelLayerOptions.from("directions_layer"))
            
            layer?.removeAll()

            // 1️⃣ 출발 마커 (비트맵에 글자를 아예 그려서 보냅니다 - 가독성 100% 보장)
            val startBitmap = vectorToBitmap(R.drawable.bg_marker_start, "출발")
            val startStyle = LabelStyle.from(startBitmap)
                .setAnchorPoint(0.5f, 1.0f)

            layer?.addLabel(LabelOptions.from(start)
                .setStyles(LabelStyles.from(startStyle))
            )

            // 2️⃣ 도착 마커
            val endBitmap = vectorToBitmap(R.drawable.bg_marker_arrival, "도착")
            val endStyle = LabelStyle.from(endBitmap)
                .setAnchorPoint(0.5f, 1.0f)

            layer?.addLabel(LabelOptions.from(end)
                .setStyles(LabelStyles.from(endStyle))
            )
            
            Log.d("DIRECTIONS_DEBUG", "Hard-coded Text Bitmaps Shown")
        } catch (e: Exception) {
            Log.e("SHOW_MARKERS", e.toString())
        }
    }

    private fun fetchCurrentAddress(lat: Double, lng: Double) {
        thread {
            try {
                val url = URL("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_KEY")
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(json)
                    val documents = root.getJSONArray("documents")
                    if (documents.length() > 0) {
                        val addressInfo = documents.getJSONObject(0).optJSONObject("address")
                        val addressName = addressInfo?.optString("address_name") ?: "현재 위치"
                        runOnUiThread {
                            findViewById<TextView>(R.id.text_start_address)?.text = addressName
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FETCH_ADDRESS", e.toString())
            }
        }
    }

    private fun vectorToBitmap(resId: Int, pinText: String? = null): android.graphics.Bitmap {
        val drawable = androidx.core.content.ContextCompat.getDrawable(this, resId) ?: return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val bitmap = android.graphics.Bitmap.createBitmap(
            Math.max(1, drawable.intrinsicWidth),
            Math.max(1, drawable.intrinsicHeight),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        // 📝 [텍스트 직접 그리기] SDK 엔진 버그 대비, 비트맵 위에 글자를 박아버립니다!
        if (pinText != null) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f // 비트맵 크기에 맞춰 조절
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            // 핀 헤드의 정중앙 (32x40 기준 Y=20 근처)
            // 텍스트 베이스라인 보정 (+ paint.textSize / 3)
            canvas.drawText(pinText, (canvas.width / 2).toFloat(), (canvas.height / 2 + paint.textSize / 3).toFloat(), paint)
        }
        
        return bitmap
    }

    private fun fetchRoute(startLat: Double, startLng: Double, destLat: Double, destLng: Double) {
        thread {
            try {
                Log.d("DIRECTIONS_DEBUG", "Fetching route from API...")
                val urlString = "https://apis-navi.kakaomobility.com/v1/directions?origin=$startLng,$startLat&destination=$destLng,$destLat&priority=RECOMMEND&car_fuel=GASOLINE&car_type=1&road_details=false"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_KEY")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(json)
                    val routes = root.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val firstRoute = routes.getJSONObject(0)
                        
                        // 📊 [요약 정보 추출] 거리 및 시간
                        val summary = firstRoute.getJSONObject("summary")
                        val distanceMeters = summary.getInt("distance")
                        val durationSeconds = summary.getInt("duration")
                        
                        val distanceKm = String.format("%.1f", distanceMeters / 1000.0)
                        val durationMin = durationSeconds / 60
                        val durationText = if (durationMin >= 60) {
                            "${durationMin / 60}시간 ${durationMin % 60}분"
                        } else {
                            "${durationMin}분"
                        }

                        val resultMsg = firstRoute.optString("result_msg")
                        if (firstRoute.getInt("result_code") != 0) {
                            runOnUiThread { Toast.makeText(this, "경로 탐색 실패: $resultMsg", Toast.LENGTH_LONG).show() }
                            return@thread
                        }
                        
                        val sections = firstRoute.getJSONArray("sections")
                        val vertexes = mutableListOf<LatLng>()
                        for (i in 0 until sections.length()) {
                            val roads = sections.getJSONObject(i).getJSONArray("roads")
                            for (j in 0 until roads.length()) {
                                val vArray = roads.getJSONObject(j).getJSONArray("vertexes")
                                for (k in 0 until vArray.length() step 2) {
                                    vertexes.add(LatLng.from(vArray.getDouble(k + 1), vArray.getDouble(k)))
                                }
                            }
                        }
                        Log.d("DIRECTIONS_DEBUG", "Route points: ${vertexes.size}")
                        runOnUiThread { 
                            findViewById<TextView>(R.id.text_directions_duration)?.text = "소요시간: $durationText"
                            findViewById<TextView>(R.id.text_directions_distance)?.text = "이동거리: ${distanceKm}km"
                            drawRoute(vertexes) 
                        }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.e("DIRECTIONS_API", "Error: $responseCode, $err")
                    runOnUiThread { Toast.makeText(this, "API 오류 ($responseCode): 경로를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e("DIRECTIONS_API", e.toString())
                runOnUiThread { Toast.makeText(this, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun drawRoute(vertexes: List<LatLng>) {
        try {
            if (vertexes.isEmpty()) return
            
            val map = kakaoMap ?: return
            val routeLineManager = map.routeLineManager ?: return
            
            // 1️⃣ 기존 경로 및 레이어 초기화
            val layer = routeLineManager.layer
            layer.removeAll()
            
            // ✨ [슬림 & 세련된 로드 스타일] 카카오맵 느낌의 얇고 진한 선
            val style = RouteLineStyle.from(12f, Color.parseColor("#4D7CFF"), 3f, Color.parseColor("#FFFFFF"))
            val styles = RouteLineStyles.from(style)
            
            val segment = RouteLineSegment.from(vertexes, styles)
            layer.addRouteLine(RouteLineOptions.from(segment))
            
            // 📍 [핀 표시] 출발지와 도착지에 핀을 꽂습니다
            showMarkers(vertexes.first(), vertexes.last())
            
            // 🗺️ [스마트 뷰포트] 출발지와 도착지가 모두 보이게 자동 설정
            var minLat = Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE

            for (v in vertexes) {
                minLat = Math.min(minLat, v.latitude)
                minLng = Math.min(minLng, v.longitude)
                maxLat = Math.max(maxLat, v.latitude)
                maxLng = Math.max(maxLng, v.longitude)
            }

            // 🗺️ [카메라 가두기] 모든 경로가 한 화면에 꽉 차게 조절 (패딩 150)
            // 카카오 Map SDK v2에서는 fitMapPoints를 사용합니다.
            map.moveCamera(CameraUpdateFactory.fitMapPoints(vertexes.toTypedArray(), 150))
            
            Log.d("DIRECTIONS_DEBUG", "Smart Viewport applied using fitMapPoints for ${vertexes.size} points")
        } catch (e: Exception) {
            Log.e("DRAW_ROUTE", e.toString())
            runOnUiThread { Toast.makeText(this, "경로 표시 중 에러: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mapView.isInitialized) mapView.finish()
    }
}
