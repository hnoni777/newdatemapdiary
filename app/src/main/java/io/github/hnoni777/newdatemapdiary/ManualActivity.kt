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
    private lateinit var dots: MutableList<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        viewPager = findViewById(R.id.manual_view_pager)
        
        // Setup Dots
        dots = mutableListOf()
        val dotIds = listOf(
            R.id.m_dot1, R.id.m_dot2, R.id.m_dot3, R.id.m_dot4, R.id.m_dot5,
            R.id.m_dot6, R.id.m_dot7, R.id.m_dot8, R.id.m_dot9
        )
        dotIds.forEach { id -> dots.add(findViewById(id)) }

        val pages = listOf(
            ManualPage(
                R.drawable.ic_modern_camera,
                R.drawable.img_manual_step_1,
                "1. 지금 이 순간, 촬영 시작! 📸",
                "지도의 중앙에 있는 [카메라] 버튼을 눌러보세요.\n\n현재 장소의 위치 정보와 함께 예쁜 폴라로이드 카드가 즉시 생성됩니다. 촬영 후 하단의 [내 추억 카드 꾸미기] 버튼을 누르면 마법 같은 편집이 시작됩니다!"
            ),
            ManualPage(
                R.drawable.bg_prem_rosegold, // Generic theme icon
                R.drawable.img_manual_theme,
                "2. 우리만의 배경 테마 고르기 🖼️",
                "[배경 테마] 버튼을 누르면 다양한 색상과 분위기의 편지지 테마가 나타납니다.\n\n로맨틱한 레드, 심플한 화이트 등 그날의 기분에 맞는 예쁜 배경을 골라보세요. '테마 저장'을 누르면 바로 적용됩니다."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_petal, // Generic quill icon
                R.drawable.img_manual_text,
                "3. 손글씨보다 예쁜 문구 남기기 ✍️",
                "[문구 쓰기] 버튼을 누르면 텍스트를 입력할 수 있습니다.\n\n우리가 함께한 이 장소에서 느낀 감정이나 연인에게 전하고 싶은 짧은 메시지를 적어보세요. 카드 중앙에 예쁜 폰트로 새겨집니다."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_love,
                R.drawable.img_manual_sticker_list,
                "4. 귀여운 스티커 고르기 🧸",
                "[스티커] 버튼을 누르면 아기자기한 스티커 모음이 나타납니다.\n\n원하는 스티커를 골라 터치해보세요! 카드 위에 스티커가 나타나며, 이제 이 스티커를 자유롭게 배치할 차례입니다."
            ),
            ManualPage(
                R.drawable.ic_gold_heart,
                R.drawable.img_manual_step_5,
                "5. 스티커 조작 마스터하기 ✨",
                "스티커를 자유자재로 다뤄보세요!\n\n👆 [이동] : 한 손가락으로 꾹 눌러서 이동\n✌️ [확대/축소] : 두 손가락으로 벌리거나 오므리기\n🔄 [회전] : 두 손가락을 댄 상태로 빙글 돌리기\n\n한번 해보면 누구나 쉽게 익힐 수 있어요!"
            ),
            ManualPage(
                R.drawable.ic_modern_share,
                R.drawable.img_manual_step_5,
                "6. 소중한 추억 저장 및 공유 💌",
                "편집이 끝났다면 하단의 버튼을 체크!\n\n💾 [저장만 하기] : 내 폰 갤러리에만 조용히 간직해요.\n✨ [저장 및 공유] : 갤러리에 저장함과 동시에 지도에 하트 핀을 꽂고, 친구에게도 바로 공유합니다!"
            ),
            ManualPage(
                R.drawable.ic_red_heart_marker,
                R.drawable.img_manual_step_3,
                "7. 지도 위에서 카드 다시보기 🗺️",
                "우리가 다녀간 장소에 [하트 핀]이 생겼어요!\n\n핀을 터치하면 그날 만들었던 카드들이 말풍선처럼 짜잔! 나타납니다. 카드가 여러 장이라면 옆으로 밀어서(Swipe) 생생한 추억을 다시 감상하세요."
            ),
            ManualPage(
                R.drawable.ic_modern_gallery,
                R.drawable.img_manual_step_4,
                "8. 차곡차곡 쌓인 보관함 🗓️",
                "좌측 하단의 앨범 버튼을 누르면 [보관함]으로 이동합니다.\n\n상측 캘린더에서 점이 찍힌 날짜를 눌러보세요. 그날 우리가 함께 만들었던 모든 추억들을 갤러리 형태로 모아볼 수 있습니다."
            ),
            ManualPage(
                R.drawable.ic_modern_retry,
                R.drawable.img_manual_step_2,
                "9. 마법 같은 추억 복원 기능 🔄",
                "폰을 바꾸거나 앱을 다시 설치해도 걱정하지 마세요!\n\n지도 상단의 [복원] 버튼을 누르면, 갤러리에 저장된 우리 사진들의 위치를 분석해 지도의 핀들을 옛날 모습 그대로 마법처럼 되살려줍니다."
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
            try {
                holder.icon.setImageResource(page.iconRes)
            } catch (e: Exception) {
                holder.icon.visibility = View.GONE
            }
            holder.image.setImageResource(page.imageRes)
            holder.title.text = page.title
            holder.description.text = page.description
        }

        override fun getItemCount() = pages.size
    }
}
