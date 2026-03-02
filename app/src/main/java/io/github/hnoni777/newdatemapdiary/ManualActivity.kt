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
                "1. 추억 카드 만들기 📸",
                "지도 중앙의 [카메라] 버튼을 누르면 지금 장소의 위치를 꽉 담은 폴라로이드 카드가 만들어집니다.\n\n사진을 찍은 후 [내 추억 카드 꾸미기] 버튼을 눌러 예쁘게 편집을 시작해보세요!"
            ),
            ManualPage(
                R.drawable.bg_prem_rosegold, // Temporary for theme
                R.drawable.img_manual_step_5,
                "2. 배경 테마 고르기 🖼️",
                "하단의 [배경 테마] 버튼을 누르면 다양한 편지지 테마가 나타납니다.\n\n옆으로 넘겨보며 곰돌이, 토끼, 로즈골드 등 우리 분위기에 딱 맞는 배경을 골라보세요."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_petal, // Temporary for text
                R.drawable.img_manual_step_5,
                "3. 마음을 담은 문구 쓰기 ✍️",
                "[문구 쓰기] 버튼을 누르면 메시지를 입력할 수 있습니다.\n\n그날의 기분이나 연인에게 하고 싶은 말을 적어보세요. 폰트에 맞춰 카드 중앙에 예쁘게 배치됩니다."
            ),
            ManualPage(
                R.drawable.ic_sticker_prem_love,
                R.drawable.img_manual_step_5,
                "4. 스티커로 꾸미기 🧸",
                "[스티커] 버튼을 눌러 아기자기한 아이템들을 추가해보세요.\n\n원하는 스티커를 선택하면 카드 위에 나타납니다. 이제 손가락으로 마법을 부릴 차례예요!"
            ),
            ManualPage(
                R.drawable.ic_gold_heart,
                R.drawable.img_manual_step_5,
                "5. 스티커 조작법 (필독!) ✨",
                "스티커를 자유자재로 다뤄보세요!\n\n👆 [이동] : 스티커를 한 손가락으로 꾹 눌러 이동\n✌️ [확대/축소] : 두 손가락으로 벌리거나 오므리기\n🔄 [회전] : 두 손가락을 댄 상태로 빙글 돌리기"
            ),
            ManualPage(
                R.drawable.ic_modern_share,
                R.drawable.img_manual_step_5,
                "6. 저장하고 추억 공유하기 💌",
                "하단에는 두 종류의 저장 버튼이 있습니다.\n\n💾 [저장만 하기] : 내 폰 갤러리에만 쏙!\n🚀 [저장 및 공유] : 갤러리 저장 + 지도에 하트 핀 꽂기 + 연인에게 공유까지 한 번에!"
            ),
            ManualPage(
                R.drawable.ic_red_heart_marker,
                R.drawable.img_manual_step_3,
                "7. 내 추억 지도 감상 🗺️",
                "지도에 우리가 다녀간 발자국인 [하트 핀]이 꽂혔습니다!\n\n핀을 누르면 그날의 카드들이 말풍선으로 나타납니다. 여러 장일 경우 옆으로 밀어서(Swipe) 볼 수 있어요."
            ),
            ManualPage(
                R.id.manual_view_pager, // Placeholder for Archive
                R.drawable.img_manual_step_4,
                "8. 지난 추억 보관함 🗓️",
                "좌측 하단의 앨범 버튼을 누르면 [보관함]으로 이동합니다.\n\n상단 캘린더에서 점이 찍힌 날짜를 눌러보세요. 그날 우리가 만들었던 소중한 기록들을 모아볼 수 있습니다."
            ),
            ManualPage(
                R.drawable.ic_modern_retry,
                R.drawable.img_manual_step_2,
                "9. 잃어버린 추억 복원 🔄",
                "폰을 초기화했거나 기기를 변경하셨나요?\n\n지도 상단의 [복원] 버튼을 누르면 갤러리 속 우리 사진들을 찾아 지도의 핀들을 옛날 모습 그대로 다시 살려드립니다!"
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
            // Safe handling if a layout ID or temporary resource is passed
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
