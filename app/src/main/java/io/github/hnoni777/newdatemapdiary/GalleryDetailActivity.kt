package io.github.hnoni777.newdatemapdiary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.github.hnoni777.newdatemapdiary.databinding.ActivityGalleryDetailBinding

class GalleryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUriString = intent.getStringExtra("image_uri")
        
        if (imageUriString != null) {
            val uri = Uri.parse(imageUriString)
            
            // 📸 Glide 이미지 로드 (Uri 방식 - 무결점 로딩)
            Glide.with(this)
                .load(uri)
                .into(binding.ivDetailImage)

            binding.btnCopyAddress.setOnClickListener {
                copyToClipboard("전설의 사랑 장소 ❤️")
            }

            // 📤 Share button click listener (Safe from TransactionTooLargeException)
            binding.btnShareImage.setOnClickListener {
                shareImage(uri)
            }
        }

        binding.btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("HereWithYou_Location", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, "소중한 장소의 주소가 복사되었습니다! 📋", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareImage(uri: Uri) {
        try {
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                // ⚠️ 폴더블 기기 및 공유 화면 호출 시 튕김 방지 (권한 문제 및 Activity 팝인 현상 해결)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // createChooser를 통해 안정적인 시스템 팝업 띄우기
            startActivity(Intent.createChooser(shareIntent, "HereWithYou 추억 공유하기"))
        } catch (e: Exception) {
            android.util.Log.e("ShareError", "공유 중 에러 발생: ${e.message}")
            android.widget.Toast.makeText(this, "공유를 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
