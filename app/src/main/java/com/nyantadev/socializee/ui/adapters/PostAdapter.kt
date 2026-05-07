package com.nyantadev.socializee.ui.adapters

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    private val isAdmin: Boolean = false,
    private val onLike      : (Post, Int) -> Unit,
    private val onComment   : (Post) -> Unit,
    private val onRepost    : (Post) -> Unit,
    private val onUserClick : (String) -> Unit,
    private val onDelete    : (Post, Int) -> Unit,
    private val onImageClick: (List<String>, Int) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
        override fun areContentsTheSame(old: Post, new: Post) = old == new
    }

    // ────────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ────────────────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    inner class PostViewHolder(val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // ExoPlayer untuk video pertama di post ini (null jika tidak ada video)
        private var player: ExoPlayer? = null
        private var videoUrl: String? = null

        fun bind(post: Post, position: Int) = with(binding) {
            // ── Repost label ──────────────────────────────────────────────
            if (post.isRepost && !post.repostedByDisplayName.isNullOrBlank()) {
                tvRepostLabel.visibility = View.VISIBLE
                tvRepostLabel.text =
                    root.context.getString(R.string.repost_label, post.repostedByDisplayName)
            } else {
                tvRepostLabel.visibility = View.GONE
            }

            // ── User info ─────────────────────────────────────────────────
            tvUsername.text    = "@${post.username}"
            tvDisplayName.text = post.displayName.ifBlank { post.username }
            tvContent.text     = post.content
            tvLikeCount.text   = post.likesCount.toString()
            tvCommentCount.text = post.commentsCount.toString()
            tvRepostCount.text = post.repostsCount.toString()

            Glide.with(root.context)
                .load(post.avatarUrl.ifBlank { null })
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(ivAvatar)

            // ── Like / Repost state ───────────────────────────────────────
            ivLike.setImageResource(
                if (post.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            ivLike.setColorFilter(
                root.context.getColor(if (post.isLiked) R.color.red_like else R.color.text_secondary)
            )
            ivRepost.setImageResource(
                if (post.isReposted) R.drawable.ic_repost_filled else R.drawable.ic_repost_outline
            )
            ivRepost.setColorFilter(
                root.context.getColor(if (post.isReposted) R.color.green_repost else R.color.text_secondary)
            )

            tvTime.text = formatTime(post.createdAt)

            // ── Delete button ─────────────────────────────────────────────
            val canDelete = !post.isRepost && (post.userId == currentUserId || isAdmin)
            ivDelete.visibility = if (canDelete) View.VISIBLE else View.GONE

            // ── Media ─────────────────────────────────────────────────────
            releasePlayer()  // release player from previous bind
            imageContainer.removeAllViews()

            val sortedImages = post.images.sortedBy { it.order }
            if (sortedImages.isNotEmpty()) {
                imageContainer.visibility = View.VISIBLE
                val urls = sortedImages.map { it.url }
                buildMediaGrid(urls, position)

                // Track first video URL for autoplay
                videoUrl = urls.firstOrNull { isVideoUrl(it) }
            } else {
                imageContainer.visibility = View.GONE
                videoUrl = null
            }

            // ── Clicks ────────────────────────────────────────────────────
            ivLike.setOnClickListener        { onLike(post, position) }
            ivComment.setOnClickListener     { onComment(post) }
            ivRepost.setOnClickListener      { onRepost(post) }
            ivAvatar.setOnClickListener      { onUserClick(post.userId) }
            tvDisplayName.setOnClickListener { onUserClick(post.userId) }
            tvUsername.setOnClickListener    { onUserClick(post.userId) }
            ivDelete.setOnClickListener      { onDelete(post, position) }
        }

        // ────────────────────────────────────────────────────────────────
        // Media grid builder — FIX req #4: each row is an independent
        // LinearLayout with fixed height so images don't overlap each other.
        // ────────────────────────────────────────────────────────────────

        private fun buildMediaGrid(urls: List<String>, postPosition: Int) = with(binding) {
            // imageContainer is a FrameLayout in the layout — we add a vertical
            // LinearLayout wrapper so rows stack correctly without touching .orientation.
            imageContainer.removeAllViews()
            val wrapper = LinearLayout(imageContainer.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            imageContainer.addView(wrapper)
            // Redirect all addView calls below to wrapper via local alias
            @Suppress("LocalVariableName")
            val imageContainer = wrapper

            val ctx     = root.context
            val density = ctx.resources.displayMetrics.density
            val corner  = (10 * density).toInt()
            val gap     = (4  * density).toInt()

            when (urls.size) {
                1 -> {
                    // Single image / video — taller for visual impact
                    val h = if (isVideoUrl(urls[0])) (220 * density).toInt()
                    else (340 * density).toInt()
                    imageContainer.addView(
                        buildCell(urls[0], postPosition, 0, urls,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, h
                            ), corner
                        )
                    )
                }

                2 -> {
                    // Side-by-side
                    val row = makeRow((180 * density).toInt(), gap)
                    urls.forEachIndexed { i, url ->
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        if (i == 0) lp.rightMargin = gap
                        row.addView(buildCell(url, postPosition, i, urls, lp, corner))
                    }
                    imageContainer.addView(row)
                }

                3 -> {
                    // Top: single image full-width
                    imageContainer.addView(
                        buildCell(urls[0], postPosition, 0, urls,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (220 * density).toInt()
                            ).apply { bottomMargin = gap }, corner
                        )
                    )
                    // Bottom row: 2 images side-by-side
                    val row = makeRow((160 * density).toInt(), gap)
                    for (i in 1..2) {
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        if (i == 1) lp.rightMargin = gap
                        row.addView(buildCell(urls[i], postPosition, i, urls, lp, corner))
                    }
                    imageContainer.addView(row)
                }

                else -> {
                    // 4+ images: top single + bottom 3
                    imageContainer.addView(
                        buildCell(urls[0], postPosition, 0, urls,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (200 * density).toInt()
                            ).apply { bottomMargin = gap }, corner
                        )
                    )
                    val row = makeRow((150 * density).toInt(), gap)
                    for (i in 1..minOf(3, urls.size - 1)) {
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        if (i > 1) lp.leftMargin = gap
                        row.addView(buildCell(urls[i], postPosition, i, urls, lp, corner))
                    }
                    imageContainer.addView(row)
                }
            }
        }

        /** Fixed-height horizontal LinearLayout used as a grid row */
        private fun makeRow(height: Int, gap: Int): LinearLayout =
            LinearLayout(binding.root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, height
                )
            }

        /**
         * Build a single cell (image or video thumbnail).
         * Using FrameLayout so overlay icons don't displace the image.
         */
        @OptIn(UnstableApi::class)
        private fun buildCell(
            url       : String,
            postPos   : Int,
            index     : Int,
            allUrls   : List<String>,
            lp        : LinearLayout.LayoutParams,
            cornerPx  : Int
        ): FrameLayout {
            val ctx = binding.root.context

            return FrameLayout(ctx).apply {
                layoutParams = lp

                if (isVideoUrl(url)) {
                    // ── Video cell ─────────────────────────────────────
                    val pv = PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        useController    = false   // hide default controls
                        resizeMode       = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        clipToOutline    = true
                    }
                    addView(pv)

                    // Small centered play icon (req #5 — tidak terlalu besar)
                    val playIcon = ImageView(ctx).apply {
                        val sizePx = (36 * ctx.resources.displayMetrics.density).toInt()
                        layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                            gravity = android.view.Gravity.CENTER
                        }
                        setImageResource(R.drawable.ic_play_circle)
                        alpha = 0.85f
                    }
                    addView(playIcon)

                    // Build ExoPlayer and attach to this holder
                    val exo = ExoPlayer.Builder(ctx).build().also { ex ->
                        ex.setMediaItem(MediaItem.fromUri(url))
                        ex.repeatMode  = Player.REPEAT_MODE_ONE
                        ex.volume      = 0f   // muted
                        ex.prepare()
                        // do NOT autoPlay here; FeedFragment handles it via scroll
                    }
                    pv.player = exo
                    player    = exo
                    videoUrl  = url

                    // Show/hide play overlay based on player state
                    exo.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playIcon.visibility = if (isPlaying) View.GONE else View.VISIBLE
                        }
                    })

                    setOnClickListener { onImageClick(allUrls, index) }

                } else {
                    // ── Image cell ─────────────────────────────────────
                    val iv = ImageView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    Glide.with(ctx)
                        .load(url)
                        .transform(RoundedCorners(cornerPx))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(iv)
                    addView(iv)
                    setOnClickListener { onImageClick(allUrls, index) }
                }
            }
        }

        // ── Video control (called by FeedFragment scroll listener) ────────

        fun playVideo() {
            player?.let {
                if (!it.isPlaying) it.play()
            }
        }

        fun pauseVideo() {
            player?.let {
                if (it.isPlaying) it.pause()
            }
        }

        /** Returns approximate on-screen pixel area for this item */
        fun getVisibleArea(): Int {
            if (player == null) return 0  // not a video post
            val rect = Rect()
            return if (itemView.getGlobalVisibleRect(rect)) rect.width() * rect.height() else 0
        }

        fun releasePlayer() {
            player?.release()
            player = null
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Adapter
    // ────────────────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) =
        holder.bind(getItem(position), position)

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private fun isVideoUrl(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".avi") ||
                path.endsWith(".mkv") || path.endsWith(".3gp") || path.contains("/videos/")
    }

    private fun formatTime(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date     = sdf.parse(dateStr) ?: return dateStr
            val now      = Date()
            val diffMs   = now.time - date.time
            val diffMin  = diffMs / 60000
            val diffHour = diffMin / 60
            val diffDay  = diffHour / 24
            when {
                diffMin  < 1  -> "Just uploaded"
                diffMin  < 60 -> "${diffMin}m"
                diffHour < 24 -> "${diffHour}h"
                diffDay  < 7  -> "${diffDay}d"
                else          -> SimpleDateFormat("d MMM", Locale("id")).format(date)
            }
        } catch (e: Exception) { "" }
    }
}