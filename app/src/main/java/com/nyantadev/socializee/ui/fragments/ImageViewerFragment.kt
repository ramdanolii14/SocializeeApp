package com.nyantadev.socializee.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.nyantadev.socializee.databinding.FragmentImageViewerBinding

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    // Keep track of active ExoPlayer instances so we can release them
    private val players = mutableListOf<ExoPlayer>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val urls = arguments?.getStringArray("urls")?.toList() ?: run {
            findNavController().navigateUp()
            return
        }
        val startIndex = arguments?.getInt("startIndex", 0) ?: 0

        binding.ivClose.setOnClickListener { findNavController().navigateUp() }

        binding.viewPager.adapter = MediaPagerAdapter(urls)
        binding.viewPager.setCurrentItem(startIndex, false)

        if (urls.size > 1) {
            binding.tvIndicator.visibility = View.VISIBLE
            binding.tvIndicator.text = "${startIndex + 1} / ${urls.size}"
            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.tvIndicator.text = "${position + 1} / ${urls.size}"
                }
            })
        } else {
            binding.tvIndicator.visibility = View.GONE
        }
    }

    inner class MediaPagerAdapter(private val urls: List<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val url = urls[position].substringBefore("?").lowercase()
            val isVideo = url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".avi") ||
                    url.endsWith(".mkv") || url.endsWith(".3gp") || url.contains("/videos/")
            return if (isVideo) TYPE_VIDEO else TYPE_IMAGE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_VIDEO) {
                val playerView = PlayerView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                VideoViewHolder(playerView)
            } else {
                val iv = ImageView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
                ImageViewHolder(iv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val url = urls[position]
            when (holder) {
                is ImageViewHolder -> {
                    Glide.with(holder.iv.context)
                        .load(url)
                        .into(holder.iv)
                }
                is VideoViewHolder -> {
                    val player = ExoPlayer.Builder(holder.playerView.context).build().also {
                        players.add(it)
                    }
                    holder.playerView.player = player
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    player.prepare()
                    // Don't auto-play; user taps play
                }
            }
        }

        override fun getItemCount() = urls.size

        inner class ImageViewHolder(val iv: ImageView) : RecyclerView.ViewHolder(iv)
        inner class VideoViewHolder(val playerView: PlayerView) : RecyclerView.ViewHolder(playerView)
    }

    override fun onPause() {
        super.onPause()
        players.forEach { it.pause() }
    }

    override fun onDestroyView() {
        players.forEach { it.release() }
        players.clear()
        super.onDestroyView()
        _binding = null
    }

    // Companion object diletakkan dengan benar di dalam class
    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }
}