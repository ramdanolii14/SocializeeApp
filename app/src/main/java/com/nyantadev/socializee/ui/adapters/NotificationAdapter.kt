package com.nyantadev.socializee.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyantadev.socializee.R
import com.nyantadev.socializee.databinding.ItemNotificationBinding
import com.nyantadev.socializee.models.Notification
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(old: Notification, new: Notification) = old.id == new.id
        override fun areContentsTheSame(old: Notification, new: Notification) = old == new
    }

    inner class ViewHolder(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notif: Notification) = with(binding) {

            // Avatar
            Glide.with(root.context)
                .load(notif.actorAvatarUrl?.ifBlank { null })
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(ivAvatar)

            // Nama aktor
            tvActorName.text = notif.actorDisplayName.ifBlank { notif.actorUsername }

            // Pesan per tipe — inline string agar tidak bergantung strings.xml tertentu
            tvMessage.text = when (notif.type) {
                "follow"   -> "mulai mengikutimu"
                "like"     -> "menyukai postinganmu"
                "comment"  -> "mengomentari postinganmu"
                "mention"  -> "menyebutmu dalam komentar"
                "repost"   -> "merepost postinganmu"
                "new_post" -> "memposting sesuatu yang baru"
                else       -> notif.type
            }

            // Preview konten post
            if (!notif.postContent.isNullOrBlank()) {
                tvPostPreview.visibility = View.VISIBLE
                tvPostPreview.text       = notif.postContent.take(80)
            } else {
                tvPostPreview.visibility = View.GONE
            }

            // Waktu
            tvTime.text = formatTime(notif.createdAt)

            // Opacity lebih rendah jika sudah dibaca
            root.alpha = if (notif.isRead) 0.60f else 1f

            root.setOnClickListener { onClick(notif) }
        }

        private fun formatTime(createdAt: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(createdAt) ?: return ""
                val diff = (Date().time - date.time) / 1000
                when {
                    diff < 60        -> "Just now"
                    diff < 3600      -> "${diff / 60}m ago"
                    diff < 86400     -> "${diff / 3600}h ago"
                    diff < 86400 * 7 -> "${diff / 86400}d ago"
                    else             -> SimpleDateFormat("d MMM", Locale("id")).format(date)
                }
            } catch (e: Exception) { "" }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}