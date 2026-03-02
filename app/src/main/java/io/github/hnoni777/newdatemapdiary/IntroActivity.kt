package io.github.hnoni777.newdatemapdiary

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import android.view.View

class IntroActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (!prefs.getBoolean("isFirstRun", true)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_intro)

        viewPager = findViewById(R.id.viewPager)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        val pages = listOf(
            IntroPageItem(
                R.drawable.ic_gold_heart,
                "둘만의 소중한 데이트 기록",
                "우리가 함께한 예쁜 순간들을\n나만의 다이어리에 담아보세요"
            ),
            IntroPageItem(
                R.drawable.ic_white_location,
                "폴라로이드 카드로 찰칵! 📸",
                "예쁜 스티커로 카드를 꾸미고\n지도에 하트 핀을 꽂아 남겨보세요!"
            ),
            IntroPageItem(
                R.drawable.ic_modern_share, 
                "추억을 함께 나눠요",
                "만들어진 예쁜 추억 카드들을\n연인과 친구들에게 편하게 공유해봐요!"
            )
        )

        viewPager.adapter = IntroPagerAdapter(pages)

        val btnAction = findViewById<Button>(R.id.btn_take_photo)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                if (position == pages.size - 1) {
                    btnAction.text = "시작하기"
                } else {
                    btnAction.text = "다음"
                }
            }
        })

        btnAction.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                prefs.edit().putBoolean("isFirstRun", false).apply()
                startActivity(Intent(this@IntroActivity, MainActivity::class.java))
                finish()
            }
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
}
