package io.github.hnoni777.newdatemapdiary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.hnoni777.newdatemapdiary.databinding.ItemGalleryCardBinding


class GalleryAdapter(private val onItemClick: (android.net.Uri, android.widget.ImageView) -> Unit) :
    ListAdapter<android.net.Uri, GalleryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGalleryCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemGalleryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: android.net.Uri) {
            Glide.with(binding.ivGalleryItem.context)
                .load(uri)
                .into(binding.ivGalleryItem)

            binding.root.setOnClickListener {
                onItemClick(uri, binding.ivGalleryItem)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<android.net.Uri>() {
        override fun areItemsTheSame(oldItem: android.net.Uri, newItem: android.net.Uri): Boolean =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: android.net.Uri, newItem: android.net.Uri): Boolean =
            oldItem == newItem
    }
}
