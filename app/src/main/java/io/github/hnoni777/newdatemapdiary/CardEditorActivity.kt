package io.github.hnoni777.newdatemapdiary

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class CardEditorActivity : AppCompatActivity() {

    private lateinit var editCardMessage: EditText
    private var photoUri: Uri? = null
    private var address: String = ""
    private var lat: Double = 0.0
    private var lng: Double = 0.0

    private var currentSelectedSticker: View? = null
    private var scaleFactor = 1f
    private var initialDistance = 0f
    private var initialRotation = 0f

    private val sakuraHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sakuraRunnable: Runnable? = null

    // Save the original beautiful handwriting font instantiated from XML
    private var calligraphyFont: android.graphics.Typeface? = null

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
        showCardPreview()
    }

    private fun setupPanels() {
        val categoryGroup = findViewById<View>(R.id.category_button_group)
        val panelContainer = findViewById<View>(R.id.detail_panel_container)
        
        val panelText = findViewById<View>(R.id.panel_text)
        val panelTheme = findViewById<View>(R.id.panel_theme)
        val panelSticker = findViewById<View>(R.id.panel_sticker)

        // Show/Hide logic
        fun showPanel(panel: View) {
            categoryGroup.visibility = View.GONE
            panelContainer.visibility = View.VISIBLE
            
            panelText.visibility = View.GONE
            panelTheme.visibility = View.GONE
            panelSticker.visibility = View.GONE
            
            panel.visibility = View.VISIBLE
        }

        fun hidePanels() {
            panelContainer.visibility = View.GONE
            categoryGroup.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.btn_category_text).setOnClickListener { showPanel(panelText) }
        findViewById<View>(R.id.btn_category_theme).setOnClickListener { showPanel(panelTheme) }
        findViewById<View>(R.id.btn_category_sticker).setOnClickListener { showPanel(panelSticker) }

        findViewById<View>(R.id.btn_done_text).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_theme).setOnClickListener { hidePanels() }
        findViewById<View>(R.id.btn_done_sticker).setOnClickListener { hidePanels() }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_save_photo).setOnClickListener {
            takeScreenshot(false)
        }

        findViewById<View>(R.id.btn_share_photo).setOnClickListener {
            takeScreenshot(true)
        }

        // 🎨 Theme Selection
        findViewById<View>(R.id.theme_pale_blue).setOnClickListener { updateCardTheme("#F0F7FF") }
        findViewById<View>(R.id.theme_romantic_pink).setOnClickListener { updateCardTheme("#FFF0F5") }
        findViewById<View>(R.id.theme_warm_vintage).setOnClickListener { updateCardTheme("#FFF8E1") }
        findViewById<View>(R.id.theme_modern_minimal).setOnClickListener { updateCardTheme("#F5F5F5") }

        // 👑 VVIP Premium Effects
        findViewById<View>(R.id.btn_effect_basic).setOnClickListener { applyCardEffect("basic") }
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
                
                setOnClickListener { addOrToggleSticker("icon", resId, 0) }
            }
            val img = ImageView(this).apply {
                setImageResource(resId)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(img)
            if (isPremium) {
                val lock = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_secure)
                    layoutParams = FrameLayout.LayoutParams((16 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt()).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    }
                    setColorFilter(Color.WHITE)
                }
                frame.addView(lock)
            }
            return frame
        }

        // Helper to add emoji sticker
        fun createEmojiIcon(emoji: String, isPremium: Boolean): View {
            val frame = FrameLayout(this).apply {
                val params = android.view.ViewGroup.MarginLayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
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
                
                setOnClickListener { addEmojiSticker(emoji) }
            }
            val tv = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(tv)
            if (isPremium) {
                val lock = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_secure)
                    layoutParams = FrameLayout.LayoutParams((16 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt()).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    }
                    setColorFilter(Color.WHITE)
                }
                frame.addView(lock)
            }
            return frame
        }

        // Helper to add lettering sticker
        fun createLetteringIcon(textStr: String): View {
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
                
                setOnClickListener { addLetteringSticker(textStr) }
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
            
            val lock = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_secure)
                layoutParams = FrameLayout.LayoutParams((14 * resources.displayMetrics.density).toInt(), (14 * resources.displayMetrics.density).toInt()).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                }
                setColorFilter(Color.WHITE)
            }
            frame.addView(lock)
            
            return frame
        }

        // Add 20 Basic (1 tape + 19 emojis)
        basicContainer.addView(createDrawableIcon(R.drawable.ic_sticker_tape, 60, false))
        basicEmojis.forEach { basicContainer.addView(createEmojiIcon(it, false)) }

        // --- 1. Top priority: Heart Emojis ---
        vvipHeartEmojis.forEach { premiumContainer.addView(createEmojiIcon(it, true)) }

        // --- 2. VVIP Premium Emojis (Watercolor & Neon) ---
        vvipWatercolorEmojis.forEach { premiumContainer.addView(createEmojiIcon(it, true)) }
        vvipNeonEmojis.forEach { premiumContainer.addView(createEmojiIcon(it, true)) }

        // --- 3. Standard Premium Emojis ---
        premiumEmojis.forEach { premiumContainer.addView(createEmojiIcon(it, true)) }

        // --- 4. Lettering Stickers (Moved to Bottom) ---
        vvipLetterings.forEach { premiumContainer.addView(createLetteringIcon(it)) }
        premiumLetterings.forEach { premiumContainer.addView(createLetteringIcon(it)) }
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
        cardView.findViewById<TextView>(R.id.card_date).text =
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
            
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
        showQRCodeOnStickerLayer(cardView)
        
        val stickerLayer = cardView.findViewById<View>(R.id.sticker_container)
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                clearStickerSelection()
                return true
            }
        })
        stickerLayer?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // 🚀 KODARI DYNAMIC LAYOUT ENGINE 🚀
        // Calculate the exact empty gap caused by scaleY=0.85 and pull the UI up to sit flush with the card's visual bottom.
        val bottomPanel = findViewById<View>(R.id.editor_bottom_panel)
        container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (container.height > 0) {
                    val realHeight = container.height
                    val visualHeight = realHeight * 0.85f
                    val gap = realHeight - visualHeight

                    val params = bottomPanel.layoutParams as ViewGroup.MarginLayoutParams
                    
                    // Add an extra ~15dp offset for visual sweetness between the card margin and buttons
                    params.topMargin = -gap.toInt() + (15 * resources.displayMetrics.density).toInt()
                    
                    bottomPanel.layoutParams = params
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    private fun showQRCodeOnStickerLayer(cardView: View) {
        try {
            // ✂️ Shorten data to make QR code less dense (easier to scan!)
            val shortLat = String.format("%.6f", lat)
            val shortLng = String.format("%.6f", lng)
            val shortAddr = if (address.length > 20) address.substring(0, 20) else address
            val addrEncoded = java.net.URLEncoder.encode(shortAddr, "UTF-8")
            
            val link = "https://hnoni777.github.io/newdatemapdiary/share/map.html?lat=$shortLat&lng=$shortLng&addr=$addrEncoded"
            val qrBitmap = generateQRCode(link)
            
            val qrView = cardView.findViewById<ImageView>(R.id.card_qr_code)
            if (qrBitmap != null && qrView != null) {
                qrView.setImageBitmap(qrBitmap)
                qrView.visibility = View.VISIBLE
                Log.d("QR_CODE", "Optimized QR Added to layout: $link")
            } else if (qrView != null) {
                qrView.visibility = View.INVISIBLE
            }
        } catch (e: Exception) {
            Log.e("QR_CODE", "Failed to add QR", e)
        }
    }
    
    private fun updateCardMessageText() {
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount > 0) {
            val cardView = container.getChildAt(0)
            val userMessage = editCardMessage.text.toString()
            cardView.findViewById<TextView>(R.id.card_message).text =
                if (userMessage.isNotEmpty()) userMessage else "오늘의 로맨틱한 순간"
        }
    }

    private fun updateCardTheme(colorHex: String) {
        val color = Color.parseColor(colorHex)
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        if (container.childCount > 0) {
            val cardView = container.getChildAt(0) as? androidx.cardview.widget.CardView
            val contentLayout = cardView?.findViewById<View>(R.id.card_content_layout)
            
            // 프리미엄/VVIP 효과가 적용되어 있을 경우 배경색 변경 무시
            val currentTag = contentLayout?.tag as? String
            if (currentTag != "basic" && currentTag != "glass" && currentTag != null) {
                Toast.makeText(this, "프리미엄 테마 적용 중에는 배경 컬러 기능을 사용할 수 없습니다. 초기화 후 사용해주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            
            cardView?.setCardBackgroundColor(color)
            
            // Keep content layout background transparent or colored depending on current effect (Basic vs Glass)
            // By default let's make it the same as the card
            if (currentTag != "glass") {
                contentLayout?.setBackgroundColor(color)
                cardView?.findViewById<View>(R.id.card_text_layout)?.setBackgroundColor(color)
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
                
                stopSakuraEffect()
                Toast.makeText(this, "카드 효과가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
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
                
                Toast.makeText(this, "✨ 코부장이 드리는 VIP 고감격 다이나믹 에디션!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🍷 우아하고 클래식한 버건디 럭셔리 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🌌 모던하고 세련된 네이비 클래식 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🕊️ 고급스러운 무색 양각 크림 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🌿 신비로운 포레스트 에메랄드 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🤍 순백의 우아함 화이트 펄 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🌸 로맨틱한 로즈골드 블러시 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🖤 압도적인 포스 미드나잇 플래티넘 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🔮 매혹적인 로열 자수정 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "☁️ 포근하고 몽환적인 파스텔 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "💛 통통 튀는 키치한 뉴트로 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🎫 감성 돋는 빈티지 티켓 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🌌 사이버 펑크 네온 테마 적용!", Toast.LENGTH_SHORT).show()
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
                
                Toast.makeText(this, "✉️ 아날로그 감성 편지지 적용 완료!", Toast.LENGTH_SHORT).show()
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
                
                Toast.makeText(this, "🎀 뽀짝뽀짝 큐티 쁘띠 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "❤️ 러블리 하트 뿅뿅 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "⭐ 별이 쏟아지는 감성 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🐈 귀여운 냥이 발바닥 테마 적용!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "🍰 달콤함이 팡팡! 디저트 테마 적용!", Toast.LENGTH_SHORT).show()
            }
            "bw" -> {
                val matrix = android.graphics.ColorMatrix()
                matrix.setSaturation(0f)
                cardImage.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                Toast.makeText(this, "🎞️ 흑백 빈티지 필터 적용!", Toast.LENGTH_SHORT).show()
            }
            "sakura" -> {
                if (sakuraRunnable == null) {
                    startSakuraEffect()
                } else {
                    stopSakuraEffect()
                    Toast.makeText(this, "🌸 벚꽃 효과 종료", Toast.LENGTH_SHORT).show()
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
        
        Toast.makeText(this, "🌸 카드 전체에 예쁜 벚꽃이 박제되었습니다(공유가능)!", Toast.LENGTH_SHORT).show()
        
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

    private fun addOrToggleSticker(tag: String, resId: Int, color: Int) {
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
                    layoutParams = FrameLayout.LayoutParams(160, 160).apply {
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

    private fun addEmojiSticker(emoji: String) {
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

                val stickerView = TextView(this).apply {
                    text = emoji
                    textSize = 60f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(40, 40, 40, 40)
                    }
                    setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
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

                stickerWrapper.addView(stickerView)
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

    private fun addLetteringSticker(textStr: String) {
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

                val stickerView = TextView(this).apply {
                    text = textStr
                    textSize = 45f 
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(30, 30, 30, 30) // padding for close button
                    }
                    paint.isFakeBoldText = true
                    setShadowLayer(8f, 2f, 2f, Color.parseColor("#99000000"))
                }

                // Apply shiny gold gradient to the text after it measures
                stickerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        stickerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, stickerView.height.toFloat(),
                            intArrayOf(Color.parseColor("#FFF5C3"), Color.parseColor("#D4AF37"), Color.parseColor("#AA7A00")),
                            floatArrayOf(0f, 0.5f, 1f),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        stickerView.paint.shader = shader
                    }
                })

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

                stickerWrapper.addView(stickerView)
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

    private fun takeScreenshot(shareAfter: Boolean) {
        clearStickerSelection()
        
        val container = findViewById<FrameLayout>(R.id.card_preview_container)
        
        val innerCard = if (container.childCount > 0) {
            val cardView = container.getChildAt(0) as? ViewGroup
            cardView?.getChildAt(0) ?: container
        } else container

        val photoView = innerCard.findViewById<View>(R.id.card_image)
        var photoCardView: View? = null
        
        if (photoView != null) {
            var current: View? = photoView
            while (current != null && current != innerCard && current !is androidx.cardview.widget.CardView) {
                current = current.parent as? View
            }
            photoCardView = current ?: photoView
            // 💡 꼼수: 먼저 숨겨서 직각으로 그려지는 것 방지!
            photoCardView.visibility = android.view.View.INVISIBLE
        }
        
        val bitmap = Bitmap.createBitmap(innerCard.width, innerCard.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        innerCard.draw(canvas)
        
        try {
            if (photoView != null && photoCardView != null) {
                // 원래대로 원상복구
                photoCardView.visibility = android.view.View.VISIBLE
                
                var rx = 0f
                var ry = 0f
                var pCurrent: View? = photoCardView
                while (pCurrent != null && pCurrent != innerCard) {
                    rx += pCurrent.x
                    ry += pCurrent.y
                    pCurrent = pCurrent.parent as? View
                }
                
                val left = rx
                val top = ry
                val right = left + photoCardView.width
                val bottom = top + photoCardView.height
                
                val radiusPx = (photoCardView as? androidx.cardview.widget.CardView)?.radius ?: (12 * resources.displayMetrics.density)
                
                val path = android.graphics.Path().apply {
                    addRoundRect(
                        android.graphics.RectF(left, top, right, bottom),
                        radiusPx, radiusPx,
                        android.graphics.Path.Direction.CW
                    )
                }
                
                canvas.save()
                canvas.clipPath(path)
                // 직각 찌꺼기가 애초에 없으므로, 배경 채색(drawRect)으로 가릴 필요 없이 그냥 예쁘게 그리기만 하면 됨!
                canvas.translate(left, top)
                photoCardView.draw(canvas)
                canvas.restore()
                
                // Redraw the premium border if it's visible so it overlays the newly drawn photo properly
                val premiumBorder = innerCard.findViewById<View>(R.id.card_premium_border)
                if (premiumBorder != null && premiumBorder.visibility == android.view.View.VISIBLE) {
                    var bx = 0f
                    var by = 0f
                    var bCurrent: View? = premiumBorder
                    while (bCurrent != null && bCurrent != innerCard) {
                        bx += bCurrent.x
                        by += bCurrent.y
                        bCurrent = bCurrent.parent as? View
                    }
                    canvas.save()
                    canvas.translate(bx, by)
                    premiumBorder.draw(canvas)
                    canvas.restore()
                }

                val stickerLayer = innerCard.findViewById<View>(R.id.sticker_container)
                if (stickerLayer != null) {
                    var sdx = 0f
                    var sdy = 0f
                    var sCurrent: View? = stickerLayer
                    while (sCurrent != null && sCurrent != innerCard) {
                        sdx += sCurrent.x
                        sdy += sCurrent.y
                        sCurrent = sCurrent.parent as? View
                    }
                    canvas.save()
                    canvas.translate(sdx, sdy)
                    stickerLayer.draw(canvas)
                    canvas.restore()
                }
            }
        } catch (e: Exception) {
            photoCardView?.visibility = android.view.View.VISIBLE
            Log.e("SCREENSHOT_ROUNDING", "Precision drawing failed", e)
        }
        val savedUri = saveBitmapToGallery(bitmap, lat, lng, address)
        if (savedUri != null) {
            try {
                // DB에 추억 저장 (내 추억지도용)
                val dbHelper = MemoryDatabaseHelper(this)
                val memory = Memory(
                    photoUri = savedUri.toString(),
                    address = address.trim(),
                    lat = lat,
                    lng = lng,
                    date = System.currentTimeMillis()
                )
                dbHelper.insertMemory(memory)
                Log.d("DB_INSERT", "내 추억지도 저장 성공: ${address.trim()}")
            } catch (e: Exception) {
                Log.e("DB_INSERT", "내 추억지도 저장 실패", e)
            }
        }

        if (shareAfter && savedUri != null) {
            shareImage(savedUri, lat, lng, address)
        } else if(savedUri != null) {
            Toast.makeText(this, "갤러리 및 추억지도에 저장되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, lat: Double, lng: Double, address: String): Uri? {
        try {
            val filename = "DateMapDiary_Card_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NewDateMapDiary")
            }

            val tempFile = java.io.File(cacheDir, "temp_card_exif.jpg")
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // Exif Metadata Injector 🕵️‍♂️
            try {
                val exif = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                val encodedAddr = java.net.URLEncoder.encode(address, "UTF-8")
                val jsonMeta = "{\"lat\":$lat, \"lng\":$lng, \"addr\":\"$encodedAddr\"}"
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
            val bitMatrix = writer.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    // Standard Black on Transparent (ImageView background handles white)
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.TRANSPARENT)
                }
            }
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
