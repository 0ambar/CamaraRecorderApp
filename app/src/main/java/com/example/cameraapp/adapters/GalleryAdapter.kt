package com.example.cameraapp.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cameraapp.R
import com.example.cameraapp.activities.AudioPlayerActivity
import com.example.cameraapp.activities.ImageViewerActivity
import com.example.cameraapp.models.MediaItem
import java.io.File

class GalleryAdapter(private val mediaList: List<MediaItem>) :
    RecyclerView.Adapter<GalleryAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = mediaList[position]

        when (mediaItem.type) {
            MediaItem.TYPE_IMAGE -> {
                Glide.with(holder.itemView.context)
                    .load(File(mediaItem.path))
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.imageView)
                holder.audioIcon.visibility = View.GONE
            }
            MediaItem.TYPE_AUDIO -> {
                holder.imageView.setImageResource(R.drawable.ic_audio_placeholder)
                holder.imageView.visibility = View.VISIBLE
                holder.audioIcon.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            when (mediaItem.type) {
                MediaItem.TYPE_IMAGE -> {
                    val intent = Intent(context, ImageViewerActivity::class.java)
                    intent.putExtra("image_path", mediaItem.path)
                    context.startActivity(intent)
                }
                MediaItem.TYPE_AUDIO -> {
                    val intent = Intent(context, AudioPlayerActivity::class.java)
                    intent.putExtra("audio_path", mediaItem.path)
                    context.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount() = mediaList.size

    class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.mediaImage)
        val audioIcon: ImageView = itemView.findViewById(R.id.audioIcon)
    }
}