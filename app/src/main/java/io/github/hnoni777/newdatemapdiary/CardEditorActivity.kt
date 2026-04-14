package io.github.hnoni777.newdatemapdiary

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.PorterDuffXfermode
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class CardEditorActivity : AppCompatActivity() {

    private lateinit var editCardMessage: EditText
    private var photoUri: Uri? = null
    private var address: String = ""
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var currentRating: Int = 0

    private val pickFaceStickerImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            processFaceSticker(it)
        }
    }

    private var currentSelectedSticker: View? = null
    private var scaleFactor = 1f
    private var initialDistance = 0f
    private var initialRotation = 0f

    private val sakuraHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sakuraRunnable: Runnable? = null

    // Save the original beautiful handwriting font instantiated from XML
    private var calligraphyFont: android.graphics.Typeface? = null

    private lateinit var billingManager: BillingManager
    private var isPremiumPurchased = false
    private var chosenMarkerProfileFilename: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_editor)

        photoUri = intent.getStringExtra("photoUri")?.let { Uri.parse(it) }
        address = intent.getStringExtra("address") ?: "주소 정보 없음"
        lat = intent.getDoubleExtra("lat", 0.0)
        lng = intent.getDoubleExtra("lng", 0.0)

        editCardMessage = findViewById(R.id.edit_card_message)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        editCardMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCardMessageText()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        setupButtons()
        setupPanels()

        billingManager = BillingManager(this) { isPremium, isInitialCheck ->
            runOnUiThread {
                // 🛠️ [코부장 임시 락 해제] 비공개 테스터를 위한 전체 개방 모드! (출시 전에는 제거 필수 🚨)
                isPremiumPurchased = true

                if (isPremium && !isInitialCheck) {
                    Toast.makeText(this, "💎 프리미엄 혜택이 적용되었습니다. 감사합니다!", Toast.LENGTH_LONG).show()
                }
            }
        }

        isPremiumPurchased = true
        showCardPreview()

        // 🛡️ [비공개 테스트 모드] 에디터 도구함 봉쇄
        if (AppConfig.IS_TEST_MODE) {
            findViewById<View>(R.id.btn_category_theme)?.visibility = View.GONE
            findViewById<View>(R.id.btn_category_sticker)?.visibility = View.GONE
            findViewById<View>(R.id.btn_category_draw)?.visibility = View.GONE
            findViewById<View>(R.id.btn_category_marker)?.visibility = View.GONE
            // 📝 [부장님 특별 지시] 문구 수정(텍스트) 기능은 열어둠
            findViewById<View>(R.id.btn_category_text)?.visibility = View.VISIBLE
        }
    }

    private fun setupPanels() {
        val tray = findViewById<View>(R.id.floating_panel_tray)
        val dimOverlay = findViewById<View>(R.id.panel_dim_overlay)

        val panelText = findViewById<View>(R.id.panel_text)
        val panelTheme = findViewById<View>(R.id.panel_theme)
        val panelSticker = findViewById<View>(R.id.panel_sticker)
        val panelDraw = findViewById<View>(R.id.panel_draw)
        val panelMarker = findViewById<View>(R.id.panel_marker)

        fun getDrawingView(): DrawingView? {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                return container.getChildAt(0).findViewById(R.id.card_drawing_view)
            }
            return null
        }

        fun showPanel(panel: View) {
            // Hide all panels first
            panelText.visibility = View.GONE
            panelTheme.visibility = View.GONE
            panelSticker.visibility = View.GONE
            panelDraw.visibility = View.GONE
            panelMarker.visibility = View.GONE

            // Show target panel
            panel.visibility = View.VISIBLE

            // 🖌️ Enable Drawing mode and bring to absolute front
            getDrawingView()?.let { dv ->
                val isDrawMode = (panel == panelDraw)
                dv.setDrawingEnabled(isDrawMode)
                if (isDrawMode) {
                    dv.visibility = View.VISIBLE
                    dv.bringToFront()
                }
            }

            // 📍 지도 핀 패널일 경우 리스트 로드
            if (panel == panelMarker) {
                setupMarkerProfileList()
            }

            // Show Tray & Overlay (Transparent)
            if (tray.visibility != View.VISIBLE) {
                tray.visibility = View.VISIBLE
                tray.translationY = 1000f // Start from below
                tray.animate().translationY(0f).setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator()).start()

                dimOverlay.visibility = View.VISIBLE
            }
        }

        fun hidePanels() {
            getDrawingView()?.setDrawingEnabled(false)

            tray.animate().translationY(1000f).setDuration(250)
                .withEndAction { tray.visibility = View.GONE }
                .start()

            dimOverlay.visibility = View.GONE
        }

        findViewById<View>(R.id.btn_category_text).setOnClickListener { showPanel(panelText) }
        findViewById<View>(R.id.btn_category_theme).setOnClickListener { showPanel(panelTheme) }
        findViewById<View>(R.id.btn_category_sticker).setOnClickListener { showPanel(panelSticker) }
        findViewById<View>(R.id.btn_category_draw).setOnClickListener { showPanel(panelDraw) }
        findViewById<View>(R.id.btn_category_marker).setOnClickListener { showPanel(panelMarker) }

        findViewById<View>(R.id.btn_done_text).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_theme).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_sticker).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_draw).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_marker).setOnClickListener { hidePanels() }

        // Close when clicking outside on overlay (now transparent)
        dimOverlay.setOnClickListener { hidePanels() }

        // 📸 [NEW] Create Face Sticker Button
        findViewById<View>(R.id.btn_create_face_sticker).setOnClickListener {
            pickFaceStickerImage.launch("image/*")
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_save_photo).setOnClickListener {
            takeScreenshot(false)
        }

        findViewById<View>(R.id.btn_share_photo).setOnClickListener {
            takeScreenshot(true)
        }

        // 🎨 Premium Theme Selection
        findViewById<View>(R.id.btn_effect_premium_leather).setOnClickListener { applyCardEffect("premium_leather") }
        findViewById<View>(R.id.btn_effect_premium_glass).setOnClickListener { applyCardEffect("premium_glass") }
        findViewById<View>(R.id.btn_effect_premium_film).setOnClickListener { applyCardEffect("premium_film") }

        // 🎨 Basic Theme Selection
        findViewById<View>(R.id.btn_effect_basic).setOnClickListener { applyCardEffect("basic") }
        findViewById<View>(R.id.btn_effect_oatmeal).setOnClickListener { applyCardEffect("oatmeal") }
        findViewById<View>(R.id.btn_effect_matcha).setOnClickListener { applyCardEffect("matcha") }
        findViewById<View>(R.id.btn_effect_peach).setOnClickListener { applyCardEffect("peach") }
        findViewById<View>(R.id.btn_effect_lavender).setOnClickListener { applyCardEffect("lavender") }
        findViewById<View>(R.id.btn_effect_charcoal).setOnClickListener { applyCardEffect("charcoal") }
        findViewById<View>(R.id.btn_effect_vip).setOnClickListener { applyCardEffect("vip") }
        findViewById<View>(R.id.btn_effect_letter).setOnClickListener { applyCardEffect("letter") }
        findViewById<View>(R.id.btn_effect_burgundy).setOnClickListener { applyCardEffect("burgundy") }
        findViewById<View>(R.id.btn_effect_navy).setOnClickListener { applyCardEffect("navy") }
        findViewById<View>(R.id.btn_effect_cream).setOnClickListener { applyCardEffect("cream") }
        findViewById<View>(R.id.btn_effect_emerald).setOnClickListener { applyCardEffect("emerald") }
        findViewById<View>(R.id.btn_effect_pearl).setOnClickListener { applyCardEffect("pearl") }
        findViewById<View>(R.id.btn_effect_rosegold).setOnClickListener { applyCardEffect("rosegold") }
        findViewById<View>(R.id.btn_effect_midnight).setOnClickListener { applyCardEffect("midnight") }
        findViewById<View>(R.id.btn_effect_purple).setOnClickListener { applyCardEffect("purple") }
        findViewById<View>(R.id.btn_effect_dreamy).setOnClickListener { applyCardEffect("dreamy") }
        findViewById<View>(R.id.btn_effect_brutalism).setOnClickListener { applyCardEffect("brutalism") }
        findViewById<View>(R.id.btn_effect_ticket).setOnClickListener { applyCardEffect("ticket") }
        findViewById<View>(R.id.btn_effect_cyber).setOnClickListener { applyCardEffect("cyber") }
        findViewById<View>(R.id.btn_effect_cute).setOnClickListener { applyCardEffect("cute") }
        findViewById<View>(R.id.btn_effect_heart).setOnClickListener { applyCardEffect("heart") }
        findViewById<View>(R.id.btn_effect_starry).setOnClickListener { applyCardEffect("starry") }
        findViewById<View>(R.id.btn_effect_cat).setOnClickListener { applyCardEffect("cat") }
        findViewById<View>(R.id.btn_effect_dessert).setOnClickListener { applyCardEffect("dessert") }
        findViewById<View>(R.id.btn_effect_bw).setOnClickListener { applyCardEffect("bw") }
        findViewById<View>(R.id.btn_effect_sakura).setOnClickListener { applyCardEffect("sakura") }

        // 🖍️ Drawing Tool Click Listeners
        fun getDrawingView(): DrawingView? {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                return container.getChildAt(0).findViewById(R.id.card_drawing_view)
            }
            return null
        }

        findViewById<View>(R.id.btn_draw_color_black).setOnClickListener { getDrawingView()?.setStrokeColor(Color.BLACK) }
        findViewById<View>(R.id.btn_draw_color_white).setOnClickListener { getDrawingView()?.setStrokeColor(Color.WHITE) }
        findViewById<View>(R.id.btn_draw_color_red).setOnClickListener { getDrawingView()?.setStrokeColor(Color.parseColor("#FF5252")) }
        findViewById<View>(R.id.btn_draw_color_pink).setOnClickListener { getDrawingView()?.setStrokeColor(Color.parseColor("#FF4081")) }
        findViewById<View>(R.id.btn_draw_color_blue).setOnClickListener { getDrawingView()?.setStrokeColor(Color.parseColor("#448AFF")) }
        findViewById<View>(R.id.btn_draw_color_gold).setOnClickListener { getDrawingView()?.setStrokeColor(Color.parseColor("#D4AF37")) }

        findViewById<View>(R.id.btn_draw_size_thin).setOnClickListener { getDrawingView()?.setStrokeWidth(5f) }
        findViewById<View>(R.id.btn_draw_size_med).setOnClickListener { getDrawingView()?.setStrokeWidth(12f) }
        findViewById<View>(R.id.btn_draw_size_thick).setOnClickListener { getDrawingView()?.setStrokeWidth(25f) }

        findViewById<View>(R.id.btn_draw_clear).setOnClickListener { getDrawingView()?.clear() }

        // 🎀 Sticker Tab Selection
        val tabBasic = findViewById<TextView>(R.id.tab_sticker_basic)
        val tabPremium = findViewById<TextView>(R.id.tab_sticker_premium)
        val scrollBasic = findViewById<View>(R.id.scroll_sticker_basic)
        val scrollPremium = findViewById<View>(R.id.scroll_sticker_premium)

        tabBasic.setOnClickListener {
            tabBasic.setBackgroundResource(R.drawable.bg_romantic_button)
            tabBasic.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF1493"))
            tabBasic.setTextColor(Color.WHITE)

            tabPremium.setBackgroundResource(0)
            tabPremium.setTextColor(Color.parseColor("#80FFFFFF"))

            scrollBasic.visibility = View.VISIBLE
            scrollPremium.visibility = View.GONE
        }

        tabPremium.setOnClickListener {
            tabPremium.setBackgroundResource(R.drawable.bg_romantic_button)
            tabPremium.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D4AF37")) // Gold
            tabPremium.setTextColor(Color.WHITE)

            tabBasic.setBackgroundResource(0)
            tabBasic.setTextColor(Color.parseColor("#80FFFFFF"))

            scrollBasic.visibility = View.GONE
            scrollPremium.visibility = View.VISIBLE
        }

        // 🎀 & 💎 Sticker Selection
        setupStickerDrawers()
    }

    private fun setupStickerDrawers() {
        val basicScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_sticker_basic)
        val premiumScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_sticker_premium)

        // 🔄 Re-enable and refine for constant independent scrolling
        basicScroll.isNestedScrollingEnabled = true
        premiumScroll.isNestedScrollingEnabled = true

        val scrollTouchListener = View.OnTouchListener { v, event ->
            // Force parent NOT to intercept regardless of boundaries
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false // Continue to handle scroll internally
        }
        basicScroll.setOnTouchListener(scrollTouchListener)
        premiumScroll.setOnTouchListener(scrollTouchListener)

        val basicContainer = findViewById<FlowLayout>(R.id.container_basic_stickers)
        val premiumContainer = findViewById<FlowLayout>(R.id.container_premium_stickers)

        basicContainer.removeAllViews()
        premiumContainer.removeAllViews()

        // Basic Emojis 30
        val basicEmojis = listOf(
            "🤍", "⭐", "🌙", "☀️", "☁️", "🌸", "🌻", "🍀", "🍁", "🐾",
            "🧸", "🎈", "🎁", "💌", "📍", "✂️", "📸", "☕", "🍰", "🍎",
            "🍓", "🍒", "🍑", "🍄", "🌷", "🌹", "🦋", "🐝", "🐥", "🐶"
        )

        // Premium Emojis 20 (Removed overlap with VVIP)
        val premiumEmojis = listOf(
            "👑", "🎨", "🕊️", "🎶", "🎵", "🎸", "🎷", "🎺", "🎻", "🎧",
            "⛵", "🛳️", "🎭", "🎪", "🎡", "🚀", "🛸", "🎀", "🧸", "🎈"
        )

        // Premium Lettering 6
        val premiumLetterings = listOf(
            "Our Memory", "Happy Anniversary", "Always With You", "My Love", "Special Day", "Forever"
        )

        // VVIP Premium Packs
        val vvipLetterings = listOf(
            "With My Love", "You & Me", "Perfect Day", "Destiny", "XOXO"
        )
        val vvipHeartEmojis = listOf("❤️", "💖", "💘", "💝", "💕", "💞", "💓", "💗", "❣️", "💌")
        val vvipWatercolorEmojis = listOf("🥂", "💍", "💎", "🦢", "🍾", "💐", "🍷", "🎂", "🦋", "🌹")
        val vvipNeonEmojis = listOf("✨", "💫", "🌟", "🔥", "🔮", "🪄", "🌈", "⚡", "⭐", "☀️")

        // Helper to add drawable sticker
        fun createDrawableIcon(resId: Int, width: Int, isPremium: Boolean): View {
            val frame = FrameLayout(this).apply {
                val params = android.view.ViewGroup.MarginLayoutParams(
                    (width * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                )
                params.setMargins(
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt()
                )
                layoutParams = params

                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = getDrawable(outValue.resourceId)

                setOnClickListener {
                    Log.d("STICKER_CLICK", "Drawable clicked. isPremium: $isPremium, purchased: $isPremiumPurchased")
                    if (isPremium && !isPremiumPurchased) {
                        showPremiumBillingDialog()
                    } else {
                        addOrToggleSticker("icon", resId, 0, isPremium)
                    }                                                                                                                   
                }
            }
            val img = ImageView(this).apply {
                setImageResource(resId)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(img)

            // ✨ [프리미엄 자물쇠 배지] 가시성을 해치지 않게 우측 하단에 작게 표시
            if (isPremium) {
                val lockView = TextView(this).apply {
                    text = "🔒"
                    textSize = 8f
                    alpha = 0.7f
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    }
                }
                frame.addView(lockView)
            }

            return frame
        }

        // Helper to add emoji sticker
        fun createEmojiIcon(emoji: String, isPremium: Boolean): View {
            val frame = FrameLayout(this).apply {
                val params = android.view.ViewGroup.MarginLayoutParams(
                    (42 * resources.displayMetrics.density).toInt(),
                    (42 * resources.displayMetrics.density).toInt()
                )
                params.setMargins(
                    (4 * resources.displayMetrics.density).toInt(),
                    (4 * resources.displayMetrics.density).toInt(),
                    (4 * resources.displayMetrics.density).toInt(),
                    (4 * resources.displayMetrics.density).toInt()
                )
                layoutParams = params

                // 🎨 드로어 배경 다시 제거 (이모지 자체의 시안성 확보)
                background = null

                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = getDrawable(outValue.resourceId)

                setOnClickListener {
                    Log.d("STICKER_CLICK", "Emoji clicked: $emoji. isPremium: $isPremium, purchased: $isPremiumPurchased")
                    if (isPremium && !isPremiumPurchased) {
                        showPremiumBillingDialog()
                    } else {
                        addEmojiSticker(emoji, isPremium)
                        // ✅ [에러 수정] 정의되지 않은 v_vibrate 대신 표준 하이브틱 피드백 사용
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
            }
            val tv = TextView(this).apply {
                text = emoji
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(tv)

            // ✨ [프리미엄 자물쇠 배지]
            if (isPremium) {
                val lockView = TextView(this).apply {
                    text = "🔒"
                    textSize = 8f
                    alpha = 0.7f
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    }
                }
                frame.addView(lockView)
            }

            return frame
        }

        // 🔄 [코부장 스티커 정렬 최적화]
        // 비슷한 것끼리 묶어서 사용자가 찾기 쉽게 테마별 카테고리 정렬을 수행합니다.

        // 1. 하트 & 사랑 (Love & Emotion)
        val sortedLove = vvipHeartEmojis + listOf("🤍", "💌", "💝")

        // 2. 별, 달 & 날씨 (Celestial & Nature)
        val sortedNature = vvipNeonEmojis + listOf("☀️", "🌙", "⭐", "☁️", "⚡", "🌈")

        // 3. 꽃 & 식물 (Flowers & Plants)
        val sortedFlora = listOf("🌸", "💮", "🌻", "🌷", "🌹", "🍀", "🌵", "🌲", "🍁", "🍄")

        // 4. 동물 & 귀여움 (Animals & Cute)
        val sortedAnimals = listOf("🐾", "🐱", "🐶", "🐥", "🐝", "🦋", "🦢", "🧸", "🐰", "🐣")

        // 5. 음식 & 디저트 (Food & Cafe)
        val sortedFood = vvipWatercolorEmojis + listOf("☕", "🍰", "🧁", "🍎", "🍓", "🍒", "🍇", "🍷", "🥂", "🎂")

        // 6. 여행 & 오브제 (Travel & Objects)
        val sortedObjects = listOf("✈️", "🚀", "📷", "🎈", "🎁", "🎀", "📍", "✂️", "🎨", "🎭", "🎪", "🎡")

        // 정렬된 리스트를 하나로 합침 (중복 제거 포함)
        val consolidatedBasic = (sortedLove + sortedNature + sortedFlora + sortedAnimals + sortedFood + sortedObjects).distinct()

        // Helper to add lettering sticker
        fun createLetteringIcon(textStr: String, isPremium: Boolean): View {
            val frame = FrameLayout(this).apply {
                val params = android.view.ViewGroup.MarginLayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    (40 * resources.displayMetrics.density).toInt()
                )
                params.setMargins(
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt()
                )
                layoutParams = params

                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = getDrawable(outValue.resourceId)

                setOnClickListener {
                    Log.d("STICKER_CLICK", "Lettering clicked: $textStr. isPremium: $isPremium, purchased: $isPremiumPurchased")
                    if (isPremium && !isPremiumPurchased) {
                        showPremiumBillingDialog()
                    } else {
                        addLetteringSticker(textStr, isPremium)
                        try { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } catch(e: Exception){}
                    }
                }
            }
            val tv = TextView(this).apply {
                text = textStr
                textSize = 18f
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
                setTextColor(Color.parseColor("#FFF5C3"))
                paint.isFakeBoldText = true
                setShadowLayer(4f, 2f, 2f, Color.parseColor("#AA000000"))
                gravity = android.view.Gravity.CENTER
                setPadding((12 * resources.displayMetrics.density).toInt(), 0, (12 * resources.displayMetrics.density).toInt(), 0)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(tv)

            // ✨ [프리미엄 자물쇠 배지]
            if (isPremium) {
                val lockView = TextView(this).apply {
                    text = "🔒"
                    textSize = 7f // 레터링은 옆으로 기니까 조금 더 작게
                    alpha = 0.6f
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END // 레터링은 우측 상단이 나을 수 있음
                    }
                }
                frame.addView(lockView)
            }

            return frame
        }

        // --- [Basic Stickers Section] ---
        // 1. Initial Tape & Sorted Emojis
        basicContainer.addView(createDrawableIcon(R.drawable.ic_sticker_tape, 60, false))
        consolidatedBasic.forEach { basicContainer.addView(createEmojiIcon(it, false)) }

        // 2. Text Letterings (Keep sorted by length)
        (premiumLetterings + vvipLetterings).sortedBy { it.length }.forEach {
            basicContainer.addView(createLetteringIcon(it, false))
        }

        // --- [Premium Stickers Section] ---
        // 👑 NEW: High-Quality 3D Opaque Stickers
        // 👑 NEW: High-Quality 3D Premium Stickers (Sorted by Theme)
        val premium3DStickers = listOf(
            // 🚀 탈것 & 우주 (Vehicles & Space) - 최상단 배치
            //R.drawable.img_premium_ufo,
            //R.drawable.img_premium_henry,
            //R.drawable.img_premium_jet,
            //R.drawable.stk_premium_airplane,
           // R.drawable.img_premium_rocket,
           // R.drawable.img_premium_balloon,

            // 🐶 동물 & 캐릭터 (Animals & Creatures)
            R.drawable.stk_premium_puppy,
            R.drawable.cat,
            R.drawable.koala,
            R.drawable.rabbit,
            R.drawable.poo,
            R.drawable.img_premium_sudal,
            R.drawable.img_premium_siba,
            R.drawable.img_premium_fox,
            R.drawable.img_premium_pender,
            R.drawable.img_premium_hamster,
            //R.drawable.stk_premium_bear,
            R.drawable.stk_premium_penguin,
            R.drawable.img_premium_dog2,
            R.drawable.img_premium_rabbit2,
            R.drawable.img_premium_cat2,
            R.drawable.stk_premium_swan,
            R.drawable.stk_premium_chick,
            R.drawable.stk_premium_cat_paw,
            R.drawable.stk_premium_bee,
            R.drawable.stk_premium_butterfly,
            R.drawable.milk,
            R.drawable.suninjang,

            // 💎 감성 소품 (Romantic Objects)
            R.drawable.stk_premium_heart_red,
            R.drawable.heate1,
            R.drawable.heate2,
            R.drawable.heate3,
            R.drawable.stk_premium_white_heart,
            R.drawable.stk_premium_camera,
            R.drawable.img_premium_magicbong,
            R.drawable.img_premium_muffine,
            R.drawable.img_premium_crown,
            R.drawable.img_premium_flower,
            R.drawable.img_premium_donnut,
            R.drawable.img_premium_riborn,
            R.drawable.img_premium_moon,
            R.drawable.stk_premium_diamond,
            R.drawable.stk_premium_crown,
            R.drawable.stk_premium_ring,
            R.drawable.stk_premium_champagne,
            R.drawable.stk_premium_wine,
            R.drawable.stk_premium_music,
            R.drawable.stk_premium_violin,
            R.drawable.stk_premium_palette,
            R.drawable.stk_premium_cake,
            R.drawable.stk_premium_coffee,
            R.drawable.shup,
            R.drawable.jogae,
            R.drawable.jamul,
            R.drawable.snow,
            R.drawable.hyang,
            R.drawable.kiss,
            R.drawable.bottle,
            R.drawable.loveshot,
            R.drawable.key,
            R.drawable.stk_premium_strawberry,
            R.drawable.stk_premium_cherry,
            R.drawable.stk_premium_apple,
            R.drawable.stk_premium_rose,
            R.drawable.stk_premium_sakura,
            R.drawable.stk_premium_sunflower,
            R.drawable.stk_premium_clover,
            R.drawable.stk_premium_maple_leaf,
            R.drawable.stk_premium_rainbow,
            R.drawable.stk_premium_cloud,
            R.drawable.stk_premium_sun,
            R.drawable.stk_premium_moon,
            R.drawable.stk_premium_star,
            R.drawable.stk_premium_letter,
            R.drawable.stk_premium_gift,
            R.drawable.stk_premium_ribbon,
            R.drawable.stk_premium_magic,
            R.drawable.stk_premium_sparkle,
            R.drawable.stk_premium_map_pin,
            R.drawable.stk_premium_scissors
        )
        premium3DStickers.forEach { resId ->
            premiumContainer.addView(createDrawableIcon(resId, 44, true))
        }
    }

    private fun showPremiumBillingDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .create()

        val dialogView = layoutInflater.inflate(R.layout.dialog_premium_billing, null)
        dialog.setView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_purchase_premium).setOnClickListener {
            billingManager.launchPurchaseFlow()
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_close_billing).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCardPreview() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        container.removeAllViews()

        val cardView = layoutInflater.inflate(
            R.layout.item_memory_card_04,
            container,
            false
        )

        cardView.findViewById<ImageView>(R.id.card_image).setImageURI(photoUri)
        cardView.findViewById<TextView>(R.id.card_address).text = address
        val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREA).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
        }
        cardView.findViewById<TextView>(R.id.card_date).text = sdf.format(Date())

        // Capture the beautiful Kyobo hand font securely from the XML inflated view
        if (calligraphyFont == null) {
            calligraphyFont = cardView.findViewById<TextView>(R.id.card_message).typeface
        }

        // RESET default fonts to non-italic calligraphy (the one loaded from XML)
        val defaultTypeface = calligraphyFont
        cardView.findViewById<TextView>(R.id.card_message).typeface = defaultTypeface
        cardView.findViewById<TextView>(R.id.card_address).typeface = defaultTypeface
        cardView.findViewById<TextView>(R.id.card_date).typeface = defaultTypeface
        cardView.findViewById<TextView>(R.id.card_watermark)?.typeface = defaultTypeface

        container.addView(cardView)
        updateCardMessageText()

        // 🛡️ [비공개 테스트 모드] 에디터 내 카드 단순화
        if (AppConfig.IS_TEST_MODE) {
            cardView.findViewById<View>(R.id.card_rating_container)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.card_qr_code)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.card_watermark)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.card_premium_border)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.card_premium_bg)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.sticker_container)?.visibility = View.GONE
            cardView.findViewById<View>(R.id.card_drawing_view)?.visibility = View.GONE
        } else {
            showQRCodeOnStickerLayer(cardView)
        }

        // ⭐ 감성 별점 (Rating) 로직
        val star1 = cardView.findViewById<ImageView>(R.id.star_1)
        val star2 = cardView.findViewById<ImageView>(R.id.star_2)
        val star3 = cardView.findViewById<ImageView>(R.id.star_3)

        fun updateStars() {
            val emptyRes = R.drawable.ic_star_rate_empty
            val filledRes = R.drawable.ic_star_rate_filled

            star1.setImageResource(if (currentRating >= 1) filledRes else emptyRes)
            star2.setImageResource(if (currentRating >= 2) filledRes else emptyRes)
            star3.setImageResource(if (currentRating >= 3) filledRes else emptyRes)

            val goldColor = Color.parseColor("#D4AF37") // 🏆 샴페인 골드로 대통합!

            star1.colorFilter = null
            star2.colorFilter = null
            star3.colorFilter = null

            if (currentRating >= 1) star1.setColorFilter(goldColor)
            if (currentRating >= 2) star2.setColorFilter(goldColor)
            if (currentRating >= 3) star3.setColorFilter(goldColor)
        }

        star1?.setOnClickListener { currentRating = if (currentRating == 1) 0 else 1; updateStars() }
        star2?.setOnClickListener { currentRating = 2; updateStars() }
        star3?.setOnClickListener { currentRating = 3; updateStars() }

        updateStars() // 초기 렌더링

        // --- 📏 PERFECT FIT SCALING ---
        // Automatically scale the card to fill the workspace perfectly without clipping.
        val workspace = findViewById<View>(R.id.card_workspace)
        workspace.post {
            val cardW = cardView.width.toFloat()
            val cardH = cardView.height.toFloat()

            val wsW = (workspace.width - workspace.paddingLeft - workspace.paddingRight).toFloat()
            val wsH = (workspace.height - workspace.paddingTop - workspace.paddingBottom).toFloat()

            if (cardW > 0 && cardH > 0 && wsW > 0 && wsH > 0) {
                val scaleW = wsW / cardW
                val scaleH = wsH / cardH
                val targetScale = minOf(scaleW, scaleH) * 0.96f // 4% safety margin

                container.scaleX = targetScale
                container.scaleY = targetScale
                container.pivotX = cardW / 2f
                container.pivotY = cardH / 2f
            }
        }

        val stickerLayer = cardView.findViewById<View>(R.id.sticker_container)
        val ratingContainer = cardView.findViewById<View>(R.id.card_rating_container)

        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                clearStickerSelection()
                return true
            }
        })

        stickerLayer?.setOnTouchListener { _, event ->
            // 🚀 [코부장 정밀 타격] 터치 지점이 '설렘지수' 영역 안인지 체크합니다.
            if (ratingContainer != null && event.action == android.view.MotionEvent.ACTION_DOWN) {
                val location = IntArray(2)
                ratingContainer.getLocationOnScreen(location)
                val rect = android.graphics.Rect(
                    location[0],
                    location[1],
                    location[0] + ratingContainer.width,
                    location[1] + ratingContainer.height
                )

                if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    // 별점 영역이면 터치를 가로채지 않고 아래(별점 컨테이너)로 흘려보냅니다!
                    return@setOnTouchListener false
                }
            }

            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showQRCodeOnStickerLayer(cardView: View) {
        val qrView = cardView.findViewById<ImageView>(R.id.card_qr_code) ?: return

        // 💡 [코부장 최적화] 메인 화면과 동일하게 비동기 스레드에서 생성합니다.
        kotlin.concurrent.thread {
            try {
                // 💡 [코부장 배포 최적화] 새로운 블랙&골드 랜딩 페이지로 연결합니다.
                val link = "https://hnoni777.github.io/newdatemapdiary/"
                val qrBitmap = generateQRCode(link)

                runOnUiThread {
                    if (qrBitmap != null) {
                        qrView.setImageBitmap(qrBitmap)
                        qrView.visibility = View.VISIBLE
                        Log.d("QR_CODE", "Optimized QR Added to editor: $link")
                    } else {
                        qrView.visibility = View.INVISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("QR_CODE", "Failed to add QR", e)
            }
        }
    }

    private fun updateCardMessageText() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount > 0) {
            val cardView = container.getChildAt(0)
            val userMessage = editCardMessage.text.toString()
            cardView.findViewById<TextView>(R.id.card_message).apply {
                text = if (userMessage.isNotEmpty()) userMessage else "오늘의 로맨틱한 순간"
                // 🖋️ Improve Typography Quality
                paint.isAntiAlias = true
                paint.isSubpixelText = true
                setShadowLayer(1.5f, 0.5f, 0.5f, Color.parseColor("#20000000")) // Subtle lift
                letterSpacing = 0.02f
            }
        }
    }


    private fun applyCardEffect(effect: String) {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount == 0) return
        val cardView = container.getChildAt(0) as androidx.cardview.widget.CardView
        val contentLayout = cardView.findViewById<LinearLayout>(R.id.card_content_layout)
        val textLayout = cardView.findViewById<LinearLayout>(R.id.card_text_layout)
        val cardMessage = cardView.findViewById<TextView>(R.id.card_message)
        val cardAddress = cardView.findViewById<TextView>(R.id.card_address)
        val cardDate = cardView.findViewById<TextView>(R.id.card_date)
        val cardWatermark = cardView.findViewById<TextView>(R.id.card_watermark)
        val cardImage = cardView.findViewById<ImageView>(R.id.card_image)
        val skinOverlay = cardView.findViewById<ImageView>(R.id.card_skin_overlay)

        // 💎 [Premium Optimization] Reset skin overlay visibility for all themes by default
        skinOverlay?.visibility = View.GONE

        when (effect) {
            "basic" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "basic"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.WHITE)
                textLayout.setBackgroundColor(Color.TRANSPARENT)

                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)

                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE
                premiumBorder?.setBackgroundResource(0)

                cardView.setCardBackgroundColor(Color.WHITE)
                cardView.cardElevation = 24 * resources.displayMetrics.density

                // Remove BW filter
                cardImage.colorFilter = null

                // Reset to default premium font (non-italic calligraphy)
                val dtFont = calligraphyFont
                if (dtFont != null) {
                    val normalHand = android.graphics.Typeface.create(dtFont, android.graphics.Typeface.NORMAL)
                    cardMessage.typeface = normalHand
                    cardAddress.typeface = normalHand
                    cardDate.typeface = normalHand
                    cardWatermark?.typeface = normalHand
                } else {
                    cardMessage.typeface = android.graphics.Typeface.DEFAULT
                }

                skinOverlay?.visibility = View.GONE
                stopSakuraEffect()
            }
            "oatmeal" -> {
                cardMessage.setTextColor(Color.parseColor("#5D4037"))
                cardAddress.setTextColor(Color.parseColor("#8D6E63"))
                cardDate.setTextColor(Color.parseColor("#A1887F"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A5D4037")) } catch(e: Exception){}
                contentLayout.tag = "oatmeal"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.parseColor("#F8F5F0"))
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.parseColor("#F8F5F0"))
                cardView.cardElevation = 24 * resources.displayMetrics.density
                cardImage.colorFilter = null
                stopSakuraEffect()
            }
            "matcha" -> {
                cardMessage.setTextColor(Color.parseColor("#2E4A34"))
                cardAddress.setTextColor(Color.parseColor("#5D8D6E"))
                cardDate.setTextColor(Color.parseColor("#8FA188"))
                cardWatermark?.setTextColor(Color.parseColor("#43A047"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A2E4A34")) } catch(e: Exception){}
                contentLayout.tag = "matcha"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.parseColor("#F0F8F1"))
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.parseColor("#F0F8F1"))
                cardView.cardElevation = 24 * resources.displayMetrics.density
                cardImage.colorFilter = null
                stopSakuraEffect()
            }
            "peach" -> {
                cardMessage.setTextColor(Color.parseColor("#8D4A4A"))
                cardAddress.setTextColor(Color.parseColor("#B36E6E"))
                cardDate.setTextColor(Color.parseColor("#C28888"))
                cardWatermark?.setTextColor(Color.parseColor("#E57373"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A8D4A4A")) } catch(e: Exception){}
                contentLayout.tag = "peach"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.parseColor("#FFF0F0"))
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.parseColor("#FFF0F0"))
                cardView.cardElevation = 24 * resources.displayMetrics.density
                cardImage.colorFilter = null
                stopSakuraEffect()
            }
            "lavender" -> {
                cardMessage.setTextColor(Color.parseColor("#4A345D"))
                cardAddress.setTextColor(Color.parseColor("#6E5D8D"))
                cardDate.setTextColor(Color.parseColor("#887FA1"))
                cardWatermark?.setTextColor(Color.parseColor("#9575CD"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A4A345D")) } catch(e: Exception){}
                contentLayout.tag = "lavender"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.parseColor("#F5F0F8"))
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.parseColor("#F5F0F8"))
                cardView.cardElevation = 24 * resources.displayMetrics.density
                cardImage.colorFilter = null
                stopSakuraEffect()
            }
            "charcoal" -> {
                cardMessage.setTextColor(Color.parseColor("#F0F5FA"))
                cardAddress.setTextColor(Color.parseColor("#A0AAB5"))
                cardDate.setTextColor(Color.parseColor("#7A8593"))
                cardWatermark?.setTextColor(Color.parseColor("#E0C69E"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")) } catch(e: Exception){}
                contentLayout.tag = "charcoal"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.parseColor("#1A1D24"))
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.parseColor("#1A1D24"))
                cardView.cardElevation = 24 * resources.displayMetrics.density
                cardImage.colorFilter = null
                stopSakuraEffect()
            }
            "vip" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "vip"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)

                // 💎 Apply High-End Texture from AI generation
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_vip_texture)

                // ✨ Apply Scalable Gold Border Overlay
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_vip_border)

                textLayout.setBackgroundColor(Color.TRANSPARENT)

                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "burgundy" -> {
                cardMessage.setTextColor(Color.parseColor("#FAFAF5"))
                cardAddress.setTextColor(Color.parseColor("#E0E0E0"))
                cardDate.setTextColor(Color.parseColor("#CCCCCC"))
                cardWatermark?.setTextColor(Color.parseColor("#E8D19F"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")) } catch(e: Exception){}

                contentLayout.tag = "burgundy"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_burgundy)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_burgundy_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "navy" -> {
                cardMessage.setTextColor(Color.parseColor("#FAFAF5"))
                cardAddress.setTextColor(Color.parseColor("#E0E0E0"))
                cardDate.setTextColor(Color.parseColor("#CCCCCC"))
                cardWatermark?.setTextColor(Color.parseColor("#E8D19F"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")) } catch(e: Exception){}

                contentLayout.tag = "navy"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_navy)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_navy_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "cream" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "cream"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_cream)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_cream_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "emerald" -> {
                cardMessage.setTextColor(Color.parseColor("#FAFAF5"))
                cardAddress.setTextColor(Color.parseColor("#E0E0E0"))
                cardDate.setTextColor(Color.parseColor("#CCCCCC"))
                cardWatermark?.setTextColor(Color.parseColor("#E8D19F"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")) } catch(e: Exception){}

                contentLayout.tag = "emerald"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_emerald)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_emerald_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "pearl" -> {
                cardMessage.setTextColor(Color.parseColor("#2C3A47"))
                cardAddress.setTextColor(Color.parseColor("#5A6B7C"))
                cardDate.setTextColor(Color.parseColor("#8A9CAE"))
                cardWatermark?.setTextColor(Color.parseColor("#9BA7B5"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A2C3A47")) } catch(e: Exception){}

                contentLayout.tag = "pearl"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_pearl)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_pearl_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "rosegold" -> {
                cardMessage.setTextColor(Color.parseColor("#5F3A3E"))
                cardAddress.setTextColor(Color.parseColor("#885B60"))
                cardDate.setTextColor(Color.parseColor("#A87E82"))
                cardWatermark?.setTextColor(Color.parseColor("#B76E79"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A5F3A3E")) } catch(e: Exception){}

                contentLayout.tag = "rosegold"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_rosegold)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_rosegold_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
                skinOverlay?.visibility = View.GONE
            }
            "midnight" -> {
                cardMessage.setTextColor(Color.parseColor("#FFFFFF"))
                cardAddress.setTextColor(Color.parseColor("#BBBBBB"))
                cardDate.setTextColor(Color.parseColor("#888888"))
                cardWatermark?.setTextColor(Color.parseColor("#E5E4E2"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4DEEFFFF")) } catch(e: Exception){}

                contentLayout.tag = "midnight"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_midnight)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_midnight_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
                skinOverlay?.visibility = View.GONE
                Toast.makeText(this, "🖤 압도적인 포스 미드나잇 플래티넘 테마 적용!", Toast.LENGTH_SHORT).show()
            }
            "premium_leather" -> {
                cardMessage.setTextColor(Color.parseColor("#3A2A1D")) // Dark leather brown text
                cardAddress.setTextColor(Color.parseColor("#7D6F63"))
                cardDate.setTextColor(Color.parseColor("#9E9287"))
                cardWatermark?.setTextColor(Color.parseColor("#C4A27A")) // Gold text for watermark
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A3A2A1D")) } catch(e: Exception){}

                contentLayout.tag = "premium_leather"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_leather)
                
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_leather_border)
                
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
                skinOverlay?.visibility = View.GONE
                Toast.makeText(this, "💼 한정판 화이트 레더 다이어리 적용!", Toast.LENGTH_SHORT).show()
            }
            "premium_glass" -> {
                cardMessage.setTextColor(Color.parseColor("#1A1A24")) // Navy dark text
                cardAddress.setTextColor(Color.parseColor("#666677"))
                cardDate.setTextColor(Color.parseColor("#888899"))
                cardWatermark?.setTextColor(Color.parseColor("#A3C2D1")) 
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#22FFFFFF")) } catch(e: Exception){}

                contentLayout.tag = "premium_glass"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_glass)
                
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_glass_border)
                
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
                skinOverlay?.visibility = View.GONE
                Toast.makeText(this, "🧊 한정판 프로스트 글래스 테마 적용!", Toast.LENGTH_SHORT).show()
            }
            "premium_film" -> {
                cardMessage.setTextColor(Color.parseColor("#F5E6CC")) // Warm vintage
                cardAddress.setTextColor(Color.parseColor("#C2A98A"))
                cardDate.setTextColor(Color.parseColor("#9E8362"))
                cardWatermark?.setTextColor(Color.parseColor("#FFD700")) // Gold foil
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33000000")) } catch(e: Exception){}

                contentLayout.tag = "premium_film"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_film)
                
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_film_border)
                
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
                skinOverlay?.visibility = View.GONE
                Toast.makeText(this, "🎞️ 한정판 빈티지 필름 골드 적용!", Toast.LENGTH_SHORT).show()
            }
            "purple" -> {
                cardMessage.setTextColor(Color.parseColor("#FAFAF5"))
                cardAddress.setTextColor(Color.parseColor("#E0E0E0"))
                cardDate.setTextColor(Color.parseColor("#CCCCCC"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")) } catch(e: Exception){}

                contentLayout.tag = "purple"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_prem_purple)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_prem_purple_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "dreamy" -> {
                cardMessage.setTextColor(Color.parseColor("#4A4A68"))
                cardAddress.setTextColor(Color.parseColor("#777799"))
                cardDate.setTextColor(Color.parseColor("#9999AA"))
                cardWatermark?.setTextColor(Color.parseColor("#8E8EAA"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A4A4A68")) } catch(e: Exception){}

                contentLayout.tag = "dreamy"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_new_dreamy)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_new_dreamy_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "brutalism" -> {
                cardMessage.setTextColor(Color.parseColor("#1A1A1A"))
                cardAddress.setTextColor(Color.parseColor("#333333"))
                cardDate.setTextColor(Color.parseColor("#555555"))
                cardWatermark?.setTextColor(Color.parseColor("#1A1A1A"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A1A1A1A")) } catch(e: Exception){}

                contentLayout.tag = "brutalism"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_new_brutalism)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_new_brutalism_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "ticket" -> {
                cardMessage.setTextColor(Color.parseColor("#5D4037"))
                cardAddress.setTextColor(Color.parseColor("#795548"))
                cardDate.setTextColor(Color.parseColor("#8D6E63"))
                cardWatermark?.setTextColor(Color.parseColor("#A1887F"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A5D4037")) } catch(e: Exception){}

                contentLayout.tag = "ticket"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_new_ticket)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_new_ticket_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "cyber" -> {
                cardMessage.setTextColor(Color.parseColor("#FFFFFF"))
                cardAddress.setTextColor(Color.parseColor("#E0E0E0"))
                cardDate.setTextColor(Color.parseColor("#CCCCCC"))
                cardWatermark?.setTextColor(Color.parseColor("#00FFFF"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4DFF00FF")) } catch(e: Exception){}

                contentLayout.tag = "cyber"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(android.R.color.transparent)
                premiumBg?.setBackgroundResource(R.drawable.bg_new_cyber)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_new_cyber_border)
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "letter" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "letter"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)

                // 📜 Apply Vintage Letter Texture
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_letter_texture)

                // 🖋️ Apply Classic Letter Border Overlay
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.VISIBLE
                premiumBorder?.setBackgroundResource(R.drawable.bg_letter_border)

                textLayout.setBackgroundColor(Color.TRANSPARENT)

                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "cute" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "cute"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)

                // 🌸 Apply Cute Floral Texture
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_cute_floral)

                // No sharp gold border for cute theme, maybe a soft glow or nothing
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE

                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "heart" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "heart"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_cute_heart)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "starry" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "starry"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_cute_starry)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "cat" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "cat"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_cute_cat)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "dessert" -> {
                cardMessage.setTextColor(Color.parseColor("#221018"))
                cardAddress.setTextColor(Color.parseColor("#666666"))
                cardDate.setTextColor(Color.parseColor("#999999"))
                cardWatermark?.setTextColor(Color.parseColor("#D4AF37"))
                try { (cardAddress.parent as? View)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A221018")) } catch(e: Exception){}

                contentLayout.tag = "dessert"
                val defaultPadding = (24 * resources.displayMetrics.density).toInt()
                contentLayout.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding)
                contentLayout.setBackgroundColor(Color.TRANSPARENT)
                val premiumBg = cardView.findViewById<ImageView>(R.id.card_premium_bg)
                premiumBg?.setImageResource(R.drawable.bg_cute_dessert)
                val premiumBorder = cardView.findViewById<View>(R.id.card_premium_border)
                premiumBorder?.visibility = View.GONE
                textLayout.setBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.cardElevation = 24 * resources.displayMetrics.density
            }
            "bw" -> {
                val matrix = android.graphics.ColorMatrix()
                matrix.setSaturation(0f)
                cardImage.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            }
            "sakura" -> {
                if (sakuraRunnable == null) {
                    startSakuraEffect()
                }
            }
        }

        contentLayout.requestLayout()
        cardView.requestLayout()
        contentLayout.invalidate()
        cardView.invalidate()
    }

    private fun startSakuraEffect() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView ?: return
        val stickerLayer = cardView.findViewById<ViewGroup>(R.id.sticker_container) ?: return

        // Remove existing first to avoid duplication
        stopSakuraEffect()

        stickerLayer.post {
            val w = if (stickerLayer.width > 0) stickerLayer.width.toFloat() else (resources.displayMetrics.widthPixels).toFloat()
            val h = if (stickerLayer.height > 0) stickerLayer.height.toFloat() else 1400f

            // Fixed aesthetic positions for 8 beautiful sakura elements
            val positions = listOf(
                Pair(0.05f, 0.05f), Pair(0.85f, 0.08f), Pair(0.9f, 0.4f),
                Pair(0.06f, 0.5f), Pair(0.83f, 0.8f), Pair(0.1f, 0.88f),
                Pair(0.45f, 0.94f), Pair(0.55f, 0.02f)
            )

            val sizes = listOf(36f, 30f, 26f, 22f, 40f, 32f, 24f, 28f)
            val rotations = listOf(15f, -20f, 45f, -10f, 30f, -45f, 10f, 60f)

            for (i in positions.indices) {
                val petal = TextView(this).apply {
                    text = "🌸"
                    textSize = sizes[i]
                    alpha = 0.9f
                    rotation = rotations[i]
                    tag = "sakura_filter"
                    isClickable = false
                    isFocusable = false
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                }

                petal.x = (w * positions[i].first)
                petal.y = (h * positions[i].second)

                stickerLayer.addView(petal)
            }

            // Dummy runnable so the system knows the effect is "on"
            sakuraRunnable = Runnable { }
        }
    }

    private fun stopSakuraEffect() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount == 0) return
        val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView ?: return
        val stickerLayer = cardView.findViewById<ViewGroup>(R.id.sticker_container) ?: return

        val toRemove = mutableListOf<View>()
        for (i in 0 until stickerLayer.childCount) {
            val v = stickerLayer.getChildAt(i)
            if (v.tag == "sakura_filter") {
                toRemove.add(v)
            }
        }
        for (v in toRemove) {
            stickerLayer.removeView(v)
        }

        sakuraRunnable?.let { sakuraHandler.removeCallbacks(it) }
        sakuraRunnable = null
    }

    private fun selectSticker(stickerView: View) {
        currentSelectedSticker = stickerView
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
        val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

        for (i in 0 until stickerLayer.childCount) {
            val wrapper = stickerLayer.getChildAt(i) as? ViewGroup
            if (wrapper != null) {
                if (wrapper == stickerView) {
                    wrapper.setBackgroundResource(R.drawable.bg_sticker_selected)
                    wrapper.getChildAt(1).visibility = View.VISIBLE
                    wrapper.bringToFront()
                } else {
                    wrapper.setBackgroundResource(0)
                    wrapper.getChildAt(1).visibility = View.GONE
                }
            }
        }
    }

    private fun clearStickerSelection() {
        currentSelectedSticker = null
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
        val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

        for (i in 0 until stickerLayer.childCount) {
            val wrapper = stickerLayer.getChildAt(i) as? ViewGroup
            if (wrapper != null) {
                wrapper.setBackgroundResource(0)
                wrapper.getChildAt(1).visibility = View.GONE
            }
        }
    }

    private fun spacing(event: android.view.MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun rotation(event: android.view.MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val deltaX = (event.getX(0) - event.getX(1)).toDouble()
        val deltaY = (event.getY(0) - event.getY(1)).toDouble()
        val radians = Math.atan2(deltaY, deltaX)
        return Math.toDegrees(radians).toFloat()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val sticker = currentSelectedSticker
        if (sticker != null && event.pointerCount >= 2) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        initialDistance = spacing(event)
                        initialRotation = rotation(event) - sticker.rotation
                        scaleFactor = sticker.scaleX
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        val newDistance = spacing(event)
                        val newRotation = rotation(event)

                        if (initialDistance > 0 && newDistance > 0) {
                            var newScale = scaleFactor * (newDistance / initialDistance)
                            newScale = Math.max(0.3f, Math.min(newScale, 5.0f))
                            sticker.scaleX = newScale
                            sticker.scaleY = newScale
                        }
                        sticker.rotation = newRotation - initialRotation
                    }
                }
            }
            super.dispatchTouchEvent(event)
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun addOrToggleSticker(tag: String, resId: Int, color: Int, isPremium: Boolean = false) {
        try {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
                val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

                val stickerWrapper = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(150, 450, 0, 0)
                    }
                }

                val stickerImage = ImageView(this).apply {
                    setImageResource(resId)
                    val iconSize = if (isPremium) 160 else 100
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                        setMargins(30, 30, 30, 30)
                    }
                    if (color != 0) setColorFilter(color)
                }

                val closeButton = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    setBackgroundResource(R.drawable.bg_romantic_button)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    setColorFilter(Color.WHITE)
                    visibility = View.VISIBLE
                    setOnClickListener {
                        stickerLayer.removeView(stickerWrapper)
                        if (currentSelectedSticker == stickerWrapper) {
                            currentSelectedSticker = null
                        }
                    }
                }

                stickerWrapper.addView(stickerImage)
                stickerWrapper.addView(closeButton)

                var dX = 0f
                var dY = 0f
                val finalScale = container.scaleX
                stickerWrapper.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            selectSticker(view)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            dX = view.x - event.rawX / finalScale
                            dY = view.y - event.rawY / finalScale
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                view.x = (event.rawX / finalScale) + dX
                                view.y = (event.rawY / finalScale) + dY
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }

                stickerLayer.addView(stickerWrapper)
                selectSticker(stickerWrapper)
            }
        } catch (e: Exception) {
            Log.e("STICKER_ERROR", "Crash prevented: ${e.message}")
        }
    }

    private fun addEmojiSticker(emoji: String, isPremium: Boolean = false) {
        try {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
                val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

                val stickerWrapper = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(150, 450, 0, 0)
                    }
                }

                //  emojiStackContainer를 만들어 3중 중첩 적용 (Index 0)
                val emojiStackContainer = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    for (i in 0 until 3) {
                        val layer = TextView(this@CardEditorActivity).apply {
                            text = emoji
                            textSize = if (isPremium) 62f else 40f
                            gravity = android.view.Gravity.CENTER
                            paint.isFakeBoldText = true
                            if (i == 0) {
                                setShadowLayer(8f, 0f, 0f, Color.parseColor("#CC000000"))
                            }
                            val pad = (8 * resources.displayMetrics.density).toInt()
                            setPadding(pad, pad, pad, pad)
                        }
                        addView(layer)
                    }
                }

                val closeButton = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    setBackgroundResource(R.drawable.bg_romantic_button)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    setColorFilter(Color.WHITE)
                    visibility = View.VISIBLE
                    setOnClickListener {
                        stickerLayer.removeView(stickerWrapper)
                        if (currentSelectedSticker == stickerWrapper) {
                            currentSelectedSticker = null
                        }
                    }
                }

                stickerWrapper.addView(emojiStackContainer) // Index 0
                stickerWrapper.addView(closeButton)        // Index 1

                var dX = 0f
                var dY = 0f
                val finalScale = container.scaleX
                stickerWrapper.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            selectSticker(view)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            dX = view.x - event.rawX / finalScale
                            dY = view.y - event.rawY / finalScale
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                view.x = (event.rawX / finalScale) + dX
                                view.y = (event.rawY / finalScale) + dY
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }

                stickerLayer.addView(stickerWrapper)
                selectSticker(stickerWrapper)
            }
        } catch (e: Exception) {
            Log.e("STICKER_ERROR", "Crash prevented: ${e.message}")
        }
    }

    private fun addLetteringSticker(textStr: String, isPremium: Boolean = false) {
        try {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
                val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

                val stickerWrapper = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(150, 450, 0, 0)
                    }
                }

                // 🚀 [레터링 불투명화: 3중 중첩 적용]
                val letteringStackContainer = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    for (i in 0 until 3) {
                        val layer = TextView(this@CardEditorActivity).apply {
                            text = textStr
                            textSize = if (isPremium) 45f else 30f
                            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
                            gravity = android.view.Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(30, 30, 30, 30)
                            }
                            paint.isFakeBoldText = true
                            paint.isAntiAlias = true
                            paint.isSubpixelText = true
                            if (i == 0) {
                                setShadowLayer(8f, 2f, 2f, Color.parseColor("#99000000"))
                            }
                        }
                        addView(layer)

                        // Apply shiny gold gradient to each layer after it measures
                        layer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                layer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                val shader = android.graphics.LinearGradient(
                                    0f, 0f, 0f, layer.height.toFloat(),
                                    intArrayOf(Color.parseColor("#FFF5C3"), Color.parseColor("#D4AF37"), Color.parseColor("#AA7A00")),
                                    floatArrayOf(0f, 0.5f, 1f),
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                layer.paint.shader = shader
                            }
                        })
                    }
                }

                val closeButton = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    setBackgroundResource(R.drawable.bg_romantic_button)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    setColorFilter(Color.WHITE)
                    visibility = View.VISIBLE
                    setOnClickListener {
                        stickerLayer.removeView(stickerWrapper)
                        if (currentSelectedSticker == stickerWrapper) {
                            currentSelectedSticker = null
                        }
                    }
                }

                stickerWrapper.addView(letteringStackContainer) // Index 0
                stickerWrapper.addView(closeButton)           // Index 1

                var dX = 0f
                var dY = 0f
                val finalScale = container.scaleX
                stickerWrapper.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            selectSticker(view)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            dX = view.x - event.rawX / finalScale
                            dY = view.y - event.rawY / finalScale
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                view.x = (event.rawX / finalScale) + dX
                                view.y = (event.rawY / finalScale) + dY
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }

                stickerLayer.addView(stickerWrapper)
                selectSticker(stickerWrapper)
            }
        } catch (e: Exception) {
            Log.e("STICKER_ERROR", "Crash prevented: ${e.message}")
        }
    }

    private fun takeScreenshot(shareAfter: Boolean) {
        clearStickerSelection()

        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount == 0) return
        val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView ?: return

        // --- 🚀 ULTRA-HIGH RESOLUTION CAPTURE 🚀 ---
        // 뭉개짐(Mangling) 해결을 위해 화면 해상도보다 훨씬 높은 해상도로 렌더링합니다.
        val qualityScale = 2.5f
        val exportWidth = (cardView.width * qualityScale).toInt()
        val exportHeight = (cardView.height * qualityScale).toInt()

        if (exportWidth <= 0 || exportHeight <= 0) return

        val bitmap = Bitmap.createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Settings for high quality
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(qualityScale, qualityScale)

        // 🔲 사장님 요청: 외각은 곡선(라운딩) 없이 직각으로 깔끔하게 출력!
        cardView.draw(canvas)
        val savedUri = saveBitmapToGallery(bitmap, lat, lng, address)
        if (savedUri != null) {
            val dbHelper = MemoryDatabaseHelper(this)
            val memory = Memory(
                photoUri = savedUri.toString(),
                address = address.trim(),
                lat = lat,
                lng = lng,
                date = System.currentTimeMillis(),
                rating = currentRating,
                profileSticker = chosenMarkerProfileFilename ?: ProfileStickerManager.getSelectedProfileFilename(this)
            )
            try {
                // DB에 추억 저장 (내 추억지도용)
                dbHelper.insertMemory(memory)
                Log.d("DB_INSERT", "내 추억지도 저장 성공: ${address.trim()}")
            } catch (e: Exception) {
                Log.e("DB_INSERT", "내 추억지도 저장 실패", e)
            }

            if (shareAfter) {
                // 💡 [대표님 지시] 지도에서와 동일한 공유 선택창 표시
                showShareSelectionDialog(memory, savedUri)
            } else {
                val msg = if (AppConfig.IS_TEST_MODE) "보관함에 저장되었습니다! ✨" else "갤러리 및 추억지도에 저장되었습니다! ✨"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showShareSelectionDialog(memory: Memory, imageUri: Uri) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_share_selection, null)
        
        // 카드 이미지만 공유
        view.findViewById<View>(R.id.btn_share_card_direct).setOnClickListener {
            dialog.dismiss()
            shareImage(imageUri, memory.lat, memory.lng, memory.address ?: "")
        }
        
        // 🛡️ [비공개 테스트 모드] 상대방 지도에 흔적 남기기(카카오 공유) 숨기기
        if (AppConfig.IS_TEST_MODE) {
            view.findViewById<View>(R.id.btn_share_pin_direct)?.visibility = View.GONE
        } else {
            // 카카오 지도로 보내기 (핀 포함)
            view.findViewById<View>(R.id.btn_share_pin_direct).setOnClickListener {
                dialog.dismiss()
                shareToLoverViaKakao(memory)
            }
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, lat: Double, lng: Double, address: String): Uri? {
        try {
            val filename = "DateMapDiary_Card_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HereWithYou")
            }

            val tempFile = java.io.File(cacheDir, "temp_card_exif.jpg")
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // Exif Metadata Injector 🕵️‍♂️
            try {
                val exif = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                val encodedAddr = java.net.URLEncoder.encode(address, "UTF-8")
                val sender = java.net.URLEncoder.encode(ProfileStickerManager.getMyName(this), "UTF-8")
                val receiver = java.net.URLEncoder.encode(ProfileStickerManager.getPartnerName(this), "UTF-8")
                val profile = chosenMarkerProfileFilename ?: ProfileStickerManager.getSelectedProfileFilename(this) ?: ""
                val jsonMeta = "{\"lat\":$lat, \"lng\":$lng, \"addr\":\"$encodedAddr\", \"sender\":\"$sender\", \"receiver\":\"$receiver\", \"profile\":\"$profile\"}"
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, jsonMeta)
                exif.saveAttributes()
            } catch (e: Exception) {
                Log.e("EXIF", "Metadata injection failed", e)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("MediaStore insert failed")
            contentResolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(tempFile).use { input ->
                    input.copyTo(out)
                }
            }
            tempFile.delete()

            return uri
        } catch (e: Exception) {
            Log.e("SCREENSHOT", e.toString())
            Toast.makeText(this, "스샷 저장 실패", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun generateQRCode(url: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            // 💡 [코부장 최적화] 256x256 크기로 줄이고, setPixels로 속도를 대폭 향상
            val bitMatrix = writer.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                256,
                256,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.TRANSPARENT
                }
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            bmp
        } catch (e: Exception) {
            Log.e("QR_GEN", "Error", e)
            null
        }
    }

    private fun shareImage(uri: Uri, lat: Double, lng: Double, address: String) {
        try {
            // 💡 깔끔하게 카드 이미지만 공유
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(null, uri)
            }
            startActivity(Intent.createChooser(shareIntent, "추억 카드 공유하기"))
        } catch (e: Exception) {
            Log.e("ShareError", "공유 중 에러 발생: ${e.message}")
            Toast.makeText(this, "공유를 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processFaceSticker(uri: Uri) {
        try {
            val loadingToast = Toast.makeText(this, "📸 얼굴 분석 중... 잠시만 기다려주세요!", Toast.LENGTH_SHORT)
            loadingToast.show()

            // Load Bitmap from URI
            val inputStream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original == null) {
                Toast.makeText(this, "이미지를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                return
            }

            // 🔥 [회전 보정] 사진의 EXIF 정보를 읽어서 똑바로 세움
            val rotationInputStream = contentResolver.openInputStream(uri)
            val exif = rotationInputStream?.let { androidx.exifinterface.media.ExifInterface(it) }
            val orientation = exif?.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
            rotationInputStream?.close()

            val matrix = Matrix()
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            
            val rotatedBitmap = if (orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL) {
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } else original

            // Downscale for faster processing if too large
            val maxDim = 1000
            val scaledOriginal = if (rotatedBitmap.width > maxDim || rotatedBitmap.height > maxDim) {
                val ratio = rotatedBitmap.width.toFloat() / rotatedBitmap.height
                val newW = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                val newH = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                Bitmap.createScaledBitmap(rotatedBitmap, newW, newH, true)
            } else rotatedBitmap

            FaceStickerUtil.createFaceSticker(scaledOriginal) { stickerBitmap ->
                runOnUiThread {
                    loadingToast.cancel()
                    if (stickerBitmap != null) {
                        addFaceSticker(stickerBitmap)
                        Toast.makeText(this, "🎉 나만의 얼굴 스티커 탄생!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "얼굴을 찾지 못했거나 분석에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FACE_STICKER", "Error processing sticker", e)
            Toast.makeText(this, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFaceSticker(bitmap: Bitmap) {
        try {
            val container = findViewById<FrameLayout>(R.id.card_preview_container)
            if (container.childCount > 0) {
                val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
                val stickerLayer = cardView?.findViewById<ViewGroup>(R.id.sticker_container) ?: return

                val stickerWrapper = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(150, 450, 0, 0)
                    }
                }

                // Main Image
                val imgView = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    val size = (100 * resources.displayMetrics.density).toInt() // Initial size
                    layoutParams = FrameLayout.LayoutParams(size, size).apply {
                        setMargins(30, 30, 30, 30)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                val closeButton = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    setBackgroundResource(R.drawable.bg_romantic_button)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        stickerLayer.removeView(stickerWrapper)
                        if (currentSelectedSticker == stickerWrapper) {
                            currentSelectedSticker = null
                        }
                    }
                }

                stickerWrapper.addView(imgView)
                stickerWrapper.addView(closeButton)

                var dX = 0f
                var dY = 0f
                val finalScale = container.scaleX
                stickerWrapper.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            selectSticker(view)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            dX = view.x - event.rawX / finalScale
                            dY = view.y - event.rawY / finalScale
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                view.x = (event.rawX / finalScale) + dX
                                view.y = (event.rawY / finalScale) + dY
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    true
                }

                stickerLayer.addView(stickerWrapper)
                selectSticker(stickerWrapper)
            }
        } catch (e: Exception) {
            Log.e("STICKER_ERROR", "Face sticker add failed: ${e.message}")
        }
    }

    private fun setupMarkerProfileList() {
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_editor_profile_list)
        val profiles = ProfileStickerManager.getProfileStickers(this)
        
        // 💑 "기본 핀"과 현재 저장된 프로필들을 합침
        val items = mutableListOf<String?>(null) // null 이면 '전역 설정 따름' 또는 '기본'
        profiles.forEach { items.add(it.name) }

        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_profile_sticker, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val filename = items[position]
                val img = holder.itemView.findViewById<android.widget.ImageView>(R.id.img_profile_sticker)
                val indicator = holder.itemView.findViewById<android.view.View>(R.id.bg_selected_indicator)
                val btnDelete = holder.itemView.findViewById<android.view.View>(R.id.btn_delete_profile)
                
                btnDelete.visibility = android.view.View.GONE // 여기서는 삭제 기능 비활성화

                if (filename == null) {
                    com.bumptech.glide.Glide.with(holder.itemView.context).clear(img)
                    img.setImageResource(R.drawable.ic_red_heart_marker)
                    img.setPadding(20, 20, 20, 20)
                } else {
                    val file = java.io.File(java.io.File(holder.itemView.context.filesDir, "profile_stickers"), filename)
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(file)
                        .into(img)
                    img.setPadding(0, 0, 0, 0)
                }

                // 선택된 항목 표시
                indicator.visibility = if (chosenMarkerProfileFilename == filename) android.view.View.VISIBLE else android.view.View.INVISIBLE
                
                holder.itemView.setOnClickListener {
                    chosenMarkerProfileFilename = filename
                    notifyDataSetChanged()
                }
            }

            override fun getItemCount() = items.size
        }
    }

    // region 💌 [Share Logic from MemoryMapActivity]
    
    private fun shareToLoverViaKakao(target: Memory) {
        val shareDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val shareView = layoutInflater.inflate(R.layout.dialog_share_names, null)
        val etSender = shareView.findViewById<android.widget.EditText>(R.id.et_share_sender)
        val etReceiver = shareView.findViewById<android.widget.EditText>(R.id.et_share_receiver)
        val btnConfirm = shareView.findViewById<android.widget.Button>(R.id.btn_confirm_share)
        
        // 💡 [대표님 지시] 특정 이름을 기본값으로 채우지 않고 힌트(입력하세요)가 보이도록 함
        
        btnConfirm.setOnClickListener {
            val senderName = etSender.text.toString().trim()
            val receiverName = etReceiver.text.toString().trim()
            if (senderName.isNotEmpty() && receiverName.isNotEmpty()) {
                ProfileStickerManager.setMyName(this, senderName)
                ProfileStickerManager.setPartnerName(this, receiverName)
                shareDialog.dismiss()
                proceedToShare(target, senderName, receiverName)
            }
        }
        shareDialog.setContentView(shareView)
        shareDialog.show()
    }

    private fun createShareCoverGraphic(senderName: String, receiverName: String): Bitmap {
        val size = 1000
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val bgPaint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, size.toFloat(),
                intArrayOf(Color.parseColor("#1A1A1A"), Color.parseColor("#000000")),
                null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
        
        val logoPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BBFFFFFF")
            textSize = 34f
            letterSpacing = 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
            try { typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD) } catch (e: Exception) {}
        }
        canvas.drawText("H E R E   W I T H   Y O U", size / 2f, 100f, logoPaint)
        
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            try { typeface = androidx.core.content.res.ResourcesCompat.getFont(this@CardEditorActivity, R.font.kyobo_hand_family) } catch (e: Exception) {}
        }
        textPaint.textSize = 62f
        canvas.drawText("${senderName}님이", size / 2f, size / 2f - 60f, textPaint)
        textPaint.apply { textSize = 68f; color = Color.parseColor("#FFD700") }
        canvas.drawText("${receiverName}님에게", size / 2f, size / 2f + 20f, textPaint)
        textPaint.apply { textSize = 54f; color = Color.WHITE }
        canvas.drawText("소중한 추억의 장소를 공유합니다 📍", size / 2f, size / 2f + 110f, textPaint)
        
        return result
    }

    private fun proceedToShare(target: Memory, senderName: String, receiverName: String) {
        Toast.makeText(this, "우리만의 소중한 장소 공유를 준비합니다 ✨", Toast.LENGTH_SHORT).show()
        kotlin.concurrent.thread {
            try {
                val uri = Uri.parse(target.photoUri)
                val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                
                val myProfileBitmap = if (!target.profileSticker.isNullOrEmpty()) {
                    ProfileStickerManager.getProfileBitmap(this@CardEditorActivity, target.profileSticker!!)
                } else {
                    ProfileStickerManager.getSelectedProfileBitmap(this@CardEditorActivity)
                }
                
                val coverBitmap = createShareCoverGraphic(senderName, receiverName)
                
                val coverFile = java.io.File(cacheDir, "share_cover.jpg")
                coverFile.outputStream().use { coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                
                val originalFile = java.io.File(cacheDir, "share_original.jpg")
                originalFile.outputStream().use { originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                
                val profileFile = if (myProfileBitmap != null) {
                    val f = java.io.File(cacheDir, "sh_prof.png")
                    f.outputStream().use { myProfileBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    f
                } else null
                
                runOnUiThread { uploadAndShareTriple(coverFile, originalFile, profileFile, target) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun uploadAndShareTriple(coverFile: java.io.File, originalFile: java.io.File, profileFile: java.io.File?, target: Memory) {
        com.kakao.sdk.share.ShareClient.instance.uploadImage(originalFile) { oResult, oError ->
            if (oError != null || oResult == null) {
                runOnUiThread { Toast.makeText(this@CardEditorActivity, "이미지 업로드에 실패했습니다. 네트워크를 확인해주세요! 😢", Toast.LENGTH_SHORT).show() }
                return@uploadImage
            }
            val oUrl = oResult.infos.original.url
            
            if (profileFile != null) {
                com.kakao.sdk.share.ShareClient.instance.uploadImage(profileFile) { pResult, pError ->
                    if (pError != null || pResult == null) {
                        com.kakao.sdk.share.ShareClient.instance.uploadImage(coverFile) { cResult, cError ->
                            val cUrl = cResult?.infos?.original?.url ?: ""
                            sendKakaoLinkWithProfile(cUrl, oUrl, "", target)
                        }
                    } else {
                        val pUrl = pResult.infos.original.url
                        com.kakao.sdk.share.ShareClient.instance.uploadImage(coverFile) { cResult, cError ->
                            val cUrl = cResult?.infos?.original?.url ?: ""
                            sendKakaoLinkWithProfile(cUrl, oUrl, pUrl, target)
                        }
                    }
                }
            } else {
                com.kakao.sdk.share.ShareClient.instance.uploadImage(coverFile) { cResult, cError ->
                    val cUrl = cResult?.infos?.original?.url ?: ""
                    sendKakaoLinkWithProfile(cUrl, oUrl, "", target)
                }
            }
        }
    }

    private fun sendKakaoLinkWithProfile(coverUrl: String, originalUrl: String, profileUrl: String, target: Memory) {
        val executionParams = mutableMapOf(
            "lat" to target.lat.toString(),
            "lng" to target.lng.toString(),
            "addr" to (target.address ?: ""),
            "img" to originalUrl
        )
        if (profileUrl.isNotEmpty()) {
            executionParams["profile"] = profileUrl
        }
        
        val feedTemplate = com.kakao.sdk.template.model.FeedTemplate(
            content = com.kakao.sdk.template.model.Content(
                title = "소중한 추억 ✨",
                description = "지도로 우리만의 비밀 장소를 확인해보세요!",
                imageUrl = coverUrl,
                link = com.kakao.sdk.template.model.Link(androidExecutionParams = executionParams)
            ),
            buttons = listOf(com.kakao.sdk.template.model.Button("추억 확인하기", com.kakao.sdk.template.model.Link(androidExecutionParams = executionParams)))
        )
        
        com.kakao.sdk.share.ShareClient.instance.shareDefault(this, feedTemplate) { result, error ->
            if (error == null && result != null) startActivity(result.intent)
        }
    }
    // endregion

}
class FlowLayout @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.view.ViewGroup(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingRight
        var width = 0
        var height = paddingTop
        var currentLineWidth = paddingLeft
        var currentLineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val childHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (currentLineWidth + childWidth > widthSize) {
                width = Math.max(width, currentLineWidth)
                currentLineWidth = paddingLeft + childWidth
                height += currentLineHeight
                currentLineHeight = childHeight
            } else {
                currentLineWidth += childWidth
                currentLineHeight = Math.max(currentLineHeight, childHeight)
            }
        }
        height += currentLineHeight + paddingBottom
        width = Math.max(width, currentLineWidth) + paddingRight
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val widthSize = r - l - paddingRight
        var currentLeft = paddingLeft
        var currentTop = paddingTop
        var currentLineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (currentLeft + childWidth + lp.leftMargin + lp.rightMargin > widthSize) {
                currentTop += currentLineHeight
                currentLeft = paddingLeft
                currentLineHeight = 0
            }

            val left = currentLeft + lp.leftMargin
            val top = currentTop + lp.topMargin
            child.layout(left, top, left + childWidth, top + childHeight)

            currentLeft += childWidth + lp.leftMargin + lp.rightMargin
            currentLineHeight = Math.max(currentLineHeight, childHeight + lp.topMargin + lp.bottomMargin)
        }
    }

    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }
    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return MarginLayoutParams(p)
    }
    override fun checkLayoutParams(p: LayoutParams?): Boolean {
        return p is MarginLayoutParams
    }
}
