package io.github.hnoni777.newdatemapdiary

import android.os.Bundle
import android.text.Html
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
            R.id.m_dot6, R.id.m_dot7, R.id.m_dot8, R.id.m_dot9, R.id.m_dot10, R.id.m_dot11
        )
        dotIds.forEach { id -> dots.add(findViewById(id)) }

        val pages = listOf(
            ManualPage(
                0,
                R.drawable.img_v14_manual_main,
                "1. 한눈에 보는 메인 화면 📱",
                "메인화면 버튼 설명<br/><br/>" +
                        "화면을 쓸어올려 아래 내용을 확인하세요 👆<br/><br/>" +
                        "[01]<br/> [내 추억지도] : 방문 장소를 지도에서 확인합니다.<br/><br/>" +
                        "[02] [사진촬영] : 지금 이 순간을 카메라로 담습니다.<br/><br/>" +
                        "[03] [사진저장] : '원본 사진'만 깔끔하게 보관합니다.<br/><br/>" +
                        "[04] [스샷] : '카드 형태 그대로' 캡처하여 저장합니다.<br/><br/>" +
                        "[05] [공유하기] : 완성된 추억 카드를 연인에게 전송합니다.<br/><br/>" +
                        "[06] [카드저장소] : 지금까지 만든 모든 추억들을 확인합니다.<br/><br/>해당 날자의 카드를 선택하고 <br/>길찾기,로드뷰,공유기능을 사용해보세요<br/><br/>" +
                        "✨ 촬영 후 하단의<br/>[07] 버튼을 눌러보세요!"
            ),
            ManualPage(
                R.drawable.img_v14_icon_02,
                R.drawable.img_v14_manual_main,
                "2. 지금 이 순간, 촬영 시작! 📸",
                "메뉴에 있는 카메라 버튼을 눌러 촬영하세요.<br/><br/>" +
                        "현재 위치 정보가 자동으로 담긴 폴라로이드 카드가 즉시 생성됩니다.<br/><br/>" +
                        "촬영 후 하단의 추억카드 꾸미기 버튼을 누르는 것, 잊지 마세요!"
            ),
            ManualPage(
                0, // Removed icon as requested (Chapter 4)
                R.drawable.img_v14_manual_10,
                "3. 추억카드 꾸미기 메뉴 ✍️",
                "문구 , 테마 , 스티커, 그리기 .<br/><br/>" +
                        "옆으로 넘기시고 메뉴를 확인하세요"
            ),
            ManualPage(
                0, // Removed icon as requested (Chapter 4)
                R.drawable.img_v14_manual_12,
                "4. 소중한 문구 남기기 ✍️",
                "[문구 쓰기] 버튼을 누르면 제목이나 짧은 메시지를 입력할 수 있습니다.<br/><br/>" +
                        "그날 우리가 느꼈던 기분을 적어보세요. 카드 중앙에 예쁜 폰트로 새겨집니다."
            ),
            ManualPage(
                0, // Removed icon as requested (Chapter 3)
                R.drawable.img_v14_manual_04,
                "5. 우리만의 배경 테마 고르기 🖼️",
                "[배경 테마] 버튼을 누르면 다양한 분위기의 테마가 나타납니다.<br/><br/>" +
                        "옆으로 넘겨보며 20여종의 테마중 마음에 드는것을 골라 '테마 저장'을 눌러보세요."
            ),

            ManualPage(
                0, // Removed icon as requested (Chapter 5)
                R.drawable.img_v14_manual_03,
                "6. 귀여운 스티커 고르기 🧸",
                "[스티커] 버튼을 누르면 아기자기한 모음집이 나타납니다.<br/>" +
                        "원하는 스티커를 골라 터치하면 카드 위에 나타납니다.<br/> 100여종의 다양한 스티커를 추가해보세요!"
            ),
            ManualPage(
                0, // Removed icon as requested (Chapter 6)
                R.drawable.img_v14_manual_03,
                "7. 스티커 조작 마스터하기 ✨",
                "스티커를 자유자재로 다뤄보세요!<br/><br/>" +
                        "👆 [이동] : 한 손가락으로 꾹 눌러서 이동<br/>" +
                        "✌️ [확대/축소] : 두 손가락으로 벌리거나 오므리기<br/>" +
                        "🔄 [회전] : 두 손가락을 댄 상태로 빙글 돌리기"
            ),
            ManualPage(
                0,
                R.drawable.img_v14_manual_06,
                "8. 마음가는데로 그리기 ",
                "원하는 색이나 굵기를 선택하여!<br/><br/>" +
                        "마음 가는데로 그려주세요 <br/>"

            ),
            ManualPage(
                0, // Fixed: Changed R.id.map_card to 0 to prevent crash
                R.drawable.img_v14_manual_08, // Using v14 image for safety
                "9.내 추억 지도  📍",
                "우리가 다녀간 장소에 핀이 꽂혔어요!<br/>" +
                        "핀을 터치하면 그날 만들었던 카드들이 말풍선처럼 짜잔! 나타납니다.<br/>" +
                        "옆으로 밀어서(Swipe) 감상하세요.<br/>" +
                        "추억 따라가기를 눌러 다녀왔던 장소들을 탈것들을 이용해서 여행해보세요<br/>" +
                        "지도꾸미기를 이용하여 탈것들을 변경해보시고<br/>" +
                        "일반 핀들을 사진을 보여주는 핀으로 바꿔보세요"
            ),
            ManualPage(
                0,
                R.drawable.img_v14_manual_05, // Using v14 image for safety
                "10. 메모리카드 보관함 🗓️",
                "[06]버튼을 누르면 메모리카드 보관함으로 이동합니다.<br/><br/>" +
                        "캘린더에서 밑줄이 있는 날짜를 눌러 그날 우리가 함께 만들었던 모든 기록들을 모아볼 수 있습니다."
            ),
            ManualPage(
                0,
                R.drawable.img_v14_manual_07, // Using v14 image for safety
                "11. 길찾기와 로드뷰🔄",
                "카드에 있는 네비와 로드뷰기능을 사용해보세요!<br/><br/>" +
                        "(카카오맵이 미설치시 설치페이지로 이동하오니 설치후 사용하세요)"
            )
        )

        viewPager.adapter = ManualAdapter(pages) { context, text -> getPremiumText(context, text) }
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

    private fun getPremiumText(context: android.content.Context, text: String): CharSequence {
        val spannable = android.text.SpannableStringBuilder(text.replace("<br/>", "\n").replace("<br>", "\n"))
        val icons = mapOf(
            "[01]" to R.drawable.img_v14_icon_01,
            "[02]" to R.drawable.img_v14_icon_02,
            "[03]" to R.drawable.img_v14_icon_03,
            "[04]" to R.drawable.img_v14_icon_04,
            "[05]" to R.drawable.img_v14_icon_05,
            "[06]" to R.drawable.img_v14_icon_06,
            "[07]" to R.drawable.img_v14_icon_07
        )

        icons.forEach { (tag, resId) ->
            var index = spannable.indexOf(tag)
            while (index >= 0) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId)
                drawable?.let {
                    val scale = context.resources.displayMetrics.density
                    val size = (24 * scale).toInt()
                    val width = (it.intrinsicWidth.toFloat() / it.intrinsicHeight.toFloat() * size).toInt()
                    it.setBounds(0, 0, width, size)
                    val imageSpan = android.text.style.ImageSpan(it, android.text.style.ImageSpan.ALIGN_BOTTOM)
                    spannable.setSpan(imageSpan, index, index + tag.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                index = spannable.indexOf(tag, index + tag.length)
            }
        }
        return spannable
    }

    class ManualAdapter(private val pages: List<ManualPage>, private val processText: (android.content.Context, String) -> CharSequence) : RecyclerView.Adapter<ManualAdapter.ViewHolder>() {
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
            val context = holder.itemView.context

            if (page.iconRes != 0) {
                holder.icon.setImageResource(page.iconRes)
                holder.icon.visibility = View.VISIBLE
            } else {
                holder.icon.visibility = View.GONE
            }

            holder.image.setImageResource(page.imageRes)
            holder.title.text = page.title
            holder.description.text = processText(context, page.description)
        }

        override fun getItemCount() = pages.size
    }
}
