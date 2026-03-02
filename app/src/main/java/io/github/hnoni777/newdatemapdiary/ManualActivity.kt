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
        
        // Setup Dots (Supporting up to 10 chapters)
        dots = mutableListOf()
        val dotIds = listOf(
            R.id.m_dot1, R.id.m_dot2, R.id.m_dot3, R.id.m_dot4, R.id.m_dot5,
            R.id.m_dot6, R.id.m_dot7, R.id.m_dot8, R.id.m_dot9, R.id.m_dot10
        )
        dotIds.forEach { id -> dots.add(findViewById(id)) }

        val pages = listOf(
            ManualPage(
                R.drawable.btn_manual_memory_map,
                R.drawable.img_manual_step_1,
                "1. 한눈에 보는 메인 화면 📱",
                "지도를 중심으로 우리만의 여행 기록을 한눈에 살펴보세요!\n\n" +
                "📍 [내 추억지도] : 방문했던 장소를 지도 위에서 확인합니다.\n" +
                "📸 [사진촬영] : 지금 이 순간의 생생한 장면을 카메라로 담습니다.\n" +
                "💾 [사진저장] : 촬영한 '원본 사진'만 깔끔하게 저장합니다.\n" +
                "📷 [스샷] : 예쁜 프레임과 정보가 담긴 '카드 형태 그대로' 저장합니다.\n" +
                "🚀 [공유하기] : 완성된 추억 카드를 연인에게 즉시 전송해보세요.\n" +
                "🖼️ [카드저장소] : 지금까지 만든 모든 추억들을 확인하는 공간입니다.\n\n" +
                "✨ 하단의 황금색 [내 추억 카드 꾸미기] 버튼을 눌러보세요!"
            ),
            ManualPage(
                R.drawable.btn_manual_camera,
                R.drawable.img_manual_step_1,
                "2. 지금 이 순간, 촬영 시작! 📸",
                "지도의 중앙에 있는 [카메라] 버튼을 눌러 촬영하세요.\n\n현재 위치 정보가 자동으로 담긴 폴라로이드 카드가 즉시 생성됩니다. 촬영 후 하단의 [내 추억 카드 꾸미기] 버튼을 누르는 것, 잊지 마세요!"
            ),
            ManualPage(
                R.drawable.bg_prem_rosegold, 
                R.drawable.img_manual_theme,
                "3. 우리만의 배경 테마 고르기 🖼️",
                "[배경 테마] 버튼을 누르면 다양한 색상과 분위기의 테마가 나타납니다.\n\n옆으로 넘겨보며 곰돌이, 토끼, 로즈골드 등 우리 분위기에 딱 맞는 배경을 골라보세요. '테마 저장'을 누르면 바로 적용됩니다."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_petal, 
                R.drawable.img_manual_text,
                "4. 소중한 문구 남기기 ✍️",
                "[문구 쓰기] 버튼을 누르면 제목이나 짧은 메시지를 입력할 수 있습니다.\n\n그날 우리가 느꼈던 기분이나 서로에게 하고 싶은 말을 적어보세요. 카드 중앙에 예쁜 폰트로 새겨집니다."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_love,
                R.drawable.img_manual_sticker_list,
                "5. 귀여운 스티커 고르기 🧸",
                "[스티커] 버튼을 누르면 아기자기한 모음집이 나타납니다.\n\n원하는 스티커를 골라 터치하면 카드 위에 나타납니다. 카테고리를 넘겨가며 어울리는 아이템을 추가해보세요!"
            ),
            ManualPage(
                R.drawable.ic_gold_heart,
                R.drawable.img_manual_sticker_list,
                "6. 스티커 조작 마스터하기 ✨",
                "스티커를 자유자재로 다뤄보세요!\n\n👆 [이동] : 한 손가닥으로 꾹 눌러서 이동\n✌️ [확대/축소] : 두 손가락으로 벌리거나 오므리기\n🔄 [회전] : 두 손가락을 댄 상태로 빙글 돌리기"
            ),
            ManualPage(
                R.drawable.btn_manual_share,
                R.drawable.img_manual_step_5,
                "7. 저장하고 추억 공유하기 💌",
                "편집이 끝났다면 하단의 버튼을 체크!\n\n📍 [저장만 하기] : 내 폰 갤러리에만 조용히 간직해요.\n✨ [저장 및 공유] : 갤러리 저장 + 지도에 하트 핀 꽂기 + 연인에게 공유까지 한 번에!"
            ),
            ManualPage(
                R.drawable.ic_red_heart_marker,
                R.drawable.img_manual_step_3,
                "8. 지도 위 핑크빛 하트 📍",
                "우리가 다녀간 장소에 [하트 핀]이 꽂혔어요!\n\n핀을 터치하면 그날 만들었던 카드들이 말풍선처럼 짜잔! 나타납니다. 카드가 여러 장일 경우 옆으로 밀어서(Swipe) 소중한 기록을 다시 감상하세요."
            ),
            ManualPage(
                R.drawable.btn_manual_gallery,
                R.drawable.img_manual_step_4,
                "9. 지난 추억 보관함 🗓️",
                "좌측 하단의 앨범 버튼을 누르면 [보관함]으로 이동합니다.\n\n캘린더에서 점이 찍힌 날짜를 눌러보세요. 그날 우리가 함께 만들었던 모든 기록들을 한눈에 모아볼 수 있습니다."
            ),
            ManualPage(
                R.drawable.ic_modern_retry,
                R.drawable.img_manual_step_2,
                "10. 잃어버린 추억 복원 🔄",
                "기기를 변경했거나 앱을 다시 깔아도 걱정 마세요!\n\n지도 상단의 [복원] 버튼을 누르면 갤러리 속 우리 사진들을 찾아 지도의 핀들을 옛날 모습 그대로 다시 살려드립니다!"
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
                holder.icon.visibility = View.VISIBLE
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
