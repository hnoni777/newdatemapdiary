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

        // 바로 메인 액티비티로 이동
        findViewById<Button>(R.id.btn_take_photo).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
