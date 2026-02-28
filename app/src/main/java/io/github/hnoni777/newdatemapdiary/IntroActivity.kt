package io.github.hnoni777.newdatemapdiary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import android.view.View
import java.io.File

class IntroActivity : AppCompatActivity() {

    private lateinit var photoUri: Uri
    private val REQ_CAMERA = 200

    private lateinit var viewPager: ViewPager2
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    // 📷 카메라 결과 받기
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("photoUri", photoUri.toString())
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "사진 촬영 취소됨", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        viewPager = findViewById(R.id.viewPager)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        val pages = listOf(
            IntroPageItem(
                R.drawable.ic_gold_heart,
                "모든 기억이 자리를 찾는 곳",
                "어디든 함께하는 우리만의\n소중한 지도 다이어리"
            ),
            IntroPageItem(
                R.drawable.ic_white_location,
                "발길이 닿는 모든 곳",
                "우리가 함께 간 곳의 사진을 찍으면\n위치와 함께 지도에 예쁘게 저장돼요"
            ),
            IntroPageItem(
                R.drawable.ic_modern_share, 
                "함께 나누는 설렘",
                "우리가 예쁘게 만든 추억 카드들을\n소중한 사람들과 편하게 나눠보세요"
            )
        )

        viewPager.adapter = IntroPagerAdapter(pages)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
            }
        })

        findViewById<Button>(R.id.btn_take_photo).setOnClickListener {
            checkCameraPermissionAndOpen()
        }
    }

    private fun updateIndicator(position: Int) {
        val activeBg = R.drawable.bg_gold_pill_button
        val inactiveBg = "#33FFFFFF"

        dot1.setBackgroundResource(if (position == 0) activeBg else 0)
        if (position != 0) dot1.setBackgroundColor(android.graphics.Color.parseColor(inactiveBg))

        dot2.setBackgroundResource(if (position == 1) activeBg else 0)
        if (position != 1) dot2.setBackgroundColor(android.graphics.Color.parseColor(inactiveBg))

        dot3.setBackgroundResource(if (position == 2) activeBg else 0)
        if (position != 2) dot3.setBackgroundColor(android.graphics.Color.parseColor(inactiveBg))
    }

    // 🔐 카메라 권한 체크
    private fun checkCameraPermissionAndOpen() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQ_CAMERA
            )
        }
    }

    // 🔐 권한 요청 결과
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_CAMERA &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 📸 카메라 실행
    private fun openCamera() {
        val photoFile = File.createTempFile(
            "photo_",
            ".jpg",
            cacheDir
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )

        cameraLauncher.launch(photoUri)
    }
}
