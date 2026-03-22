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
    private lateinit var dot4: View
    private lateinit var dot5: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        viewPager = findViewById(R.id.viewPager)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        dot4 = findViewById(R.id.dot4)
        dot5 = findViewById(R.id.dot5)

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
                R.drawable.intro_hero_flight_travel,
                "추억의 노선을 비행기로 여행하기 ✈️",
                "우리가 함께 거닐었던 소중한 길들을\n비행기를 타고 생생하게 돌아보세요!"
            ),
            IntroPageItem(
                R.drawable.intro_hero_roadview_nav,
                "생생한 로드뷰와 원터치 길찾기 🌇",
                "그날의 공기까지 느껴지는 로드뷰와\n빠른 길 안내로 추억을 다시 방문해보세요!"
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
        val dots = listOf(dot1, dot2, dot3, dot4, dot5)
        val activeBg = R.drawable.bg_champagne_gold_button
        val inactiveColor = android.graphics.Color.parseColor("#2EFFFFFF")

        dots.forEachIndexed { index, dot ->
            if (index == position) {
                dot.setBackgroundResource(activeBg)
                val lp = dot.layoutParams
                lp.width = (24 * resources.displayMetrics.density).toInt()
                lp.height = (24 * resources.displayMetrics.density).toInt()
                dot.layoutParams = lp
            } else {
                dot.setBackgroundColor(inactiveColor)
                val lp = dot.layoutParams
                lp.width = (18 * resources.displayMetrics.density).toInt()
                lp.height = (18 * resources.displayMetrics.density).toInt()
                dot.layoutParams = lp
            }
        }
    }
}
