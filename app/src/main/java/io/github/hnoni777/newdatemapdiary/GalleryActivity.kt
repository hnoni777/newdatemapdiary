package io.github.hnoni777.newdatemapdiary

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import io.github.hnoni777.newdatemapdiary.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var galleryAdapter: GalleryAdapter

    private val imagesByDate = mutableMapOf<String, MutableList<Uri>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGallery()
        setupCalendar()

        binding.btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the gallery list when returning (e.g., after deleting an image)
        loadSavedImages()
    }

    private fun setupGallery() {
        galleryAdapter = GalleryAdapter { selectedUri, imageView ->
            val intent = Intent(this, GalleryDetailActivity::class.java).apply {
                putExtra("image_uri", selectedUri.toString())
            }
            startActivity(intent)
        }
        binding.rvGallery.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 2)
            adapter = galleryAdapter
        }
    }
    
    private lateinit var customCalendarAdapter: CalendarAdapter
    private var currentYear = 0
    private var currentMonth = 0
    private var selectedDateKey = ""

    private fun setupCalendar() {
        val today = java.util.Calendar.getInstance()
        currentYear = today.get(java.util.Calendar.YEAR)
        currentMonth = today.get(java.util.Calendar.MONTH) + 1
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
        selectedDateKey = String.format("%04d-%02d-%02d", currentYear, currentMonth, todayDay)

        customCalendarAdapter = CalendarAdapter { year, month, day ->
            selectedDateKey = String.format("%04d-%02d-%02d", year, month, day)
            updateGalleryForDate(year, month, day)
            updateCalendarUI()
        }

        binding.rvCalendar.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 7)
            adapter = customCalendarAdapter
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            updateCalendarUI()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            updateCalendarUI()
        }
    }

    private fun updateCalendarUI() {
        binding.tvMonthYear.text = "${currentYear}년 ${currentMonth}월"
        
        val dates = mutableListOf<CalendarDate>()
        val cal = java.util.Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, 1) // First day of the month
        
        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val startDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday, 1 = Monday...
        
        // Empty slots
        for (i in 0 until startDayOfWeek) {
            dates.add(CalendarDate(0, 0, 0))
        }
        
        // Days
        for (i in 1..maxDays) {
            dates.add(CalendarDate(currentYear, currentMonth, i))
        }

        customCalendarAdapter.updateData(dates, imagesByDate, selectedDateKey)
    }

    private fun updateGalleryForDate(year: Int, month: Int, day: Int) {
        val dateKey = String.format("%04d-%02d-%02d", year, month, day)
        val urisForDate = imagesByDate[dateKey] ?: emptyList()
        
        if (urisForDate.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvGallery.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvGallery.visibility = View.VISIBLE
            galleryAdapter.submitList(urisForDate)
        }
    }

    private fun loadSavedImages() {
        imagesByDate.clear()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%NewDateMapDiary%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAddedSecs = cursor.getLong(dateColumn)
                    
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    // Convert timestamp to yyyy-MM-dd
                    val calendar = java.util.Calendar.getInstance()
                    calendar.timeInMillis = dateAddedSecs * 1000L
                    val year = calendar.get(java.util.Calendar.YEAR)
                    val month = calendar.get(java.util.Calendar.MONTH) + 1
                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    
                    val dateKey = String.format("%04d-%02d-%02d", year, month, day)
                    
                    if (!imagesByDate.containsKey(dateKey)) {
                        imagesByDate[dateKey] = mutableListOf()
                    }
                    imagesByDate[dateKey]?.add(contentUri)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Gallery", "MediaStore query failed", e)
        }

        // Initialize with today's date
        val today = java.util.Calendar.getInstance()
        updateCalendarUI()
        updateGalleryForDate(
            today.get(java.util.Calendar.YEAR),
            today.get(java.util.Calendar.MONTH) + 1,
            today.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
