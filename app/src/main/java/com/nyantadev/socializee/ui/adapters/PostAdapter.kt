package com.nyantadev.socializee.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.nyantadev.socializee.R
import com.nyantadev.socializee.databinding.ItemPostBinding
import com.nyantadev.socializee.models.Post
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(
    private val currentUserId: String,
    private val isAdmin: Boolean = false,      // [NEW] — diisi dari SessionManager.isAdmin()
    private val onLike: (Post, Int) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onRepost: (Post) -> Unit,
    private val onUserClick: (String) -> Unit,
    private val onDelete: (Post, Int) -> Unit,
    private val onImageClick: (List<String>, Int) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
        override fun areContentsTheSame(old: Post, new: Post) = old == new
    }

    inner class PostViewHolder(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post, position: Int) = with(binding) {

            // ── Label repost "🔁 Nama merepost" ──────────────────────────────
            if (post.isRepost && !post.repostedByDisplayName.isNullOrBlank()) {
                tvRepostLabel.visibility = View.VISIBLE
                tvRepostLabel.text = "🔁 ${post.repostedByDisplayName} merepost"
            } else {
                tvRepostLabel.visibility = View.GONE
            }

            // ── Info user (penulis konten asli) ───────────────────────────────
            tvUsername.text     = "@${post.username}"
            tvDisplayName.text  = post.displayName.ifBlank { post.username }
            tvContent.text      = post.content
            tvLikeCount.text    = post.likesCount.toString()
            tvCommentCount.text = post.commentsCount.toString()
            tvRepostCount.text  = post.repostsCount.toString()

            // ── Avatar ────────────────────────────────────────────────────────
            Glide.with(root.context)
                .load(post.avatarUrl.ifBlank { null })
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(ivAvatar)

            // ── Like state ────────────────────────────────────────────────────
            ivLike.setImageResource(
                if (post.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            ivLike.setColorFilter(
                root.context.getColor(if (post.isLiked) R.color.red_like else R.color.text_secondary)
            )

            // ── Repost state ──────────────────────────────────────────────────
            ivRepost.setImageResource(
                if (post.isReposted) R.drawable.ic_repost_filled else R.drawable.ic_repost_outline
            )
            ivRepost.setColorFilter(
                root.context.getColor(if (post.isReposted) R.color.green_repost else R.color.text_secondary)
            )

            // ── Waktu ─────────────────────────────────────────────────────────
            tvTime.text = formatTime(post.createdAt)

            // ── Tombol hapus ──────────────────────────────────────────────────
            // [CHANGED] Admin bisa hapus post siapapun (kecuali baris repost)
            // User biasa hanya bisa hapus miliknya sendiri (bukan repost)
            val canDelete = !post.isRepost && (post.userId == currentUserId || isAdmin)
            ivDelete.visibility = if (canDelete) View.VISIBLE else View.GONE

            // ── Gambar ────────────────────────────────────────────────────────
            imageContainer.removeAllViews()
            if (post.images.isNotEmpty()) {
                imageContainer.visibility = View.VISIBLE
                val sortedImages = post.images.sortedBy { it.order }
                buildImageGrid(sortedImages.map { it.url }, position)
            } else {
                imageContainer.visibility = View.GONE
            }

            // ── Klik ──────────────────────────────────────────────────────────
            ivLike.setOnClickListener        { onLike(post, position) }
            ivComment.setOnClickListener     { onComment(post) }
            ivRepost.setOnClickListener      { onRepost(post) }
            ivAvatar.setOnClickListener      { onUserClick(post.userId) }
            tvDisplayName.setOnClickListener { onUserClick(post.userId) }
            tvUsername.setOnClickListener    { onUserClick(post.userId) }
            ivDelete.setOnClickListener      { onDelete(post, position) }
        }

        // ── Image grid ────────────────────────────────────────────────────────
        private fun buildImageGrid(urls: List<String>, postPosition: Int) = with(binding) {
            imageContainer.removeAllViews()
            val context  = root.context
            val cornerPx = (12 * context.resources.displayMetrics.density).toInt()
            val margin   = (4  * context.resources.displayMetrics.density).toInt()

            when (urls.size) {
                1 -> {
                    addImage(urls[0], LinearLayout.LayoutParams.MATCH_PARENT, 700, cornerPx, 0) {
                        onImageClick(urls, 0)
                    }
                }
                2 -> {
                    val row = LinearLayout(context).apply {
                        orientation  = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
                    }
                    urls.forEachIndexed { i, url ->
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            if (i == 0) rightMargin = margin
                        }
                        row.addView(buildImageView(url, lp, cornerPx) { onImageClick(urls, i) })
                    }
                    imageContainer.addView(row)
                }
                else -> {
                    addImage(urls[0], LinearLayout.LayoutParams.MATCH_PARENT, 500, cornerPx, margin) {
                        onImageClick(urls, 0)
                    }
                    val row = LinearLayout(context).apply {
                        orientation  = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
                    }
                    urls.drop(1).take(3).forEachIndexed { i, url ->
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            if (i > 0) leftMargin = margin
                        }
                        row.addView(buildImageView(url, lp, cornerPx) { onImageClick(urls, i + 1) })
                    }
                    imageContainer.addView(row)
                }
            }
        }

        private fun addImage(url: String, w: Int, h: Int, corner: Int, bottomMargin: Int, onClick: () -> Unit) {
            val lp = LinearLayout.LayoutParams(w, h).apply { this.bottomMargin = bottomMargin }
            binding.imageContainer.addView(buildImageView(url, lp, corner, onClick))
        }

        private fun buildImageView(
            url: String,
            lp: LinearLayout.LayoutParams,
            cornerPx: Int,
            onClick: () -> Unit
        ): ImageView {
            val isVideo = isVideoUrl(url)
            return ImageView(binding.root.context).apply {
                layoutParams = lp
                scaleType    = ImageView.ScaleType.CENTER_CROP
                if (isVideo) {
                    Glide.with(context)
                        .asBitmap()
                        .load(url)
                        .apply(
                            com.bumptech.glide.request.RequestOptions()
                                .frame(1_000_000L)
                                .centerCrop()
                                .transform(RoundedCorners(cornerPx))
                        )
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                            override fun onResourceReady(
                                resource: android.graphics.Bitmap,
                                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                            ) {
                                setImageBitmap(resource)
                                foreground = androidx.core.content.ContextCompat.getDrawable(
                                    context, android.R.drawable.ic_media_play
                                )
                            }
                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                setImageDrawable(placeholder)
                            }
                            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                setImageResource(R.drawable.ic_image_placeholder)
                                foreground = androidx.core.content.ContextCompat.getDrawable(
                                    context, android.R.drawable.ic_media_play
                                )
                            }
                        })
                } else {
                    Glide.with(context)
                        .load(url)
                        .transform(RoundedCorners(cornerPx))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(this)
                }
                setOnClickListener { onClick() }
            }
        }

        private fun isVideoUrl(url: String): Boolean {
            val path = url.substringBefore("?").lowercase()
            return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".avi") ||
                    path.endsWith(".mkv") || path.endsWith(".3gp") || path.contains("/videos/")
        }
    }

    private fun formatTime(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date   = sdf.parse(dateStr) ?: return dateStr
            val now    = Date()
            val diffMs   = now.time - date.time
            val diffMin  = diffMs / 60000
            val diffHour = diffMin / 60
            val diffDay  = diffHour / 24
            when {
                diffMin  < 1  -> "baru saja"
                diffMin  < 60 -> "${diffMin}m"
                diffHour < 24 -> "${diffHour}j"
                diffDay  < 7  -> "${diffDay}h"
                else -> SimpleDateFormat("d MMM", Locale("id")).format(date)
            }
        } catch (e: Exception) { "" }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}