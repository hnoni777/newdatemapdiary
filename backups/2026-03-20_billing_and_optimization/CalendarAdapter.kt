package io.github.hnoni777.newdatemapdiary

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class CalendarAdapter(
    private val onDateClick: (Int, Int, Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private val dates = mutableListOf<CalendarDate>()
    private var imagesByDate = mapOf<String, Any>()
    private var selectedDateKey = ""

    fun updateData(newDates: List<CalendarDate>, savedImagesMap: Map<String, Any>, selected: String) {
        dates.clear()
        dates.addAll(newDates)
        imagesByDate = savedImagesMap
        selectedDateKey = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = dates[position]
        holder.bind(date)
    }

    override fun getItemCount(): Int = dates.size

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay: TextView = itemView.findViewById(R.id.tv_day)
        private val viewIndicator: View = itemView.findViewById(R.id.view_indicator)

        fun bind(date: CalendarDate) {
            if (date.day == 0) {
                tvDay.text = ""
                viewIndicator.visibility = View.INVISIBLE
                itemView.setOnClickListener(null)
                tvDay.setBackgroundResource(0)
                return
            }

            tvDay.text = date.day.toString()
            val dateKey = String.format("%04d-%02d-%02d", date.year, date.month, date.day)
            
            // Check if there are images for this date
            if (imagesByDate.containsKey(dateKey)) {
                viewIndicator.visibility = View.VISIBLE
            } else {
                viewIndicator.visibility = View.INVISIBLE
            }

            // Selection styling
            if (dateKey == selectedDateKey) {
                tvDay.setBackgroundResource(R.drawable.bg_calendar_selected)
                tvDay.setTextColor(Color.WHITE)
            } else {
                tvDay.setBackgroundResource(0)
                // Sunday/Saturday colors
                when (adapterPosition % 7) {
                    0 -> tvDay.setTextColor(Color.parseColor("#E53935")) // Sunday
                    6 -> tvDay.setTextColor(Color.parseColor("#1E88E5")) // Saturday
                    else -> tvDay.setTextColor(Color.parseColor("#221018")) // Weekday
                }
            }

            itemView.setOnClickListener {
                onDateClick(date.year, date.month, date.day)
            }
        }
    }
}

data class CalendarDate(val year: Int, val month: Int, val day: Int)
