package io.github.hnoni777.newdatemapdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

data class ManualPage(
    val iconRes: Int,
    val imageRes: Int,
    val title: String,
    val description: String
)

class ManualActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var dots: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        viewPager = findViewById(R.id.manual_view_pager)
        dots = listOf(
            findViewById(R.id.m_dot1),
            findViewById(R.id.m_dot2),
            findViewById(R.id.m_dot3),
            findViewById(R.id.m_dot4),
            findViewById(R.id.m_dot5)
        )

        val pages = listOf(
            ManualPage(
                R.drawable.ic_modern_camera,
                R.drawable.img_manual_step_1,
                "1. 지금 이 순간, 사진 찍기 📷",
                "메인 화면 중앙의 [카메라 아이콘]을 눌러보세요!\n\n현재 장소의 위치 정보와 함께 예쁜 폴라로이드 카드가 즉시 생성됩니다. 촬영 후 '내 추억 카드 꾸미기' 버튼을 눌러 다음 단계로 GO!"
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_love,
                R.drawable.img_manual_step_5,
                "2. 아기자기하게 꾸미기 🎨",
                "생성된 카드를 우리 스타일로 변신시켜요.\n\n✨ [문구 쓰기] : 그날의 감정을 텍스트로 남겨보세요.\n✨ [배경 테마] : 곰돌이, 토끼 등 귀여운 프레임 변경!\n✨ [스티커] : 원하는 위치에 슥슥! (확대/축소/회전 가능)"
            ),
            ManualPage(
                R.drawable.ic_modern_download,
                R.drawable.img_manual_step_5,
                "3. 소중하게 저장하기 💾",
                "편집이 끝났다면 하단의 버튼을 체크!\n\n📍 [저장만 하기] : 내 폰 갤러리에만 조용히 간직해요.\n✨ [저장 및 공유] : 갤러리 저장과 동시에 추억 지도에 하트 핀을 꽂고, 친구에게도 바로 보냅니다."
            ),
            ManualPage(
                R.drawable.ic_red_heart_marker,
                R.drawable.img_manual_step_3,
                "4. 지도 위의 핑크빛 하트 📍",
                "우리가 다녀간 장소에 [하트 핀]이 생겼어요!\n\n핀을 터치하면 그날 만들었던 카드들이 말풍선처럼 짜잔! 나타납니다. 지도를 돌아다니며 우리만의 데이트 지도를 완성해가는 재미를 느껴보세요."
            ),
            ManualPage(
                R.drawable.ic_modern_retry,
                R.drawable.img_manual_step_2,
                "5. 마법 같은 추억 복원 🔄",
                "폰을 바꿔도 걱정 마세요!\n\n지도 화면 우측 상단의 [새로고침 아이콘]을 누르면, 갤러리에 저장된 사진들의 위치 정보를 읽어와 지도의 핀들을 옛날 모습 그대로 마법처럼 되살려줍니다."
            )
        )

        viewPager.adapter = ManualAdapter(pages)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, view ->
            if (index == position) {
                view.setBackgroundResource(R.drawable.bg_gold_pill_button)
            } else {
                view.setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))
            }
        }
    }

    class ManualAdapter(private val pages: List<ManualPage>) : RecyclerView.Adapter<ManualAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_feature_icon)
            val image: ImageView = view.findViewById(R.id.iv_manual_screenshot)
            val title: TextView = view.findViewById(R.id.tv_manual_title)
            val description: TextView = view.findViewById(R.id.tv_manual_description)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manual_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = pages[position]
            holder.icon.setImageResource(page.iconRes)
            holder.image.setImageResource(page.imageRes)
            holder.title.text = page.title
            holder.description.text = page.description
        }

        override fun getItemCount() = pages.size
    }
}
