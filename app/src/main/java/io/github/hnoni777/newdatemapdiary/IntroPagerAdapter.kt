package io.github.hnoni777.newdatemapdiary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class IntroPageItem(
    val iconRes: Int,
    val title: String,
    val description: String
)

class IntroPagerAdapter(private val pages: List<IntroPageItem>) :
    RecyclerView.Adapter<IntroPagerAdapter.IntroViewHolder>() {

    inner class IntroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
        val tvTagline: TextView = view.findViewById(R.id.tv_tagline)
        val tvSubTagline: TextView = view.findViewById(R.id.tv_sub_tagline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_intro_page, parent, false)
        return IntroViewHolder(view)
    }

    override fun onBindViewHolder(holder: IntroViewHolder, position: Int) {
        val item = pages[position]
        holder.ivIcon.setImageResource(item.iconRes)
        
        holder.tvTagline.text = item.title
        holder.tvSubTagline.text = item.description
    }

    override fun getItemCount(): Int = pages.size
}
