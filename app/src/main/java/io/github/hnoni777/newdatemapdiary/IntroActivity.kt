package io.github.hnoni777.newdatemapdiary

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
        setContentView(R.layout.activity_intro)

        viewPager = findViewById(R.id.viewPager)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        val pages = listOf(
            IntroPageItem(
                R.drawable.intro_hero_couple,
                "둘만의 소중한 데이트 기록",
                "우리가 함께한 예쁜 순간들을\n나만의 다이어리에 담아보세요"
            ),
            IntroPageItem(
                R.drawable.intro_hero_polaroid,
                "폴라로이드 카드로 찰칵! 📸",
                "예쁜 스티커로 카드를 꾸미고\n지도에 하트 핀을 꽂아 남겨보세요!"
            ),
            IntroPageItem(
                R.drawable.intro_hero_share,
                "추억을 함께 나눠요",
                "만들어진 예쁜 추억 카드들을\n연인과 친구들에게 편하게 공유해봐요!"
            )
        )

        viewPager.adapter = IntroPagerAdapter(pages)

        val btnAction = findViewById<Button>(R.id.btn_take_photo)
        btnAction.text = "추억 남기기"

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
            }
        })

        btnAction.setOnClickListener {
            startActivity(Intent(this@IntroActivity, MainActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.btn_how_to_use).setOnClickListener {
            startActivity(Intent(this@IntroActivity, ManualActivity::class.java))
        }
    }

    private fun updateIndicator(position: Int) {
        // 🎨 [영자 디자인] 활성화: 샴페인 골드 채워진 원 / 비활성화: 흰색 반투명 작은 원
        val activeBg = R.drawable.bg_champagne_gold_button
        val inactiveColor = android.graphics.Color.parseColor("#2EFFFFFF")

        // Dot 1
        if (position == 0) {
            dot1.setBackgroundResource(activeBg)
            val lp = dot1.layoutParams; lp.width = 24; lp.height = 24; dot1.layoutParams = lp
        } else {
            dot1.setBackgroundColor(inactiveColor)
            val lp = dot1.layoutParams; lp.width = 18; lp.height = 18; dot1.layoutParams = lp
        }

        // Dot 2
        if (position == 1) {
            dot2.setBackgroundResource(activeBg)
            val lp = dot2.layoutParams; lp.width = 24; lp.height = 24; dot2.layoutParams = lp
        } else {
            dot2.setBackgroundColor(inactiveColor)
            val lp = dot2.layoutParams; lp.width = 18; lp.height = 18; dot2.layoutParams = lp
        }

        // Dot 3
        if (position == 2) {
            dot3.setBackgroundResource(activeBg)
            val lp = dot3.layoutParams; lp.width = 24; lp.height = 24; dot3.layoutParams = lp
        } else {
            dot3.setBackgroundColor(inactiveColor)
            val lp = dot3.layoutParams; lp.width = 18; lp.height = 18; dot3.layoutParams = lp
        }
    }
}
