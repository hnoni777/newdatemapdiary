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
                R.drawable.img_manual_step_2,
                "우리의 추억 지도 🗺️",
                "지도를 돌아다니며 우리가 함께했던 소중한 장소들을 확인해보세요.\n\n📍 [하트 핀] : 핀을 클릭하면 그날의 추억 카드가 나타납니다.\n🔄 [추억 복원] : 갤러리 사진만 있다면 지도의 모든 핀을 언제든 되살릴 수 있어요!"
            ),
            ManualPage(
                R.drawable.img_manual_step_1,
                "추억 기록하기 📸",
                "지금 이 순간을 영원히 기록하고 싶다면?\n\n📷 [중앙 버튼] : 사진을 바로 촬영하거나 앨범에서 선택하여 우리만의 추억 카드를 만들 수 있습니다.\n✨ 장착된 GPS를 통해 자동으로 위치가 기록되니 걱정 끝!"
            ),
            ManualPage(
                R.drawable.img_manual_step_5,
                "카드 예쁘게 꾸미기 🎨",
                "편지지 테마와 스티커로 감성을 더해보세요.\n\n🧸 [스티커 조작 가이드]\n• 이동: 스티커를 누른 채 드래그\n• 크기 조절: 두 손가락으로 벌리거나 오므리기\n• 회전: 두 손가락으로 빙글빙글 돌리기\n\n나만의 멘트까지 적으면 세상에 하나뿐인 카드 완성!"
            ),
            ManualPage(
                R.drawable.img_manual_step_4,
                "추억 보관함 (아카이브) 💌",
                "날짜별로 차곡차곡 쌓인 우리들의 이야기.\n\n📅 [달력 보기] : 날짜를 선택해 그날의 기록을 한눈에 보세요.\n🔗 [공유] : 완성된 카드를 연인에게 보내거나 SNS에 자랑할 수 있습니다."
            ),
            ManualPage(
                R.drawable.img_manual_step_3,
                "생생하게 다시보기 ✨",
                "지도의 핀을 누르면 나타나는 카드 미리보기!\n\n여러 장의 카드가 있다면 스와이프하여 골라볼 수 있습니다. '삭제' 버튼으로 소중하지 않은(?) 기록은 정리도 가능해요."
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
            holder.image.setImageResource(page.imageRes)
            holder.title.text = page.title
            holder.description.text = page.description
        }

        override fun getItemCount() = pages.size
    }
}
